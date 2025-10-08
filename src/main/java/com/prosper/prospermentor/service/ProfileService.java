package com.prosper.prospermentor.service;

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
     * Get complete profile information for a user by their ID
     */
    public Optional<Map<String, Object>> getCompleteProfile(UUID userId) {
        log.debug("Fetching complete profile for user ID: {}", userId);
        
        Optional<Profile> profileOpt = profileRepository.findById(userId);
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
}
