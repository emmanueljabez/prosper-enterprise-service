package com.prosper.prospermentor.security;

import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * UserDetailsService implementation for Supabase users
 * Loads user details from the local profile database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseUserDetailsService implements UserDetailsService {

    private final ProfileService profileService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);
        
        Optional<Profile> profileOpt = profileService.getProfileByEmail(email);
        
        if (profileOpt.isEmpty()) {
            log.warn("User not found with email: {}", email);
            throw new UsernameNotFoundException("User not found with email: " + email);
        }

        Profile profile = profileOpt.get();
        
        return new SupabaseUserDetails(
                profile.getId().toString(),
                profile.getEmail(),
                profile.getRole(),
                profile,
                profile.getIsVerified() != null ? profile.getIsVerified() : false
        );
    }

    /**
     * Load user details by user ID (UUID)
     */
    public UserDetails loadUserByUserId(UUID userId) throws UsernameNotFoundException {
        log.debug("Loading user by ID: {}", userId);
        
        Optional<Profile> profileOpt = profileService.getBasicProfile(userId);
        
        if (profileOpt.isEmpty()) {
            log.warn("User not found with ID: {}", userId);
            throw new UsernameNotFoundException("User not found with ID: " + userId);
        }

        Profile profile = profileOpt.get();
        
        return new SupabaseUserDetails(
                profile.getId().toString(),
                profile.getEmail(),
                profile.getRole(),
                profile,
                profile.getIsVerified() != null ? profile.getIsVerified() : false
        );
    }

    /**
     * Load user details by user ID (string)
     */
    public UserDetails loadUserByUserId(String userId) throws UsernameNotFoundException {
        try {
            UUID userUuid = UUID.fromString(userId);
            return loadUserByUserId(userUuid);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format: {}", userId);
            throw new UsernameNotFoundException("Invalid user ID format: " + userId);
        }
    }

    /**
     * Create UserDetails from JWT information only (when profile doesn't exist yet)
     */
    public UserDetails createUserDetailsFromJwt(String userId, String email, String role) {
        log.debug("Creating UserDetails from JWT for user: {}", email);
        
        return new SupabaseUserDetails(userId, email, role);
    }

    /**
     * Check if user exists in the local database
     */
    public boolean userExists(String email) {
        return profileService.getProfileByEmail(email).isPresent();
    }

    /**
     * Check if user exists by ID
     */
    public boolean userExistsById(UUID userId) {
        return profileService.profileExists(userId);
    }

    /**
     * Check if user exists by ID (string)
     */
    public boolean userExistsById(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            return userExistsById(userUuid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
