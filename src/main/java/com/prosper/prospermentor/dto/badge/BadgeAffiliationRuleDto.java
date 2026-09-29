package com.prosper.prospermentor.dto.badge;

import java.util.UUID;

public record BadgeAffiliationRuleDto(
        UUID id,
        UUID companyId,
        String companyName,
        String profileRole,
        UUID badgeTypeId,
        String badgeTypeName,
        String status
) {
}
