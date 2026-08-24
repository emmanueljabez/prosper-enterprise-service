package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "company_program_cohort_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_program_cohort_id", "profile_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohortParticipant {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_cohort_id", nullable = false)
    private CompanyProgramCohort cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_participant_id")
    private CompanyProgramParticipant companyProgramParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ParticipantSource source = ParticipantSource.MANUAL_ADD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CohortParticipantStatus status = CohortParticipantStatus.PENDING;

    @Column(name = "first_name_snapshot")
    private String firstNameSnapshot;

    @Column(name = "last_name_snapshot")
    private String lastNameSnapshot;

    @Column(name = "email_snapshot")
    private String emailSnapshot;

    @Column(name = "phone_snapshot")
    private String phoneSnapshot;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "region")
    private String region;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interest_tags", columnDefinition = "jsonb", nullable = false)
    private List<String> interestTags = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "self_join_request_id")
    private CompanyProgramCohortJoinRequest selfJoinRequest;

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_status", nullable = false)
    private DuplicateStatus duplicateStatus = DuplicateStatus.CLEAR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_candidate_profile_id")
    private Profile duplicateCandidateProfile;

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
        if (source == null) {
            source = ParticipantSource.MANUAL_ADD;
        }
        if (status == null) {
            status = CohortParticipantStatus.PENDING;
        }
        if (interestTags == null) {
            interestTags = new ArrayList<>();
        }
        if (duplicateStatus == null) {
            duplicateStatus = DuplicateStatus.CLEAR;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ParticipantSource {
        ROSTER_UPLOAD,
        MANUAL_ADD,
        SELF_JOIN,
        ADMIN_TRANSFER
    }

    public enum CohortParticipantStatus {
        PENDING,
        CONFIRMED,
        PLENARY_ATTENDED,
        PLACED_IN_CIRCLE,
        ELIGIBLE_FOR_MATCHING,
        MATCHED,
        ACTIVE,
        COMPLETED,
        WITHDRAWN,
        REJECTED
    }

    public enum DuplicateStatus {
        CLEAR,
        POSSIBLE_DUPLICATE,
        RESOLVED_EXISTING_PROFILE,
        RESOLVED_NEW_PROFILE
    }
}
