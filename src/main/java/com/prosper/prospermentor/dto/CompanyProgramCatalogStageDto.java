package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCatalogProgram;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCatalogStageDto {
    private UUID id;
    private UUID programId;
    private String programName;
    private String programDescription;
    private Integer journeyOrder;
    private String journeyStageName;
    private CompanyProgramCatalogProgram.StageType stageType;
}
