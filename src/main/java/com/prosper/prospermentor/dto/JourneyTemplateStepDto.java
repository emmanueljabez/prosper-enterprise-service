package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.JourneyStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateStepDto {
    private UUID id;
    private String stepKey;
    private Integer defaultSequence;
    private String title;
    private String description;
    private JourneyStep.StepType stepType;
    private Boolean required;
    private Integer defaultDueOffsetDays;
    private String stepConfigJson;
}
