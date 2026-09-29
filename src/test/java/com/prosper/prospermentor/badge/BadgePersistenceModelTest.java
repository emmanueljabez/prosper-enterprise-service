package com.prosper.prospermentor.badge;

import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeDto;
import com.prosper.prospermentor.entity.BadgeAffiliationRule;
import com.prosper.prospermentor.entity.BadgeAuditEvent;
import com.prosper.prospermentor.entity.BadgeType;
import com.prosper.prospermentor.entity.ProfileBadgeAward;
import com.prosper.prospermentor.repository.BadgeAffiliationRuleRepository;
import com.prosper.prospermentor.repository.BadgeAuditEventRepository;
import com.prosper.prospermentor.repository.BadgeTypeRepository;
import com.prosper.prospermentor.repository.ProfileBadgeAwardRepository;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BadgePersistenceModelTest {

    @Test
    void entities_shouldMapToBadgeTables() {
        assertThat(BadgeType.class.getAnnotation(Table.class).name()).isEqualTo("badge_types");
        assertThat(ProfileBadgeAward.class.getAnnotation(Table.class).name()).isEqualTo("profile_badge_awards");
        assertThat(BadgeAffiliationRule.class.getAnnotation(Table.class).name()).isEqualTo("badge_affiliation_rules");
        assertThat(BadgeAuditEvent.class.getAnnotation(Table.class).name()).isEqualTo("badge_audit_events");
    }

    @Test
    void repositories_shouldExposeQueriesNeededByBadgeService() throws Exception {
        assertThat(BadgeTypeRepository.class.getMethod("findBySlugAndStatus", String.class, String.class)).isNotNull();
        assertThat(BadgeTypeRepository.class.getMethod("findByStatusOrderByDisplayOrderAscNameAsc", String.class)).isNotNull();
        assertThat(ProfileBadgeAwardRepository.class.getMethod("findByProfile_IdAndStatusOrderByAwardedAtDesc", UUID.class, String.class)).isNotNull();
        assertThat(ProfileBadgeAwardRepository.class.getMethod("findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc", UUID.class, String.class, String.class)).isNotNull();
        assertThat(ProfileBadgeAwardRepository.class.getMethod("findByProfile_IdAndBadgeType_IdAndStatus", UUID.class, UUID.class, String.class)).isNotNull();
        assertThat(ProfileBadgeAwardRepository.class.getMethod("findByProfile_IdAndIsPrimaryTrueAndStatusAndVisibility", UUID.class, String.class, String.class)).isNotNull();
        assertThat(BadgeAffiliationRuleRepository.class.getMethod("findByCompany_IdAndProfileRoleAndStatus", UUID.class, String.class, String.class)).isNotNull();
        assertThat(BadgeAuditEventRepository.class.getMethod("findByProfileIdOrderByCreatedAtDesc", UUID.class)).isNotNull();
    }

    @Test
    void dtos_shouldUsePublicBadgeContractFields() {
        BadgeTypeDto type = new BadgeTypeDto(
                UUID.randomUUID(),
                "G4G Mentor",
                "g4g-mentor",
                "Girls for Girls mentor",
                "AFFILIATION",
                "AFFILIATION_RULE",
                "ACTIVE",
                "G4G Mentor",
                "#8f1f74",
                "#f7e8f3",
                "#6f1859",
                10
        );
        BadgeAwardDto award = new BadgeAwardDto(
                UUID.randomUUID(),
                type.id(),
                type.name(),
                type.label(),
                type.category(),
                type.colorHex(),
                type.backgroundHex(),
                type.textHex(),
                "AFFILIATION_RULE",
                "Automatic - company link confirmed",
                null,
                true,
                false,
                "SHOWN"
        );

        assertThat(award.name()).isEqualTo("G4G Mentor");
        assertThat(award.primary()).isTrue();
        assertThat(award.visibility()).isEqualTo("SHOWN");
    }
}
