package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "review_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"review_cycle_id", "reviewer_profile_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_profile_id", nullable = false)
    private Profile reviewerProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false)
    private ReviewRole reviewerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_profile_id", nullable = false)
    private Profile targetProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private ReviewRole targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ReviewChannel channel = ReviewChannel.WHATSAPP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewRequestStatus status = ReviewRequestStatus.PENDING;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "flow_token", length = 190)
    private String flowToken;

    @Column(name = "submission_token", length = 190)
    private String submissionToken;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @Column(name = "last_outbound_at")
    private LocalDateTime lastOutboundAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (channel == null) {
            channel = ReviewChannel.WHATSAPP;
        }
        if (status == null) {
            status = ReviewRequestStatus.PENDING;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReviewRole {
        MENTOR,
        MENTEE
    }

    public enum ReviewChannel {
        WHATSAPP
    }

    public enum ReviewRequestStatus {
        PENDING,
        SENT,
        DELIVERY_FAILED,
        SUBMITTED,
        EXPIRED,
        CANCELLED
    }
}
