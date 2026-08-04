package com.prosper.prospermentor.security;

import com.prosper.prospermentor.entity.Profile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * UserDetails implementation for Supabase authenticated users
 * Integrates with Spring Security's authentication mechanism
 */
@AllArgsConstructor
@Getter
public class SupabaseUserDetails implements UserDetails {
    
    private final String userId;
    private final String email;
    private final String role;
    private final Profile profile; // Optional - may be null if profile doesn't exist yet
    private final boolean enabled;
    private final boolean emailVerified;

    /**
     * Constructor with minimal information (from JWT)
     */
    public SupabaseUserDetails(String userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role != null ? role : "MENTEE";
        this.profile = null;
        this.enabled = true;
        this.emailVerified = true; // Assume verified if JWT is valid
    }

    /**
     * Constructor with full profile information
     */
    public SupabaseUserDetails(String userId, String email, String role, Profile profile, boolean emailVerified) {
        this.userId = userId;
        this.email = email;
        this.role = role != null ? role : "MENTEE";
        this.profile = profile;
        this.enabled = true;
        this.emailVerified = emailVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Convert role to Spring Security authority
        String authority = "ROLE_" + (role != null ? role.toUpperCase() : "MENTEE");
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        // Password is managed by Supabase, not stored locally
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the Supabase user ID as UUID
     */
    public UUID getUserIdAsUuid() {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String roleName) {
        return roleName != null && roleName.equalsIgnoreCase(this.role);
    }

    /**
     * Check if user is a mentor
     */
    public boolean isMentor() {
        return hasRole("MENTOR");
    }

    /**
     * Check if user is a mentee
     */
    public boolean isMentee() {
        return hasRole("MENTEE");
    }

    /**
     * Check if user is an admin
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Backward-compatible company admin check.
     * Existing environments may still use COMPANY or corporate_admin-like tokens.
     */
    public boolean isCompanyAdmin() {
        return hasRole("COMPANY_ADMIN")
                || hasRole("CORPORATE_ADMIN")
                || hasRole("COMPANY");
    }

    /**
     * Get display name (first name + last name or email if not available)
     */
    public String getDisplayName() {
        if (profile != null && profile.getFirstName() != null) {
            String displayName = profile.getFirstName();
            if (profile.getLastName() != null) {
                displayName += " " + profile.getLastName();
            }
            return displayName;
        }
        return email;
    }

    /**
     * Check if user has complete profile information
     */
    public boolean hasCompleteProfile() {
        return profile != null && 
               profile.getFirstName() != null && 
               !profile.getFirstName().trim().isEmpty();
    }

    @Override
    public String toString() {
        return "SupabaseUserDetails{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", enabled=" + enabled +
                ", emailVerified=" + emailVerified +
                ", hasProfile=" + (profile != null) +
                '}';
    }
}
