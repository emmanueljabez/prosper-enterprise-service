package com.prosper.prospermentor.dto.badge;

import jakarta.validation.constraints.NotBlank;

public record BadgeVisibilityRequest(@NotBlank String visibility) {
}
