package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

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
     * Username - required field, unique identifier
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

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
     * Reference to mentee profile
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    /**
     * Location/address
     */
    @Column(name = "location")
    private String location;

    /**
     * User role enum - stored as USER-DEFINED type in PostgreSQL
     * Converted to lowercase for PostgreSQL custom type compatibility
     */
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    @ColumnTransformer(write = "?::user_role")
    @Convert(converter = com.prosper.prospermentor.entity.converter.UserRoleConverter.class)
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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "expertise", columnDefinition = "text[]")
    private String[] expertise;

    /**
     * User interests - stored as array in PostgreSQL
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "interests", columnDefinition = "text[]")
    private String[] interests;

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

    /**
     * One-to-one relationship with MentorProfile
     * Only populated if the user's role is MENTOR
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", insertable = false, updatable = false)
    private MentorProfile mentorProfile;

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

    public List<String> getExpertise() {
        return TextArrayMapping.toList(expertise);
    }

    public void setExpertise(List<String> expertise) {
        this.expertise = TextArrayMapping.fromList(expertise);
    }

    public void setExpertise(String[] expertise) {
        this.expertise = expertise;
    }

    public List<String> getInterests() {
        return TextArrayMapping.toList(interests);
    }

    public void setInterests(List<String> interests) {
        this.interests = TextArrayMapping.fromList(interests);
    }

    public void setInterests(String[] interests) {
        this.interests = interests;
    }

    /**
     * Role enum for user types
     */
    public enum Role {
        MENTEE, MENTOR, ADVISEE, ADVISOR, ADMIN, COMPANY, COMPANY_ADMIN
    }
}
