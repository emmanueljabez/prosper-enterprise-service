package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPlenaryAttendanceRequest {
    private CompanyProgramCohortPlenaryAttendance.AttendanceStatus status;
    private CompanyProgramCohortPlenaryAttendance.AttendanceSource attendanceSource;
}
