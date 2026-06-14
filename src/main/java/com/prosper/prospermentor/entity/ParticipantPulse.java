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
@Table(name = "participant_pulses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantPulse {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private CompanyProgramParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "pulse_type", nullable = false, length = 32)
    private PulseType pulseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PulseStatus status = PulseStatus.PENDING;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "goal_clarity_score")
    private Integer goalClarityScore;

    @Column(name = "free_text_feedback", columnDefinition = "TEXT")
    private String freeTextFeedback;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = PulseStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PulseType {
        BASELINE,
        MIDPOINT,
        PROGRAM_END,
        D30,
        D60,
        D90
    }

    public enum PulseStatus {
        PENDING,
        COMPLETED,
        EXPIRED
    }
}
