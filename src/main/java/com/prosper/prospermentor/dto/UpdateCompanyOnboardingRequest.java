package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateCompanyOnboardingRequest {
    @NotBlank(message = "Industry is required")
    private String industry;

    @NotBlank(message = "Company size is required")
    private String companySizeBand;

    @NotBlank(message = "Country is required")
    private String country;

    @NotBlank(message = "Timezone is required")
    private String timezone;

    @Size(max = 2000, message = "Mentorship objective must be 2000 characters or fewer")
    private String mentorshipObjective;

    @Size(max = 2000, message = "Target employee audience must be 2000 characters or fewer")
    private String targetAudienceDescription;

    @Pattern(
            regexp = "PROSPER_PROGRAMS|CUSTOM_MENTOR_POOL|BOTH",
            message = "Program design preference must be PROSPER_PROGRAMS, CUSTOM_MENTOR_POOL, or BOTH"
    )
    private String programDesignPreference;

    private List<UUID> recommendedProgramIds;
}
