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
        name = "review_cycles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "type"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCycle {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReviewType type = ReviewType.SESSION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_assignment_id")
    private CompanyProgramMentorAssignment mentorAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id")
    private CompanyProgram companyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private CompanyProgramParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_profile_id", nullable = false)
    private Profile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentee_profile_id", nullable = false)
    private Profile menteeProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewCycleStatus status = ReviewCycleStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revealed_at")
    private LocalDateTime revealedAt;

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
        if (openedAt == null) {
            openedAt = now;
        }
        if (expiresAt == null) {
            expiresAt = openedAt.plusHours(48);
        }
        if (status == null) {
            status = ReviewCycleStatus.OPEN;
        }
        if (type == null) {
            type = ReviewType.SESSION;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReviewType {
        SESSION,
        FIT
    }

    public enum ReviewCycleStatus {
        OPEN,
        PARTIALLY_SUBMITTED,
        REVEALED,
        EXPIRED_PARTIAL,
        EXPIRED_EMPTY,
        CANCELLED
    }
}
