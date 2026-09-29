package com.prosper.prospermentor.dto.badge;

import java.time.LocalDateTime;
import java.util.UUID;

public record BadgeAwardDto(
        UUID id,
        UUID badgeTypeId,
        String name,
        String label,
        String category,
        String colorHex,
        String backgroundHex,
        String textHex,
        String sourceType,
        String sourceLabel,
        LocalDateTime awardedAt,
        boolean primary,
        boolean primarySelectedByUser,
        String visibility
) {
}
