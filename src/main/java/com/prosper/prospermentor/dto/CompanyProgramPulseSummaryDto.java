package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramPulseSummaryDto {
    private UUID companyProgramId;
    private String companyProgramName;
    private long totalPulses;
    private long pendingPulses;
    private long completedPulses;
    private long expiredPulses;
    private long baselinePulses;
    private long programEndPulses;
    private Double completionRate;
}
