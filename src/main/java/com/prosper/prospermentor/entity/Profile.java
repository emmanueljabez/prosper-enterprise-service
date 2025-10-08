package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Profile entity representing user profile information
 * Maps to the profiles table in Supabase
 */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    /**
     * Primary key - UUID that links to auth.users
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * User email - required field
     */
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * First name
     */
    @Column(name = "first_name")
    private String firstName;

    /**
     * Last name
     */
    @Column(name = "last_name")
    private String lastName;

    /**
     * Avatar/profile picture URL
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * User biography
     */
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * Phone number
     */
    @Column(name = "phone")
    private String phone;

    /**
     * Location/address
     */
    @Column(name = "location")
    private String location;

    /**
     * User role enum - stored as USER-DEFINED type in PostgreSQL
     * Store as string and handle casting in repository layer
     */
    @Column(name = "role", nullable = false)
    private String role;

    /**
     * Whether the user is verified
     */
    @Column(name = "is_verified")
    private Boolean isVerified = false;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /**
     * Areas of expertise - stored as array in PostgreSQL
     */
    @Column(name = "expertise", columnDefinition = "text[]")
    private List<String> expertise;

    /**
     * User interests - stored as array in PostgreSQL
     */
    @Column(name = "interests", columnDefinition = "text[]")
    private List<String> interests;

    /**
     * Date of birth
     */
    @Column(name = "dob")
    private LocalDate dob;

    /**
     * Gender
     */
    @Column(name = "gender")
    private String gender;

    /**
     * Industry
     */
    @Column(name = "industry")
    private String industry;

    /**
     * How did you know about us
     */
    @Column(name = "how_did_you_know_about_us")
    private String howDidYouKnowAboutUs;

    /**
     * LinkedIn profile URL
     */
    @Column(name = "linkedin_url")
    private String linkedinUrl;

    /**
     * Favorite quote
     */
    @Column(name = "favourite_quote")
    private String favouriteQuote;

    /**
     * Country
     */
    @Column(name = "country")
    private String country;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    /**
     * Role enum for user types
     */
    public enum Role {
        MENTEE, MENTOR, ADVISEE, ADVISOR, ADMIN
    }
}
