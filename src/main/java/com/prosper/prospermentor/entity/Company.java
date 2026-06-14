package com.prosper.prospermentor.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company entity representing organizations in the system
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank(message = "Company name is required")
    @Column(name = "name", nullable = false)
    private String name;

    @Email(message = "Invalid email address")
    @NotBlank(message = "Email address is required")
    @Column(name = "email_address", nullable = false, unique = true)
    private String emailAddress;

    @NotBlank(message = "Phone number is required")
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website")
    private String website;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "industry")
    private String industry;

    @Column(name = "company_size_band")
    private String companySizeBand;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "mentorship_objective", columnDefinition = "TEXT")
    private String mentorshipObjective;

    @Column(name = "target_audience_description", columnDefinition = "TEXT")
    private String targetAudienceDescription;

    @Column(name = "program_design_preference")
    private String programDesignPreference;

    @Column(name = "onboarding_completed")
    private Boolean onboardingCompleted = false;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "registration_token")
    private String registrationToken;

    @Column(name = "registration_token_expiry")
    private LocalDateTime registrationTokenExpiry;

    @Column(name = "registration_completed")
    private Boolean registrationCompleted = false;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_recommended_programs",
            joinColumns = @JoinColumn(name = "company_id"),
            inverseJoinColumns = @JoinColumn(name = "program_id")
    )
    @OrderBy("orderId ASC")
    private List<Program> recommendedPrograms = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
