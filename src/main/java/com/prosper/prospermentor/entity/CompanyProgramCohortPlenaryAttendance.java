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
        name = "company_program_cohort_plenary_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_program_cohort_id", "cohort_participant_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohortPlenaryAttendance {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_cohort_id", nullable = false)
    private CompanyProgramCohort cohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_participant_id", nullable = false)
    private CompanyProgramCohortParticipant cohortParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_source", nullable = false)
    private AttendanceSource attendanceSource = AttendanceSource.ADMIN_OVERRIDE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status = AttendanceStatus.REGISTERED;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (attendanceSource == null) {
            attendanceSource = AttendanceSource.ADMIN_OVERRIDE;
        }
        if (status == null) {
            status = AttendanceStatus.REGISTERED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AttendanceSource {
        SUMMIT_EVENT,
        IMPORT,
        ADMIN_OVERRIDE
    }

    public enum AttendanceStatus {
        REGISTERED,
        ATTENDED,
        ABSENT,
        EXCUSED
    }
}
