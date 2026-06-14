package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.JourneyStepDependency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplateDependencyRequest {

    @NotBlank(message = "fromStepKey is required")
    @Size(max = 96, message = "fromStepKey must be 96 characters or fewer")
    private String fromStepKey;

    @NotBlank(message = "toStepKey is required")
    @Size(max = 96, message = "toStepKey must be 96 characters or fewer")
    private String toStepKey;

    @NotNull(message = "dependencyType is required")
    private JourneyStepDependency.DependencyType dependencyType;
}
