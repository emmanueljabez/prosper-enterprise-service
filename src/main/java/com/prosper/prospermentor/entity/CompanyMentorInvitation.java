package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "company_mentor_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMentorInvitation {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "title")
    private String title;

    @Column(name = "department")
    private String department;

    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false)
    private CompanyMentorPoolMembership.VisibilityMode defaultVisibility = CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE;

    @Column(name = "program_or_cohort_reference")
    private String programOrCohortReference;

    @Column(name = "invitation_token_hash")
    private String invitationTokenHash;

    @Column(name = "invitation_token_expires_at")
    private LocalDateTime invitationTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvitationStatus status = InvitationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_delivery_status", nullable = false)
    private DeliveryStatus emailDeliveryStatus = DeliveryStatus.NOT_ATTEMPTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "whatsapp_delivery_status", nullable = false)
    private DeliveryStatus whatsappDeliveryStatus = DeliveryStatus.NOT_ATTEMPTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_profile_id")
    private Profile acceptedProfile;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (defaultVisibility == null) {
            defaultVisibility = CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE;
        }
        if (status == null) {
            status = InvitationStatus.DRAFT;
        }
        if (emailDeliveryStatus == null) {
            emailDeliveryStatus = DeliveryStatus.NOT_ATTEMPTED;
        }
        if (whatsappDeliveryStatus == null) {
            whatsappDeliveryStatus = DeliveryStatus.NOT_ATTEMPTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum InvitationStatus {
        DRAFT,
        SENT,
        ACCEPTED,
        EXPIRED,
        CANCELLED,
        FAILED_DELIVERY
    }

    public enum DeliveryStatus {
        NOT_ATTEMPTED,
        SENT,
        FAILED,
        DELIVERED
    }
}
