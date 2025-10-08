package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core service for orchestrating the MongoDB to Supabase migration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {

    private final MongoDataReaderService mongoDataReader;
    private final IdMappingService idMappingService;
    private final MenteeProfileRepository menteeProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final ProfileRepository profileRepository;
    private final SkillRepository skillRepository;
    private final MentorSkillRepository mentorSkillRepository;
    private final SessionRepository sessionRepository;
    private final SupabaseAuthService supabaseAuthService;
    private final SupabaseDatabaseDebugService debugService;
    
    @PersistenceContext
    private EntityManager entityManager;

    private volatile MigrationStatus currentStatus = MigrationStatus.PENDING;
    private volatile String currentStep = "";
    private volatile int totalRecords = 0;
    private volatile int processedRecords = 0;
    private volatile List<String> migrationLog = new ArrayList<>();
    
    // Configuration flags for migration behavior
    private static final boolean SKIP_SUPABASE_AUTH_CREATION = true; // Set to false to re-enable

    /**
     * Execute the complete migration process
     * Note: Removed @Transactional to prevent long-running transaction issues
     */
    public MigrationResult executeMigration() {
        log.info("Starting MongoDB to Supabase migration");
        log.info("Migration Configuration: SKIP_SUPABASE_AUTH_CREATION = {}", SKIP_SUPABASE_AUTH_CREATION);
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            currentStatus = MigrationStatus.IN_PROGRESS;
            migrationLog.clear();


            // Step 1: Validate collection files
            updateStatus("Validating collection files");
            if (!mongoDataReader.validateCollectionFiles()) {
                throw new MigrationException("Required collection files are missing");
            }
            addLog("✓ Collection files validated");

            // Step 2: Load and validate data
            updateStatus("Loading MongoDB data");
            List<JsonNode> users = mongoDataReader.readUsers();
            List<JsonNode> topics = mongoDataReader.readTopics();
            List<JsonNode> sessions = mongoDataReader.readSessions();
            
            totalRecords = users.size() + topics.size() + sessions.size();
            addLog(String.format("✓ Loaded %d users, %d topics, %d sessions", 
                    users.size(), topics.size(), sessions.size()));

            // Step 3: Migrate topics to skills first (no dependencies)
            updateStatus("Migrating topics to skills");
            Map<String, UUID> topicMappings = migrateTopicsToSkills(topics);
            addLog(String.format("✓ Migrated %d topics to skills", topicMappings.size()));

            // Step 4: Migrate users to profiles
            updateStatus("Migrating users to profiles");
            MigrationUserResult userResult = migrateUsers(users);
            addLog(String.format("✓ Migrated %d mentees and %d mentors", 
                    userResult.menteeCount(), userResult.mentorCount()));

            // Step 5: Create mentor-skill relationships
            updateStatus("Creating mentor-skill relationships");
            int relationshipCount = createMentorSkillRelationships(users, topicMappings, userResult.mentorMappings());
            addLog(String.format("✓ Created %d mentor-skill relationships", relationshipCount));

            // Step 6: Migrate sessions (depends on user profiles)
            updateStatus("Migrating sessions");
            Map<String, UUID> allUserMappings = userResult.allUserMappings();
            log.info("Starting session migration with {} user mappings available", allUserMappings.size());
            if (allUserMappings.isEmpty()) {
                log.warn("No user mappings available for session migration - sessions will be skipped");
            } else {
                log.debug("User mappings sample: {}", allUserMappings.entrySet().stream().limit(5).toList());
            }
            int sessionCount = migrateSessions(sessions, allUserMappings);
            addLog(String.format("✓ Migrated %d sessions", sessionCount));

            // Step 7: Validate migration results
            updateStatus("Validating migration results");
            MigrationValidationResult validation = validateMigrationResults();
            addLog("✓ Migration validation completed");

            currentStatus = MigrationStatus.COMPLETED;
            LocalDateTime endTime = LocalDateTime.now();
            
            log.info("Migration completed successfully in {} seconds", 
                    java.time.Duration.between(startTime, endTime).getSeconds());

            return new MigrationResult(
                    MigrationStatus.COMPLETED,
                    startTime,
                    endTime,
                    totalRecords,
                    processedRecords,
                    new ArrayList<>(migrationLog),
                    validation,
                    null
            );

        } catch (Exception e) {
            currentStatus = MigrationStatus.FAILED;
            String errorMessage = "Migration failed: " + e.getMessage();
            log.error(errorMessage, e);
            addLog("✗ " + errorMessage);

            return new MigrationResult(
                    MigrationStatus.FAILED,
                    startTime,
                    LocalDateTime.now(),
                    totalRecords,
                    processedRecords,
                    new ArrayList<>(migrationLog),
                    null,
                    errorMessage
            );
        }
    }

    /**
     * Migrate topics to skills table with retry logic
     */
    private Map<String, UUID> migrateTopicsToSkills(List<JsonNode> topics) {
        Map<String, UUID> mappings = new HashMap<>();
        AtomicInteger processed = new AtomicInteger(0);

        for (JsonNode topic : topics) {
            UUID skillId = migrateIndividualTopic(topic);
            if (skillId != null) {
                String mongoId = mongoDataReader.extractMongoId(topic);
                mappings.put(mongoId, skillId);
                processedRecords++;
                processed.incrementAndGet();
            }
        }

        log.info("Successfully migrated {} topics to skills", processed.get());
        return mappings;
    }

    /**
     * Migrate individual topic with retry logic (no transaction)
     */
    private UUID migrateIndividualTopic(JsonNode topic) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                String mongoId = mongoDataReader.extractMongoId(topic);
                String name = topic.get("name").asText();

                // Check if skill already exists
                Optional<Skill> existingSkill = skillRepository.findByName(name);
                UUID skillId;
                
                if (existingSkill.isPresent()) {
                    skillId = existingSkill.get().getId();
                    log.debug("Using existing skill: {} -> {}", name, skillId);
                } else {
                    // Create new skill
                    Skill skill = new Skill(name);
                    skill = skillRepository.save(skill);
                    skillId = skill.getId();
                    log.debug("Created new skill: {} -> {}", name, skillId);
                }

                // Skip ID mapping to avoid connection leaks
                log.debug("Skipping ID mapping for topic {} -> {} to prevent connection leaks", mongoId, skillId);
                return skillId;

            } catch (Exception e) {
                attempt++;
                log.warn("Failed to migrate topic {} (attempt {}/{}): {}", 
                        mongoDataReader.extractMongoId(topic), attempt, maxRetries, e.getMessage());
                
                if (attempt >= maxRetries) {
                    log.error("Failed to migrate topic {} after {} attempts", 
                            mongoDataReader.extractMongoId(topic), maxRetries);
                    return null;
                }
                
                // Wait before retry
                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Migrate users to mentee and mentor profiles in smaller batches
     */
    private MigrationUserResult migrateUsers(List<JsonNode> users) {
        Map<String, UUID> menteeMap = new HashMap<>();
        Map<String, UUID> mentorMap = new HashMap<>();
        AtomicInteger menteeCount = new AtomicInteger(0);
        AtomicInteger mentorCount = new AtomicInteger(0);

        int batchSize = 20; // Process users in smaller batches
        List<List<JsonNode>> batches = partitionList(users, batchSize);
        
        for (int i = 0; i < batches.size(); i++) {
            List<JsonNode> batch = batches.get(i);
            log.info("Processing user batch {} of {} ({} users)", i + 1, batches.size(), batch.size());
            
            MigrationUserResult batchResult = migrateUserBatch(batch);
            
            // Merge results
            menteeMap.putAll(batchResult.menteeMappings());
            mentorMap.putAll(batchResult.mentorMappings());
            menteeCount.addAndGet(batchResult.menteeCount());
            mentorCount.addAndGet(batchResult.mentorCount());
        }

        Map<String, UUID> allMappings = new HashMap<>();
        allMappings.putAll(menteeMap);
        allMappings.putAll(mentorMap);

        log.info("Successfully migrated {} mentees and {} mentors", menteeCount.get(), mentorCount.get());
        return new MigrationUserResult(menteeMap, mentorMap, allMappings, menteeCount.get(), mentorCount.get());
    }

    /**
     * Migrate a batch of users (no transaction)
     */
    private MigrationUserResult migrateUserBatch(List<JsonNode> userBatch) {
        Map<String, UUID> menteeMap = new HashMap<>();
        Map<String, UUID> mentorMap = new HashMap<>();
        AtomicInteger menteeCount = new AtomicInteger(0);
        AtomicInteger mentorCount = new AtomicInteger(0);

        for (JsonNode user : userBatch) {
            try {
                String mongoId = mongoDataReader.extractMongoId(user);
                
                // Try userType first, fall back to role field
                String userType = "";
                if (user.has("userType") && !user.get("userType").isNull()) {
                    userType = user.get("userType").asText();
                } else if (user.has("role") && !user.get("role").isNull()) {
                    userType = user.get("role").asText();
                }

                if ("ADVISEE".equals(userType)) {
                    UUID profileId = migrateMenteeProfile(user);
                    if (profileId != null) {
                        menteeMap.put(mongoId, profileId);
                        menteeCount.incrementAndGet();
                    }
                } else if ("ADVISOR".equals(userType)) {
                    UUID profileId = migrateMentorProfile(user);
                    if (profileId != null) {
                        mentorMap.put(mongoId, profileId);
                        mentorCount.incrementAndGet();
                    }
                } else {
                    log.debug("Skipping user with unknown type: {} ({})", mongoId, userType);
                }

                processedRecords++;

            } catch (Exception e) {
                log.error("Failed to migrate user {}: {}", mongoDataReader.extractMongoId(user), e.getMessage());
            }
        }

        Map<String, UUID> allMappings = new HashMap<>();
        allMappings.putAll(menteeMap);
        allMappings.putAll(mentorMap);

        return new MigrationUserResult(menteeMap, mentorMap, allMappings, menteeCount.get(), mentorCount.get());
    }

    /**
     * Migrate individual mentee profile
     */
    private UUID migrateMenteeProfile(JsonNode user) {
        String mongoId = mongoDataReader.extractMongoId(user);
        
        // Check if user has email - skip if not
        String email = safeExtractText(user, "username");
        if (email == null || email.trim().isEmpty()) {
            log.info("Skipping user {} - no email address", mongoId);
            return null;
        }
        
        // Create new profile object first to extract name parts
        Profile profile = new Profile();
        
        // Extract and split full name into first and last name
        String fullName = safeExtractText(user, "name");
        String[] nameParts = splitFullName(fullName);
        profile.setFirstName(nameParts[0]);
        profile.setLastName(nameParts[1]);
        
        // Create user in Supabase Auth first to get proper UUID
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("role", "mentee");
        userMetadata.put("mongoId", mongoId);
        userMetadata.put("first_name", profile.getFirstName() != null ? profile.getFirstName() : "");
        userMetadata.put("last_name", profile.getLastName() != null ? profile.getLastName() : "");
        
        UUID profileId = UUID.randomUUID();

        log.info("Creating mentee profile with UUID {} for user {}", profileId, mongoId);

        // Set profile details
        profile.setId(profileId);
        profile.setEmail(email);
        profile.setRole("mentee"); // ADVISEE maps to mentee
        profile.setPhone(safeExtractText(user, "phoneNumber"));
        profile.setCountry(safeExtractText(user, "country"));

        // Extract industry from nested object
        String industry = null;
        if (user.has("industry") && user.get("industry").has("name") && !user.get("industry").get("name").isNull()) {
            industry = user.get("industry").get("name").asText();
            profile.setIndustry(industry);
        }

        if(user.has("linkedInUrl")) {
            profile.setLinkedinUrl(safeExtractText(user, "linkedInUrl"));
        }

        ZonedDateTime now = java.time.ZonedDateTime.now();
        
        // Create the profile entry
        profileRepository.insertProfileSimple(
            profileId,
            profile.getEmail(),
            profile.getFirstName(),
            profile.getLastName(),
            profile.getAvatarUrl(),
            profile.getBio(),
            profile.getPhone(),
            profile.getLocation(),
            profile.getGender(),
            profile.getIndustry(),
            "mentee", // Ensure role is set correctly for ADVISEE users
            true, // isVerified
            now,
            now
        );

        // Skip Profile ID mapping to avoid connection leaks
        log.debug("Skipping Profile ID mapping for {} -> {} to prevent connection leaks", mongoId, profileId);

        // Create mentee-specific profile
        MenteeProfile menteeProfile = new MenteeProfile();
        menteeProfile.setId(profileId);
        menteeProfile.setIndustry(industry);
        menteeProfileRepository.save(menteeProfile);

        // Skip mentee profile ID mapping to avoid connection leaks
        log.debug("Skipping mentee profile ID mapping for {} -> {} to prevent connection leaks", mongoId, profileId);
        return profileId;
    }

    /**
     * Migrate individual mentor profile
     */
    private UUID migrateMentorProfile(JsonNode user) {
        String mongoId = mongoDataReader.extractMongoId(user);
        
        // Check if user has email - skip if not
        String email = safeExtractText(user, "username");
        if (email == null || email.trim().isEmpty()) {
            log.info("Skipping user {} - no email address", mongoId);
            return null;
        }
        
        // Create new profile object first to extract name parts
        Profile profile = new Profile();
        
        // Extract and split full name into first and last name
        String fullName = safeExtractText(user, "name");
        String[] nameParts = splitFullName(fullName);
        profile.setFirstName(nameParts[0]);
        profile.setLastName(nameParts[1]);
        
        // Create user in Supabase Auth first to get proper UUID
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("role", "mentor");
        userMetadata.put("mongoId", mongoId);
        userMetadata.put("first_name", profile.getFirstName() != null ? profile.getFirstName() : "");
        userMetadata.put("last_name", profile.getLastName() != null ? profile.getLastName() : "");
        
        UUID profileId = UUID.randomUUID();
        
        log.info("Creating mentor profile with UUID {} for user {}", profileId, mongoId);
        
        // Set profile details
        profile.setId(profileId);
        profile.setEmail(email);
        profile.setRole("mentor"); // ADVISOR maps to mentor
        profile.setPhone(safeExtractText(user, "phoneNumber"));
        profile.setCountry(safeExtractText(user, "country"));
        profile.setGender(safeExtractText(user, "gender"));
        
        // Extract industry from nested object
        if (user.has("industry") && user.get("industry").has("name") && !user.get("industry").get("name").isNull()) {
            profile.setIndustry(user.get("industry").get("name").asText());
        }

        // Extract advisor details
        JsonNode advisorDetails = user.get("advisorDetails");
        String title = null;
        String bio = null;
        String avatarUrl = null;
        Integer yearsExp = null;
        if (advisorDetails != null) {
            if (advisorDetails.has("occupation")) title = advisorDetails.get("occupation").asText();
            if (advisorDetails.has("biography")) bio = advisorDetails.get("biography").asText();
            if (advisorDetails.has("profilePicUrl")) avatarUrl = advisorDetails.get("profilePicUrl").asText();
            if (advisorDetails.has("yrsOfProfessionalWorkExperience")) {
                yearsExp = parseExperienceYears(advisorDetails.get("yrsOfProfessionalWorkExperience").asText());
            }
        }

        if (bio != null) profile.setBio(bio);
        if (avatarUrl != null) profile.setAvatarUrl(avatarUrl);

        ZonedDateTime now = java.time.ZonedDateTime.now();
        
        // Then create the profile entry
        profileRepository.insertProfileSimple(
            profileId,
            profile.getEmail(),
            profile.getFirstName(),
            profile.getLastName(),
            profile.getAvatarUrl(),
            profile.getBio(),
            profile.getPhone(),
            profile.getLocation(),
            profile.getGender(),
            profile.getIndustry(),
            "mentor", // Ensure role is set correctly for ADVISOR users
            false, // isVerified
            now,
            now
        );

        // Skip Profile ID mapping to avoid connection leaks
        log.debug("Skipping Profile ID mapping for {} -> {} to prevent connection leaks", mongoId, profileId);

        // Create mentor-specific profile
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(profileId);
        mentorProfile.setTitle(title);
        mentorProfile.setBio(bio);
        mentorProfile.setAvatarUrl(avatarUrl);
        mentorProfile.setYearsExperience(yearsExp);
        mentorProfile.setSpecializations(new ArrayList<>());
        mentorProfile.setLanguages(List.of("English"));
        mentorProfile.setTimezone("UTC");
        mentorProfile.setHourlyRate(new BigDecimal("50.00"));
        mentorProfile.setIsAvailable(true);

        mentorProfileRepository.save(mentorProfile);

        // Skip mentor profile ID mapping to avoid connection leaks
        log.debug("Skipping mentor profile ID mapping for {} -> {} to prevent connection leaks", mongoId, profileId);
        return profileId;
    }

    /**
     * Create mentor-skill relationships (no transaction)
     */
    private int createMentorSkillRelationships(List<JsonNode> users, Map<String, UUID> topicMappings, Map<String, UUID> mentorMappings) {
        AtomicInteger relationshipCount = new AtomicInteger(0);

        for (JsonNode user : users) {
            // Try userType first, fall back to role field
            String userType = "";
            if (user.has("userType") && !user.get("userType").isNull()) {
                userType = user.get("userType").asText();
            } else if (user.has("role") && !user.get("role").isNull()) {
                userType = user.get("role").asText();
            }
            
            if (!"ADVISOR".equals(userType)) {
                continue;
            }

            try {
                String mongoId = mongoDataReader.extractMongoId(user);
                UUID mentorId = mentorMappings.get(mongoId);
                
                if (mentorId == null) {
                    log.warn("No mentor mapping found for MongoDB ID: {}", mongoId);
                    continue;
                }

                List<String> topicIds = mongoDataReader.extractAdvisorTopics(user);
                
                for (String topicMongoId : topicIds) {
                    UUID skillId = topicMappings.get(topicMongoId);
                    if (skillId != null) {
                        MentorSkill mentorSkill = new MentorSkill(mentorId, skillId);
                        mentorSkillRepository.save(mentorSkill);
                        relationshipCount.incrementAndGet();
                        log.debug("Created mentor-skill relationship: mentor={}, skill={}", mentorId, skillId);
                    } else {
                        log.debug("No skill mapping found for topic: {}", topicMongoId);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to create mentor-skill relationships for user {}: {}", 
                        mongoDataReader.extractMongoId(user), e.getMessage(), e);
            }
        }

        log.info("Created {} mentor-skill relationships", relationshipCount.get());
        return relationshipCount.get();
    }

    /**
     * Migrate sessions from advisor_sessions.json in batches
     */
    private int migrateSessions(List<JsonNode> sessions, Map<String, UUID> userMappings) {
        AtomicInteger sessionCount = new AtomicInteger(0);
        
        int batchSize = 50; // Process sessions in batches of 50
        List<List<JsonNode>> batches = partitionList(sessions, batchSize);
        
        for (int i = 0; i < batches.size(); i++) {
            List<JsonNode> batch = batches.get(i);
            log.info("Processing session batch {} of {} ({} sessions)", i + 1, batches.size(), batch.size());
            
            int batchCount = migrateSessionBatch(batch, userMappings);
            sessionCount.addAndGet(batchCount);
        }

        return sessionCount.get();
    }

    /**
     * Migrate a batch of sessions (no transaction)
     */
    private int migrateSessionBatch(List<JsonNode> sessionBatch, Map<String, UUID> userMappings) {
        AtomicInteger sessionCount = new AtomicInteger(0);

        for (JsonNode sessionData : sessionBatch) {
            try {
                String mongoId = mongoDataReader.extractMongoId(sessionData);
                
                // Skip if we can't get a valid MongoDB ID
                if (mongoId == null || mongoId.trim().isEmpty()) {
                    log.debug("Skipping session with invalid MongoDB ID");
                    continue;
                }
                
                Session session = new Session();
                // Let Hibernate auto-generate the ID to avoid optimistic locking conflicts
                
                // Extract data from advisor_sessions.json structure
                String title = "Mentoring Session";
                String description = "Migrated from MongoDB";
                
                // Try to get topic name for title
                if (sessionData.has("topic") && sessionData.get("topic").has("name")) {
                    title = sessionData.get("topic").get("name").asText();
                    description = "Session on: " + title;
                }
                
                // Set session data
                session.setTitle(title);
                session.setDescription(description);
                
                // Parse start time and duration from advisor_sessions.json
                if (sessionData.has("startTime") && sessionData.get("startTime").has("$date")) {
                    try {
                        String dateStr = sessionData.get("startTime").get("$date").asText();
                        ZonedDateTime startTime = ZonedDateTime.parse(dateStr);
                        session.setScheduledStart(startTime);
                        
                        // Use duration field if available, otherwise default to 1 hour
                        int durationHours = 1;
                        if (sessionData.has("duration")) {
                            durationHours = sessionData.get("duration").asInt(1);
                        }
                        session.setScheduledEnd(startTime.plusHours(durationHours));
                        
                    } catch (Exception e) {
                        log.debug("Could not parse start time for session {}, using default", mongoId);
                        session.setScheduledStart(ZonedDateTime.now().plusDays(1));
                        session.setScheduledEnd(ZonedDateTime.now().plusDays(1).plusHours(1));
                    }
                } else {
                    session.setScheduledStart(ZonedDateTime.now().plusDays(1));
                    session.setScheduledEnd(ZonedDateTime.now().plusDays(1).plusHours(1));
                }
                
                // Set status based on sessionStatus and bookingStatus from advisor_sessions.json
                Session.SessionStatus status = Session.SessionStatus.SCHEDULED;
                if (sessionData.has("sessionStatus")) {
                    String sessionStatus = sessionData.get("sessionStatus").asText().toLowerCase();
                    switch (sessionStatus) {
                        case "upcoming" -> status = Session.SessionStatus.SCHEDULED;
                        case "past", "completed" -> status = Session.SessionStatus.COMPLETED;
                        case "cancelled" -> status = Session.SessionStatus.CANCELLED;
                        default -> status = Session.SessionStatus.SCHEDULED;
                    }
                } else if (sessionData.has("bookingStatus")) {
                    String bookingStatus = sessionData.get("bookingStatus").asText().toLowerCase();
                    switch (bookingStatus) {
                        case "confirmed" -> status = Session.SessionStatus.SCHEDULED;
                        case "cancelled" -> status = Session.SessionStatus.CANCELLED;
                        default -> status = Session.SessionStatus.SCHEDULED;
                    }
                }
                session.setStatus(status);
                
                // Set payment status from paymentStatus field
                if (sessionData.has("paymentStatus")) {
                    String paymentStatus = sessionData.get("paymentStatus").asText();
                    session.setPaid("PAID".equalsIgnoreCase(paymentStatus));
                } else {
                    session.setPaid(false); // Default to unpaid
                }
                
                // Set price from amount field
                if (sessionData.has("amount")) {
                    try {
                        BigDecimal amount = sessionData.get("amount").decimalValue();
                        session.setPrice(amount);
                    } catch (Exception e) {
                        log.debug("Could not parse amount for session {}", mongoId);
                        session.setPrice(BigDecimal.ZERO);
                    }
                } else {
                    session.setPrice(BigDecimal.ZERO);
                }
                
                // Set session type if available
                if (sessionData.has("sessionType")) {
                    String sessionType = sessionData.get("sessionType").asText();
                    // Could store this in description or a custom field if needed
                    session.setDescription(description + " (Type: " + sessionType + ")");
                }
                
                // Try to link to mentor and mentee if IDs exist in userMappings
                UUID mentorUuid = null;
                UUID menteeUuid = null;
                
                if (sessionData.has("advisorId")) {
                    String advisorId = sessionData.get("advisorId").asText();
                    mentorUuid = userMappings.get(advisorId);
                    if (mentorUuid != null) {
                        session.setMentorId(mentorUuid);
                        log.debug("Found mentor mapping for session {}: advisor {} -> mentor {}", mongoId, advisorId, mentorUuid);
                    } else {
                        log.debug("No mentor mapping found for advisor ID: {} (session: {})", advisorId, mongoId);
                    }
                } else {
                    log.debug("Session {} has no advisorId field", mongoId);
                }
                
                if (sessionData.has("adviseeId")) {
                    String adviseeId = sessionData.get("adviseeId").asText();
                    menteeUuid = userMappings.get(adviseeId);
                    if (menteeUuid != null) {
                        session.setMenteeId(menteeUuid);
                        log.debug("Found mentee mapping for session {}: advisee {} -> mentee {}", mongoId, adviseeId, menteeUuid);
                    } else {
                        log.debug("No mentee mapping found for advisee ID: {} (session: {})", adviseeId, mongoId);
                    }
                } else {
                    log.debug("Session {} has no adviseeId field", mongoId);
                }
                
                // Only save session if we have both mentor and mentee IDs (database constraint requirement)
                if (mentorUuid != null && menteeUuid != null) {
                    // Save session with retry logic to handle transient optimistic locking issues
                    Session savedSession = saveSessionWithRetry(session, mongoId);
                    if (savedSession != null) {
                        // Skip session ID mapping to avoid connection leaks
                        log.debug("Skipping session ID mapping for {} -> {} to prevent connection leaks", mongoId, savedSession.getId());
                        
                        sessionCount.incrementAndGet();
                        processedRecords++;
                        
                        log.debug("Successfully migrated session: {} -> {} (mentor: {}, mentee: {})", 
                                mongoId, savedSession.getId(), mentorUuid, menteeUuid);
                    } else {
                        log.warn("Failed to save session {} after retries", mongoId);
                    }
                } else {
                    log.warn("Skipping session {} - missing required relationships: mentor={}, mentee={}", 
                            mongoId, mentorUuid != null ? "found" : "missing", menteeUuid != null ? "found" : "missing");
                }

            } catch (Exception e) {
                log.error("Failed to migrate session {}: {}", mongoDataReader.extractMongoId(sessionData), e.getMessage());
                // Continue with next session instead of failing the entire migration
            }
        }

        return sessionCount.get();
    }

    /**
     * Save session with retry logic to handle optimistic locking conflicts
     */
    private Session saveSessionWithRetry(Session session, String mongoId) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                // Clear any managed state and ensure this is a fresh entity
                entityManager.detach(session);
                
                Session savedSession = sessionRepository.save(session);
                entityManager.flush(); // Force immediate persistence
                
                log.debug("Successfully saved session {} on attempt {}", mongoId, attempt + 1);
                return savedSession;
                
            } catch (Exception e) {
                attempt++;
                String errorMessage = e.getMessage();
                
                // Check if this is an optimistic locking or concurrent modification error
                if (errorMessage != null && (
                    errorMessage.contains("Row was updated or deleted by another transaction") ||
                    errorMessage.contains("OptimisticLockException") ||
                    errorMessage.contains("StaleObjectStateException") ||
                    errorMessage.contains("unsaved-value mapping was incorrect"))) {
                    
                    log.warn("Optimistic locking conflict for session {} (attempt {}/{}): {}", 
                            mongoId, attempt, maxRetries, errorMessage);
                    
                    if (attempt >= maxRetries) {
                        log.error("Max retries reached for session {} due to persistent optimistic locking conflicts", mongoId);
                        return null;
                    }
                    
                    // Wait before retry with exponential backoff
                    try {
                        Thread.sleep(100 * attempt); // 100ms, 200ms, 300ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    
                    // Create a fresh session object for retry to avoid Hibernate state issues
                    session = cloneSessionForRetry(session);
                    
                } else {
                    // Non-optimistic locking error, don't retry
                    log.error("Non-retryable error saving session {}: {}", mongoId, errorMessage);
                    return null;
                }
            }
        }
        
        return null;
    }

    /**
     * Clone session object for retry attempts to avoid Hibernate state conflicts
     */
    private Session cloneSessionForRetry(Session original) {
        Session cloned = new Session();
        // Don't copy the ID - let Hibernate generate it
        cloned.setMentorId(original.getMentorId());
        cloned.setMenteeId(original.getMenteeId());
        cloned.setTitle(original.getTitle());
        cloned.setDescription(original.getDescription());
        cloned.setScheduledStart(original.getScheduledStart());
        cloned.setScheduledEnd(original.getScheduledEnd());
        cloned.setStatus(original.getStatus());
        cloned.setMeetingUrl(original.getMeetingUrl());
        cloned.setNotes(original.getNotes());
        cloned.setRating(original.getRating());
        cloned.setFeedback(original.getFeedback());
        cloned.setPrice(original.getPrice());
        cloned.setPaid(original.getPaid());
        cloned.setReminderSent(original.getReminderSent());
        // Don't copy audit fields - let @PrePersist handle them
        return cloned;
    }

    /**
     * Validate migration results
     */
    private MigrationValidationResult validateMigrationResults() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("profiles", profileRepository.count());
        counts.put("mentee_profiles", menteeProfileRepository.count());
        counts.put("mentor_profiles", mentorProfileRepository.count());
        counts.put("skills", skillRepository.count());
        counts.put("mentor_skills", mentorSkillRepository.count());
        counts.put("sessions", sessionRepository.count());

        // Skip mapping statistics to avoid connection leaks
        Map<String, Long> mappingCounts = new HashMap<>();
        Map<String, Object> validation = new HashMap<>();
        validation.put("status", "ID mapping disabled to prevent connection leaks");

        return new MigrationValidationResult(counts, mappingCounts, validation);
    }

    /**
     * Get current migration status
     */
    public MigrationStatusInfo getStatus() {
        return new MigrationStatusInfo(
                currentStatus,
                currentStep,
                totalRecords,
                processedRecords,
                calculateProgress(),
                new ArrayList<>(migrationLog)
        );
    }

    /**
     * Helper methods
     */
    private void updateStatus(String step) {
        this.currentStep = step;
        log.info("Migration step: {}", step);
    }

    private void addLog(String message) {
        migrationLog.add(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME) + " - " + message);
        log.info(message);
    }

    private double calculateProgress() {
        if (totalRecords == 0) return 0.0;
        return (double) processedRecords / totalRecords * 100.0;
    }

    private Integer parseExperienceYears(String experience) {
        if (experience == null || experience.trim().isEmpty()) {
            return null;
        }
        
        // Parse experience strings like "10 - 19 years", "20 years or more"
        if (experience.contains("20 years or more")) {
            return 20;
        } else if (experience.contains("10 - 19")) {
            return 15; // Middle value
        } else if (experience.contains("5 - 9")) {
            return 7;
        } else if (experience.contains("1 - 4")) {
            return 3;
        }
        
        return null;
    }

    /**
     * Safely extract text from JSON node, returning null if missing or null
     */
    private String safeExtractText(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            String value = node.get(fieldName).asText();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
    
    /**
     * Split full name into first and last name
     * @param fullName the full name to split
     * @return array with [firstName, lastName]
     */
    private String[] splitFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return new String[]{null, null};
        }
        
        String[] parts = fullName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], null};
        } else {
            return new String[]{parts[0], parts[1]};
        }
    }
    
    /**
     * Validate email format
     * @param email the email to validate
     * @return true if email is valid, false otherwise
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Clean email first
        email = email.trim().toLowerCase();
        
        // Check length constraints
        if (email.length() > 320) { // RFC 5321 limit
            return false;
        }
        
        // Check for basic email format with more comprehensive regex
        String emailRegex = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$";
        if (!email.matches(emailRegex)) {
            return false;
        }
        
        // Additional validation checks
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        // Check local part length (64 char limit)
        if (localPart.length() > 64 || localPart.isEmpty()) {
            return false;
        }
        
        // Check domain part
        if (domain.length() > 253 || domain.isEmpty()) {
            return false;
        }
        
        // Domain must have at least one dot
        if (!domain.contains(".")) {
            return false;
        }
        
        // Domain parts shouldn't start or end with hyphens
        String[] domainParts = domain.split("\\.");
        for (String part : domainParts) {
            if (part.isEmpty() || part.startsWith("-") || part.endsWith("-")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generate a compliant password for Supabase Auth
     * Meets requirements: 8+ chars, uppercase, lowercase, number, special char
     * @param email user email for uniqueness
     * @return secure password string
     */
    private String generateCompliantPassword(String email) {
        // Create a unique but deterministic password based on email hash
        // This ensures the same user gets the same password if migration is re-run
        int emailHash = Math.abs(email.hashCode());
        
        // Base password components that meet Supabase requirements
        String basePassword = "Migration2024!";
        String uniqueSuffix = String.format("%06d", emailHash % 1000000);
        
        // Combine to create a strong, unique password
        return basePassword + uniqueSuffix + "$";
    }
    
    /**
     * Create user in Supabase Auth and return the user ID
     * @param email user email
     * @param userMetadata additional user metadata
     * @return UUID of created user, or null if creation failed
     * 
     * NOTE: Currently disabled during migration to avoid database trigger issues
     */
    @SuppressWarnings("unused")
    private UUID createSupabaseAuthUser(String email, Map<String, Object> userMetadata) {
        final int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                // Clean and validate email format
                email = email.trim().toLowerCase();
                if (!isValidEmail(email)) {
                    log.error("Invalid email format: {}", email);
                    return null;
                }
                
                // Generate a unique secure password for this user
                String tempPassword = generateCompliantPassword(email);
                
                // Clean up user metadata to ensure it's valid and remove null/empty values
                Map<String, Object> cleanMetadata = new HashMap<>();
                if (userMetadata != null) {
                    userMetadata.forEach((key, value) -> {
                        if (value != null && !value.toString().trim().isEmpty()) {
                            // Ensure all metadata values are strings and not too long
                            String cleanValue = value.toString().trim();
                            if (cleanValue.length() > 255) {
                                cleanValue = cleanValue.substring(0, 255);
                            }
                            cleanMetadata.put(key, cleanValue);
                        }
                    });
                }
                
                log.debug("Attempting to create Supabase Auth user for email: {} (attempt {}/{})", email, attempt + 1, maxRetries);
                
                // Progressive delay to avoid rate limiting
                if (attempt > 0) {
                    long delay = 1000L * (attempt + 1); // Increasing delay: 1s, 2s, 3s
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                
                // First try creating user with minimal data but including role to avoid trigger issues
                log.debug("Attempting minimal user creation first for email: {}", email);
                Mono<JsonNode> userMono;
                
                // Extract role from metadata or default to appropriate role
                String userRole = "mentee"; // Default role
                if (cleanMetadata.containsKey("role")) {
                    userRole = cleanMetadata.get("role").toString();
                }
                
                try {
                    // Try the minimal user creation with role metadata first
                    userMono = supabaseAuthService.createMinimalUser(email, tempPassword, userRole);
                    JsonNode userResult = userMono.block();
                    
                    if (userResult != null && userResult.has("id")) {
                        // Success with minimal user - now try to update metadata if we have any
                        String userId = userResult.get("id").asText();
                        if (!cleanMetadata.isEmpty()) {
                            log.debug("Updating user metadata for {}", userId);
                            try {
                                supabaseAuthService.updateUserMetadata(userId, cleanMetadata).block();
                                log.debug("Successfully updated metadata for user {}", userId);
                            } catch (Exception metaError) {
                                log.warn("Failed to update metadata for user {}: {}", userId, metaError.getMessage());
                                // Continue anyway - user was created successfully
                            }
                        }
                        log.info("Successfully created Supabase Auth user: {} -> {} (minimal creation)", email, userId);
                        return UUID.fromString(userId);
                    }
                } catch (Exception minimalError) {
                    log.warn("Minimal user creation failed for {}: {}", email, minimalError.getMessage());
                    
                    // If minimal creation fails due to trigger issues, skip Supabase Auth entirely
                    if (minimalError.getMessage() != null && 
                        (minimalError.getMessage().contains("null value in column \"role\"") ||
                         minimalError.getMessage().contains("Database error creating new user"))) {
                        log.info("Detected database trigger constraint issue for {}. Skipping Supabase Auth creation.", email);
                        return null; // Will use fallback UUID generation
                    }
                    // Fall back to full user creation for other errors
                }
                
                // Fallback: try full user creation with metadata
                log.debug("Falling back to full user creation with metadata for: {}", email);
                userMono = supabaseAuthService.createUser(email, tempPassword, cleanMetadata);
                JsonNode userResult = userMono.block(); // Block since we're in a synchronous migration
                
                if (userResult != null && userResult.has("id")) {
                    String userId = userResult.get("id").asText();
                    log.info("Successfully created Supabase Auth user: {} -> {} (attempt {})", email, userId, attempt + 1);
                    return UUID.fromString(userId);
                } else {
                    log.warn("Failed to create Supabase user for email: {} - no ID in response. Response: {}", email, userResult);
                    return null;
                }
                
            } catch (Exception e) {
                attempt++;
                String errorMessage = e.getMessage();
                
                // Handle specific error cases
                if (errorMessage != null) {
                    if (errorMessage.contains("already registered") || errorMessage.contains("User already registered")) {
                        log.warn("User already exists in Supabase Auth: {} - attempting to retrieve existing user", email);
                        // Try to get existing user by email if possible
                        return null; // For now, just skip - could implement user lookup later
                    } else if (errorMessage.contains("500") || errorMessage.contains("Internal Server Error") || 
                               errorMessage.contains("Database error creating new user")) {
                        log.warn("Supabase server error for email: {} (attempt {}/{}): {} - will retry", 
                                email, attempt, maxRetries, errorMessage);
                        if (attempt >= maxRetries) {
                            log.error("Max retries reached for email: {} - continuing with fallback", email);
                            return null;
                        }
                        continue; // Retry
                    } else if (errorMessage.contains("400") || errorMessage.contains("Bad Request")) {
                        log.error("Bad request for email: {} - invalid data format. Skipping.", email);
                        return null; // Don't retry for bad requests
                    } else if (errorMessage.contains("Invalid email format")) {
                        log.error("Invalid email format detected: {} - skipping user creation", email);
                        return null; // Don't retry for invalid email
                    } else if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                        log.warn("Rate limit hit for email: {} (attempt {}/{}) - will retry with longer delay", 
                                email, attempt, maxRetries);
                        if (attempt >= maxRetries) {
                            log.error("Max retries reached due to rate limiting for email: {}", email);
                            return null;
                        }
                        // Add extra delay for rate limiting
                        try {
                            Thread.sleep(5000); // 5 second delay for rate limiting
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue; // Retry
                    }
                }
                
                if (attempt >= maxRetries) {
                    log.error("Failed to create Supabase user for email: {} after {} attempts - Error: {}", 
                            email, maxRetries, errorMessage);
                    return null;
                } else {
                    log.warn("Attempt {}/{} failed for email: {} - Error: {}", attempt, maxRetries, email, errorMessage);
                }
            }
        }
        
        return null;
    }

    // ID mapping functionality removed to prevent connection leaks
    // The migration now focuses purely on data transfer without tracking mappings

    /**
     * Utility method to partition a list into smaller batches
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    /**
     * Migration status enumeration
     */
    public enum MigrationStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, ROLLED_BACK
    }

    /**
     * Record classes for migration results
     */
    public record MigrationResult(
            MigrationStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int totalRecords,
            int processedRecords,
            List<String> log,
            MigrationValidationResult validation,
            String errorMessage
    ) {}

    public record MigrationUserResult(
            Map<String, UUID> menteeMappings,
            Map<String, UUID> mentorMappings,
            Map<String, UUID> allUserMappings,
            int menteeCount,
            int mentorCount
    ) {}

    public record MigrationValidationResult(
            Map<String, Long> entityCounts,
            Map<String, Long> mappingCounts,
            Map<String, Object> integrityCheck
    ) {}

    public record MigrationStatusInfo(
            MigrationStatus status,
            String currentStep,
            int totalRecords,
            int processedRecords,
            double progressPercentage,
            List<String> log
    ) {}

    /**
     * Custom exception for migration errors
     */
    public static class MigrationException extends RuntimeException {
        public MigrationException(String message) {
            super(message);
        }
        
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
