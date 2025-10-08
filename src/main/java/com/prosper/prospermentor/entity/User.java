package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User entity representing application users
 * This entity stores local user data synchronized with Supabase Auth
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    /**
     * Supabase Auth user UUID - this is the primary identifier
     */
    @Column(name = "supabase_user_id", unique = true, length = 36)
    private String supabaseUserId;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Password field is optional since authentication is handled by Supabase
     * This can be used for local fallback or migration scenarios
     */
    @Column(name = "password")
    private String password;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.MENTEE;

    /**
     * Store additional user metadata from Supabase
     */
    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "bio", length = 1000)
    private String bio;

    /**
     * Track last login for analytics
     */
    @Column(name = "last_login_at")
    private java.time.LocalDateTime lastLoginAt;

    public enum UserRole {
        ADMIN,
        MENTOR,
        MENTEE
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return email; // Fallback to email if no name is provided
    }

    /**
     * Check if user has Supabase integration
     */
    public boolean hasSupabaseAuth() {
        return supabaseUserId != null && !supabaseUserId.trim().isEmpty();
    }
}
