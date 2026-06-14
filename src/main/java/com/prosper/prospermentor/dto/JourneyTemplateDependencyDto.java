package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.JourneyStepDependency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateDependencyDto {
    private UUID id;
    private UUID fromStepId;
    private String fromStepKey;
    private String fromStepTitle;
    private UUID toStepId;
    private String toStepKey;
    private String toStepTitle;
    private JourneyStepDependency.DependencyType dependencyType;
}
