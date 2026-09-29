package com.prosper.prospermentor.dto.badge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualBadgeGrantRequest(
        @NotNull UUID badgeTypeId,
        @NotBlank String note
) {
}
