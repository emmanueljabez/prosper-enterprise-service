package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request model for linking a profile to a company
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkProfileToCompanyRequest {

    @NotNull(message = "Profile ID is required")
    private UUID profileId;
}
