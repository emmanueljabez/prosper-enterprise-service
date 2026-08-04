package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "journey_instance_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyInstanceStep {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_instance_id", nullable = false)
    private JourneyInstance journeyInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_step_id", nullable = false)
    private JourneyStep journeyStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status = StepStatus.PENDING;

    @Column(name = "due_at")
    private ZonedDateTime dueAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "skipped_reason", columnDefinition = "TEXT")
    private String skippedReason;

    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

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
            status = StepStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StepStatus {
        PENDING,
        READY,
        COMPLETED,
        SKIPPED,
        BLOCKED
    }
}
