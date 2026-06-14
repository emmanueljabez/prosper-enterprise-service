package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.JourneyStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateStepRequest {

    @NotBlank(message = "stepKey is required")
    @Size(max = 96, message = "stepKey must be 96 characters or fewer")
    private String stepKey;

    @NotBlank(message = "title is required")
    @Size(max = 160, message = "title must be 160 characters or fewer")
    private String title;

    private String description;

    @NotNull(message = "stepType is required")
    private JourneyStep.StepType stepType;

    private Boolean required;

    private Integer defaultDueOffsetDays;

    private String stepConfigJson;
}
