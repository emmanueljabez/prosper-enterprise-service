package com.prosper.prospermentor.dto.badge;

import java.util.UUID;

public record BadgeTypeDto(
        UUID id,
        String name,
        String slug,
        String description,
        String category,
        String awardMethod,
        String status,
        String label,
        String colorHex,
        String backgroundHex,
        String textHex,
        Integer displayOrder
) {
}
