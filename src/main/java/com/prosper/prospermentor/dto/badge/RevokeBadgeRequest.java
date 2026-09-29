package com.prosper.prospermentor.dto.badge;

import jakarta.validation.constraints.NotBlank;

public record RevokeBadgeRequest(@NotBlank String note) {
}
