package com.prosper.prospermentor.dto.badge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BadgeAffiliationRuleRequest(
        @NotNull UUID companyId,
        @NotBlank String profileRole,
        @NotNull UUID badgeTypeId
) {
}
