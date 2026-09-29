package com.prosper.prospermentor.dto.badge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BadgeTypeUpsertRequest(
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        @NotBlank String category,
        @NotBlank String awardMethod,
        @NotBlank String label,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colorHex,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundHex,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String textHex,
        Integer displayOrder
) {
}
