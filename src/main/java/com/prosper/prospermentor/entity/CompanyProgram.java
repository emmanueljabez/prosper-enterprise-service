package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "company_programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgram {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_template_id")
    private JourneyTemplate journeyTemplate;

    @OneToMany(mappedBy = "companyProgram", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("journeyOrder ASC")
    private List<CompanyProgramCatalogProgram> catalogPrograms = new ArrayList<>();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "target_audience_description", columnDefinition = "TEXT")
    private String targetAudienceDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyProgramStatus status = CompanyProgramStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_mode", nullable = false)
    private MatchingMode matchingMode = MatchingMode.ADMIN_ASSIGN;

    @Column(name = "employee_selection_window_hours", nullable = false)
    private Integer employeeSelectionWindowHours = 48;

    @Column(name = "employee_selection_shortlist_size", nullable = false)
    private Integer employeeSelectionShortlistSize = 5;

    @Column(name = "requires_mentor_for_session_steps", nullable = false)
    private Boolean requiresMentorForSessionSteps = true;

    @Column(name = "visibility_policy_code")
    private String visibilityPolicyCode;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = CompanyProgramStatus.DRAFT;
        }
        if (matchingMode == null) {
            matchingMode = MatchingMode.ADMIN_ASSIGN;
        }
        if (employeeSelectionWindowHours == null) {
            employeeSelectionWindowHours = 48;
        }
        if (employeeSelectionShortlistSize == null) {
            employeeSelectionShortlistSize = 5;
        }
        if (requiresMentorForSessionSteps == null) {
            requiresMentorForSessionSteps = true;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CompanyProgramStatus {
        DRAFT,
        LIVE,
        PAUSED,
        COMPLETED,
        CANCELLED,
        ARCHIVED
    }

    public enum MatchingMode {
        ADMIN_ASSIGN,
        EMPLOYEE_SELECT,
        SYSTEM_ASSIGN
    }
}
