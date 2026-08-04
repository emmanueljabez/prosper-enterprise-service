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
@Table(name = "journey_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyInstance {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private CompanyProgramParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_template_id", nullable = false)
    private JourneyTemplate journeyTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JourneyStatus status = JourneyStatus.NOT_STARTED;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

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
        if (startedAt == null) {
            startedAt = now;
        }
        if (status == null) {
            status = JourneyStatus.NOT_STARTED;
        }
        if (progressPercent == null) {
            progressPercent = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JourneyStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        CANCELLED
    }
}
