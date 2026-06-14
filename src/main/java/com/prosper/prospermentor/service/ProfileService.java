package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.MenteeProfile;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user profiles
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final MenteeProfileRepository menteeProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;

    /**
     * Create a new profile for a user
     */
    public Optional<Map<String, Object>> createProfile(UUID userId, String email, String role) {
        log.info("Creating profile for user: {} with role: {}", email, role);

        try {
            // Check if profile already exists
            if (profileRepository.existsById(userId)) {
                log.warn("Profile already exists for user ID: {}", userId);
                return getCompleteProfile(userId);
            }

            // Create new profile
            Profile profile = new Profile();
            profile.setId(userId);
            profile.setEmail(email);
            profile.setUsername(generateUniqueUsername(email, null, null));
            profile.setRole(role != null ? role : "mentee");
            profile.setIsVerified(false);

            // Save profile
            Profile savedProfile = profileRepository.save(profile);
            log.info("Profile created successfully for user: {}", email);

            // Return complete profile
            return getCompleteProfile(savedProfile.getId());

        } catch (Exception e) {
            log.error("Error creating profile for user {}: {}", email, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Create a new profile for a user with additional details
     */
    public Optional<Map<String, Object>> createProfileWithDetails(
            UUID userId,
            String email,
            String role,
            String firstName,
            String lastName,
            String phoneNumber,
            String dateOfBirth) {
        log.info("Creating profile with details for user: {} with role: {}", email, role);

        try {
            // Check if profile already exists
            if (profileRepository.existsById(userId)) {
                log.warn("Profile already exists for user ID: {}", userId);
                return getCompleteProfile(userId);
            }

            // Create new profile
            Profile profile = new Profile();
            profile.setId(userId);
            profile.setEmail(email);
            profile.setUsername(generateUniqueUsername(email, firstName, lastName));
            profile.setRole(role != null ? role : "mentee");
            profile.setFirstName(firstName);
            profile.setLastName(lastName);
            profile.setPhone(phoneNumber);

            // Parse and set date of birth if provided
            if (dateOfBirth != null && !dateOfBirth.trim().isEmpty()) {
                try {
                    profile.setDob(java.time.LocalDate.parse(dateOfBirth));
                } catch (Exception e) {
                    log.warn("Failed to parse date of birth: {}", dateOfBirth);
                }
            }

            profile.setIsVerified(false);

            // Save profile
            Profile savedProfile = profileRepository.save(profile);
            log.info("Profile with details created successfully for user: {}", email);

            // Return complete profile
            return getCompleteProfile(savedProfile.getId());

        } catch (Exception e) {
            log.error("Error creating profile with details for user {}: {}", email, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Get complete profile information for a user by their ID
     */
    public Optional<Map<String, Object>> getCompleteProfile(UUID userId) {
        log.debug("Fetching complete profile for user ID: {}", userId);

        // Use findByIdWithCompany to eagerly fetch the company relationship
        Optional<Profile> profileOpt = profileRepository.findByIdWithCompany(userId);
        if (profileOpt.isEmpty()) {
            log.warn("Profile not found for user ID: {}", userId);
            return Optional.empty();
        }

        Profile profile = profileOpt.get();
        Map<String, Object> completeProfile = new HashMap<>();

        // Add basic profile information
        completeProfile.put("id", profile.getId());
        completeProfile.put("email", profile.getEmail());
        completeProfile.put("firstName", profile.getFirstName());
        completeProfile.put("lastName", profile.getLastName());
        completeProfile.put("avatarUrl", profile.getAvatarUrl());
        completeProfile.put("bio", profile.getBio());
        completeProfile.put("phone", profile.getPhone());
        completeProfile.put("location", profile.getLocation());
        completeProfile.put("role", profile.getRole());
        completeProfile.put("isVerified", profile.getIsVerified());
        completeProfile.put("createdAt", profile.getCreatedAt());
        completeProfile.put("updatedAt", profile.getUpdatedAt());
        completeProfile.put("expertise", profile.getExpertise());
        completeProfile.put("interests", profile.getInterests());
        completeProfile.put("dob", profile.getDob());
        completeProfile.put("gender", profile.getGender());
        completeProfile.put("industry", profile.getIndustry());
        completeProfile.put("howDidYouKnowAboutUs", profile.getHowDidYouKnowAboutUs());
        completeProfile.put("linkedinUrl", profile.getLinkedinUrl());
        completeProfile.put("favouriteQuote", profile.getFavouriteQuote());
        completeProfile.put("country", profile.getCountry());

        // Add company information if linked
        if (profile.getCompany() != null) {
            completeProfile.put("company", createCompanyMap(profile.getCompany()));
        }

        // Add role-specific profile information
        String role = profile.getRole();
        if (role != null) {
            switch (role.toUpperCase()) {
                case "MENTEE":
                    Optional<MenteeProfile> menteeProfile = menteeProfileRepository.findById(userId);
                    if (menteeProfile.isPresent()) {
                        completeProfile.put("menteeProfile", createMenteeProfileMap(menteeProfile.get()));
                    }
                    break;
                case "MENTOR":
                    Optional<MentorProfile> mentorProfile = mentorProfileRepository.findById(userId);
                    if (mentorProfile.isPresent()) {
                        completeProfile.put("mentorProfile", createMentorProfileMap(mentorProfile.get()));
                    }
                    break;
                default:
                    log.debug("No specific profile type for role: {}", role);
            }
        }

        log.debug("Successfully retrieved complete profile for user ID: {}", userId);
        return Optional.of(completeProfile);
    }

    /**
     * Get basic profile information for a user by their ID
     */
    public Optional<Profile> getBasicProfile(UUID userId) {
        log.debug("Fetching basic profile for user ID: {}", userId);
        return profileRepository.findById(userId);
    }

    public Optional<Profile> getProfileWithCompany(UUID userId) {
        log.debug("Fetching profile with company for user ID: {}", userId);
        return profileRepository.findByIdWithCompany(userId);
    }

    /**
     * Get mentee-specific profile information
     */
    public Optional<MenteeProfile> getMenteeProfile(UUID userId) {
        log.debug("Fetching mentee profile for user ID: {}", userId);
        return menteeProfileRepository.findById(userId);
    }

    /**
     * Get mentor-specific profile information
     */
    public Optional<MentorProfile> getMentorProfile(UUID userId) {
        log.debug("Fetching mentor profile for user ID: {}", userId);
        return mentorProfileRepository.findById(userId);
    }

    /**
     * Check if a profile exists for the given user ID
     */
    public boolean profileExists(UUID userId) {
        return profileRepository.existsById(userId);
    }

    /**
     * Get profile by email
     */
    public Optional<Profile> getProfileByEmail(String email) {
        log.debug("Fetching profile for email: {}", email);
        return profileRepository.findByEmail(email);
    }

    /**
     * Get all mentors with their complete profile information
     */
    public List<Profile> getAllMentors() {
        return profileRepository.findAllByRole("mentor");
    }

    /**
     * Get all mentors with pagination and filters
     */
    public Page<Profile> getAllMentorsPaginated(int page, int size, Boolean isVerified, String searchTerm) {
        log.debug("Fetching mentors - page: {}, size: {}, isVerified: {}, searchTerm: {}",
                  page, size, isVerified, searchTerm);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"));

        return profileRepository.findByRoleWithFilters("mentor", isVerified, searchTerm, pageable);
    }

    /**
     * Get all basic mentor profiles (without mentor-specific details)
     */
    public List<Profile> getAllMentorProfiles() {
        log.debug("Fetching all mentor profiles");
        return profileRepository.findByRole("mentor");
    }

    /**
     * Create a map representation of MenteeProfile
     */
    private Map<String, Object> createMenteeProfileMap(MenteeProfile menteeProfile) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", menteeProfile.getId());
        map.put("careerLevel", menteeProfile.getCareerLevel());
        map.put("industry", menteeProfile.getIndustry());
        map.put("goals", menteeProfile.getGoals());
        map.put("interests", menteeProfile.getInterests());
        map.put("learningStyle", menteeProfile.getLearningStyle());
        map.put("preferredSessionDuration", menteeProfile.getPreferredSessionDuration());
        map.put("budgetRange", menteeProfile.getBudgetRange());
        map.put("subGoals", menteeProfile.getSubGoals());
        map.put("goalNotes", menteeProfile.getGoalNotes());
        map.put("createdAt", menteeProfile.getCreatedAt());
        map.put("updatedAt", menteeProfile.getUpdatedAt());
        return map;
    }

    /**
     * Create a map representation of MentorProfile
     */
    private Map<String, Object> createMentorProfileMap(MentorProfile mentorProfile) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", mentorProfile.getId());
        map.put("title", mentorProfile.getTitle());
        map.put("company", mentorProfile.getCompany());
        map.put("yearsExperience", mentorProfile.getYearsExperience());
        map.put("hourlyRate", mentorProfile.getHourlyRate());
        map.put("specializations", mentorProfile.getSpecializations());
        map.put("languages", mentorProfile.getLanguages());
        map.put("timezone", mentorProfile.getTimezone());
        map.put("availabilityHours", mentorProfile.getAvailabilityHours());
        map.put("totalSessions", mentorProfile.getTotalSessions());
        map.put("rating", mentorProfile.getRating());
        map.put("totalReviews", mentorProfile.getTotalReviews());
        map.put("isAvailable", mentorProfile.getIsAvailable());
        map.put("bio", mentorProfile.getBio());
        map.put("avatarUrl", mentorProfile.getAvatarUrl());
        map.put("createdAt", mentorProfile.getCreatedAt());
        map.put("updatedAt", mentorProfile.getUpdatedAt());
        return map;
    }

    /**
     * Create a complete map representation of a mentor profile including basic profile and mentor-specific data
     */
    private Map<String, Object> createMentorCompleteProfileMap(Profile profile) {
        Map<String, Object> completeProfile = new HashMap<>();
        
        // Add basic profile information
        completeProfile.put("id", profile.getId());
        completeProfile.put("email", profile.getEmail());
        completeProfile.put("firstName", profile.getFirstName());
        completeProfile.put("lastName", profile.getLastName());
        completeProfile.put("avatarUrl", profile.getAvatarUrl());
        completeProfile.put("bio", profile.getBio());
        completeProfile.put("phone", profile.getPhone());
        completeProfile.put("location", profile.getLocation());
        completeProfile.put("role", profile.getRole());
        completeProfile.put("isVerified", profile.getIsVerified());
        completeProfile.put("createdAt", profile.getCreatedAt());
        completeProfile.put("updatedAt", profile.getUpdatedAt());
        completeProfile.put("expertise", profile.getExpertise());
        completeProfile.put("interests", profile.getInterests());
        completeProfile.put("dob", profile.getDob());
        completeProfile.put("gender", profile.getGender());
        completeProfile.put("industry", profile.getIndustry());
        completeProfile.put("howDidYouKnowAboutUs", profile.getHowDidYouKnowAboutUs());
        completeProfile.put("linkedinUrl", profile.getLinkedinUrl());
        completeProfile.put("favouriteQuote", profile.getFavouriteQuote());
        completeProfile.put("country", profile.getCountry());

        // Add mentor-specific profile information if available
        Optional<MentorProfile> mentorProfile = mentorProfileRepository.findById(profile.getId());
        if (mentorProfile.isPresent()) {
            completeProfile.put("mentorProfile", createMentorProfileMap(mentorProfile.get()));
        }

        return completeProfile;
    }

    /**
     * Create a map representation of Company
     */
    private Map<String, Object> createCompanyMap(Company company) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", company.getId());
        map.put("name", company.getName());
        map.put("emailAddress", company.getEmailAddress());
        map.put("phoneNumber", company.getPhoneNumber());
        map.put("logoUrl", company.getLogoUrl());
        map.put("isActive", company.getIsActive());
        map.put("registrationCompleted", company.getRegistrationCompleted());
        map.put("createdAt", company.getCreatedAt());
        map.put("updatedAt", company.getUpdatedAt());
        return map;
    }

    /**
     * Update basic profile information
     */
    public Optional<Profile> updateProfile(UUID userId, Map<String, Object> updates) {
        log.debug("Updating profile for user ID: {}", userId);
        
        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            log.warn("Profile not found for user ID: {}", userId);
            return Optional.empty();
        }

        Profile profile = profileOpt.get();
        
        // Update fields if provided
        if (updates.containsKey("firstName")) {
            profile.setFirstName((String) updates.get("firstName"));
        }
        if (updates.containsKey("lastName")) {
            profile.setLastName((String) updates.get("lastName"));
        }
        if (updates.containsKey("bio")) {
            profile.setBio((String) updates.get("bio"));
        }
        if (updates.containsKey("phone")) {
            profile.setPhone((String) updates.get("phone"));
        }
        if (updates.containsKey("location")) {
            profile.setLocation((String) updates.get("location"));
        }
        if (updates.containsKey("avatarUrl")) {
            profile.setAvatarUrl((String) updates.get("avatarUrl"));
        }
        if (updates.containsKey("industry")) {
            profile.setIndustry((String) updates.get("industry"));
        }
        if (updates.containsKey("linkedinUrl")) {
            profile.setLinkedinUrl((String) updates.get("linkedinUrl"));
        }
        if (updates.containsKey("favouriteQuote")) {
            profile.setFavouriteQuote((String) updates.get("favouriteQuote"));
        }
        if (updates.containsKey("country")) {
            profile.setCountry((String) updates.get("country"));
        }

        Profile savedProfile = profileRepository.save(profile);
        log.debug("Successfully updated profile for user ID: {}", userId);
        return Optional.of(savedProfile);
    }

    /**
     * Generate a unique username for a profile.
     */
    public String generateUniqueUsername(String email, String firstName, String lastName) {
        String baseUsername = buildBaseUsername(email, firstName, lastName);
        String candidate = baseUsername;

        if (!profileRepository.existsByUsername(candidate)) {
            return candidate;
        }

        for (int suffix = 2; suffix <= 99; suffix++) {
            candidate = baseUsername + "_" + suffix;
            if (!profileRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return baseUsername + "_" + uuidSuffix;
    }

    private String buildBaseUsername(String email, String firstName, String lastName) {
        String preferredCandidate = joinNameParts(firstName, lastName);
        if (preferredCandidate == null || preferredCandidate.isBlank()) {
            preferredCandidate = extractEmailPrefix(email);
        }

        String normalized = normalizeUsername(preferredCandidate);
        if (normalized == null || normalized.isBlank()) {
            normalized = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        return normalized;
    }

    private String joinNameParts(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();

        String combined = (first + "_" + last).replaceAll("^_+|_+$", "");
        return combined.isBlank() ? null : combined;
    }

    private String extractEmailPrefix(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        String normalizedEmail = email.trim().toLowerCase();
        int atIndex = normalizedEmail.indexOf('@');
        return atIndex > 0 ? normalizedEmail.substring(0, atIndex) : normalizedEmail;
    }

    private String normalizeUsername(String value) {
        if (value == null) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24).replaceAll("_+$", "");
        }

        return normalized;
    }
}
