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
public class CohortGateStatusDto {
    private UUID cohortId;
    private UUID cohortParticipantId;
    private UUID companyProgramParticipantId;
    private boolean confirmed;
    private boolean plenaryAttended;
    private boolean placedInCircle;
    private boolean circlesFinalized;
    private boolean eligibleForMatching;
    private String blockedReason;
}
