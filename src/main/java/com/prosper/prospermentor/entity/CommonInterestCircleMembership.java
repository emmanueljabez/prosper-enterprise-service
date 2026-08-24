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
        name = "common_interest_circle_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"circle_id", "cohort_participant_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommonInterestCircleMembership {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circle_id", nullable = false)
    private CommonInterestCircle circle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_participant_id", nullable = false)
    private CompanyProgramCohortParticipant cohortParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement_source", nullable = false)
    private PlacementSource placementSource = PlacementSource.ADMIN_PLACED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status = MembershipStatus.PLACED;

    @Column(name = "placed_by_user_id")
    private UUID placedByUserId;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (placedAt == null) {
            placedAt = now;
        }
        if (placementSource == null) {
            placementSource = PlacementSource.ADMIN_PLACED;
        }
        if (status == null) {
            status = MembershipStatus.PLACED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PlacementSource {
        SUGGESTED,
        MENTEE_REQUESTED,
        ADMIN_PLACED,
        ADMIN_MOVED
    }

    public enum MembershipStatus {
        PENDING_REQUEST,
        PLACED,
        REMOVED,
        COMPLETED
    }
}
