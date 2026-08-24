package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchWorkspaceSummaryDto {
    private CompanyProgramMatchWorkspace.MatchStatus status;
    private LocalDateTime selectionDeadlineAt;
    private LocalDateTime shortlistGeneratedAt;
    private LocalDateTime resolvedAt;
    private CompanyProgramMatchWorkspace.ResolverType resolvedBy;
    private Integer shortlistCount;
    private Boolean canEmployeeSelect;
    private Boolean selectionWindowExpired;
    private String blockedReason;
}
