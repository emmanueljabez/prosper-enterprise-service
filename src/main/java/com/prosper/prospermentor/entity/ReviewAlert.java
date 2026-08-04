package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAlert {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "alert_key", nullable = false, unique = true, length = 190)
    private String alertKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_request_id", nullable = false)
    private ReviewRequest reviewRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id")
    private CompanyProgram companyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private CompanyProgramParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_assignment_id")
    private CompanyProgramMentorAssignment mentorAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private ReviewAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity = Severity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewAlertStatus status = ReviewAlertStatus.OPEN;

    @Column(name = "question_code", length = 80)
    private String questionCode;

    @Column(name = "score_value", precision = 4, scale = 2)
    private BigDecimal scoreValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

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
            status = ReviewAlertStatus.OPEN;
        }
        if (severity == null) {
            severity = Severity.MEDIUM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReviewAlertType {
        LOW_MENTOR_SCORE,
        LOW_MENTEE_SCORE,
        LOW_FIT_SCORE,
        DO_NOT_CONTINUE
    }

    public enum Severity {
        MEDIUM,
        HIGH
    }

    public enum ReviewAlertStatus {
        OPEN,
        ACKNOWLEDGED,
        RESOLVED
    }
}
