package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateDto {
    private UUID id;
    private String name;
    private String programType;
    private String description;
    private String coverImageUrl;
    private Integer defaultDurationWeeks;
    private Integer templateVersion;
    private Boolean active;
    private Integer stepCount;
    private Integer dependencyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<JourneyTemplateStepDto> steps;
    private List<JourneyTemplateDependencyDto> dependencies;
}
