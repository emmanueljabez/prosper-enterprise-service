package com.prosper.prospermentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertJourneyTemplateRequest {

    @NotBlank(message = "name is required")
    @Size(max = 160, message = "name must be 160 characters or fewer")
    private String name;

    @Size(max = 64, message = "programType must be 64 characters or fewer")
    private String programType;

    private String description;

    @Size(max = 1024, message = "coverImageUrl must be 1024 characters or fewer")
    private String coverImageUrl;

    private Integer defaultDurationWeeks;

    private Boolean active;

    @Valid
    @NotEmpty(message = "At least one step is required")
    private List<JourneyTemplateStepRequest> steps = new ArrayList<>();

    @Valid
    private List<JourneyTemplateDependencyRequest> dependencies = new ArrayList<>();
}
