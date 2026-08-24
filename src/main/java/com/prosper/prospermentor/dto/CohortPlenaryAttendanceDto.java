package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohortPlenaryAttendanceDto {
    private UUID id;
    private UUID cohortId;
    private UUID cohortParticipantId;
    private UUID profileId;
    private String profileName;
    private String profileEmail;
    private CompanyProgramCohortPlenaryAttendance.AttendanceSource attendanceSource;
    private CompanyProgramCohortPlenaryAttendance.AttendanceStatus status;
    private LocalDateTime attendedAt;
    private UUID recordedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
