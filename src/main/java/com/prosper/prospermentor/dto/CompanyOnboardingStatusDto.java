package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyOnboardingStatusDto {
    private UUID companyId;
    private String companyName;
    private boolean completed;
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();
    private String industry;
    private String companySizeBand;
    private String country;
    private String timezone;
    private String mentorshipObjective;
    private String targetAudienceDescription;
    private String programDesignPreference;
    @Builder.Default
    private List<UUID> recommendedProgramIds = new ArrayList<>();
    private LocalDateTime completedAt;
}
