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
@Table(name = "common_interest_circles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommonInterestCircle {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_cohort_id", nullable = false)
    private CompanyProgramCohort cohort;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "theme")
    private String theme;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interest_tags", columnDefinition = "jsonb", nullable = false)
    private List<String> interestTags = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilitator_profile_id")
    private Profile facilitatorProfile;

    @Column(name = "min_size", nullable = false)
    private Integer minSize = 5;

    @Column(name = "max_size", nullable = false)
    private Integer maxSize = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CircleStatus status = CircleStatus.DRAFT;

    @Column(name = "next_session_at")
    private LocalDateTime nextSessionAt;

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
        if (interestTags == null) {
            interestTags = new ArrayList<>();
        }
        if (minSize == null) {
            minSize = 5;
        }
        if (maxSize == null) {
            maxSize = 10;
        }
        if (status == null) {
            status = CircleStatus.DRAFT;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CircleStatus {
        DRAFT,
        FORMING,
        FINALIZED,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
