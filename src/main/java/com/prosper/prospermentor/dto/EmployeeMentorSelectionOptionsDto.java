package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeMentorSelectionOptionsDto {
    private UUID participantId;
    private UUID companyProgramId;
    private String companyProgramName;
    private CompanyProgram.MatchingMode matchingMode;
    private MatchWorkspaceSummaryDto matchWorkspace;
    private List<CompanyProgramMentorCandidateDto> options;
    private Integer count;
}
