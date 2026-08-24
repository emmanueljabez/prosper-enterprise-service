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
@Table(name = "company_program_cohorts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohort {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id", nullable = false)
    private CompanyProgram companyProgram;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "region")
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CohortStatus status = CohortStatus.DRAFT;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "self_join_enabled", nullable = false)
    private Boolean selfJoinEnabled = false;

    @Column(name = "self_join_code_hash")
    private String selfJoinCodeHash;

    @Column(name = "self_join_expires_at")
    private LocalDateTime selfJoinExpiresAt;

    @Column(name = "self_join_capacity")
    private Integer selfJoinCapacity;

    @Column(name = "circle_min_size", nullable = false)
    private Integer circleMinSize = 5;

    @Column(name = "circle_max_size", nullable = false)
    private Integer circleMaxSize = 10;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interest_tag_set", columnDefinition = "jsonb", nullable = false)
    private List<String> interestTagSet = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "plenary_event_type")
    private PlenaryEventType plenaryEventType;

    @Column(name = "plenary_event_id")
    private String plenaryEventId;

    @Column(name = "matching_starts_after_circles_finalized", nullable = false)
    private Boolean matchingStartsAfterCirclesFinalized = true;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

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
        if (status == null) {
            status = CohortStatus.DRAFT;
        }
        if (selfJoinEnabled == null) {
            selfJoinEnabled = false;
        }
        if (circleMinSize == null) {
            circleMinSize = 5;
        }
        if (circleMaxSize == null) {
            circleMaxSize = 10;
        }
        if (interestTagSet == null) {
            interestTagSet = new ArrayList<>();
        }
        if (matchingStartsAfterCirclesFinalized == null) {
            matchingStartsAfterCirclesFinalized = true;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CohortStatus {
        DRAFT,
        INTAKE_OPEN,
        INTAKE_CLOSED,
        PLENARY_SCHEDULED,
        CIRCLES_FORMING,
        CIRCLES_FINALIZED,
        MATCHING,
        ACTIVE,
        COMPLETED,
        CANCELLED,
        ARCHIVED
    }

    public enum PlenaryEventType {
        SUMMIT_EVENT,
        EXTERNAL_EVENT,
        MANUAL_EVENT
    }
}
