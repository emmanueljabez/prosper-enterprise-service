package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCohortDuplicateRequest {
    @NotNull
    private UUID profileId;
    @NotNull
    private CompanyProgramCohortParticipant.DuplicateStatus duplicateStatus;
}
