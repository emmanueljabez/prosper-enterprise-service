package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_signup_intents")
@Getter
@Setter
@NoArgsConstructor
public class CompanySignupIntent {

    public enum SignupIntentStatus {
        PENDING,
        COMPLETED,
        EXPIRED,
        CANCELLED
    }

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "token", nullable = false, unique = true, length = 120)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "company_registration_token", nullable = false, length = 120)
    private String companyRegistrationToken;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column(name = "admin_first_name", nullable = false)
    private String adminFirstName;

    @Column(name = "admin_last_name", nullable = false)
    private String adminLastName;

    @Column(name = "admin_phone_number", nullable = false)
    private String adminPhoneNumber;

    @Column(name = "target_plan_id", columnDefinition = "uuid")
    private UUID targetPlanId;

    @Column(name = "target_session_count")
    private Integer targetSessionCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SignupIntentStatus status;

    @Column(name = "linked_user_id", columnDefinition = "uuid")
    private UUID linkedUserId;

    @Column(name = "linked_profile_id", columnDefinition = "uuid")
    private UUID linkedProfileId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = SignupIntentStatus.PENDING;
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(7);
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
