package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCatalogProgram;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCatalogStageRequest {
    private UUID programId;
    private String journeyStageName;
    private CompanyProgramCatalogProgram.StageType stageType;
}
