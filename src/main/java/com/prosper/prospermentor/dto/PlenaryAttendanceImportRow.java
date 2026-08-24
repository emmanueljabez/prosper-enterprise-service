package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlenaryAttendanceImportRow {
    private UUID cohortParticipantId;
    private String email;
    private CompanyProgramCohortPlenaryAttendance.AttendanceStatus status;
    private CompanyProgramCohortPlenaryAttendance.AttendanceSource attendanceSource;
}
