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
@Table(name = "company_program_cohort_join_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohortJoinRequest {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_cohort_id", nullable = false)
    private CompanyProgramCohort cohort;

    @Column(name = "submitted_email", nullable = false)
    private String submittedEmail;

    @Column(name = "submitted_phone")
    private String submittedPhone;

    @Column(name = "submitted_first_name")
    private String submittedFirstName;

    @Column(name = "submitted_last_name")
    private String submittedLastName;

    @Column(name = "submitted_chapter")
    private String submittedChapter;

    @Column(name = "submitted_region")
    private String submittedRegion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_interest_tags", columnDefinition = "jsonb", nullable = false)
    private List<String> submittedInterestTags = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_profile_id")
    private Profile matchedProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (submittedInterestTags == null) {
            submittedInterestTags = new ArrayList<>();
        }
        if (status == null) {
            status = JoinRequestStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JoinRequestStatus {
        PENDING,
        CONFIRMED,
        REJECTED,
        DUPLICATE_REVIEW,
        EXPIRED
    }
}
