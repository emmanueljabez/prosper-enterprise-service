package com.prosper.prospermentor.badge;

import com.prosper.prospermentor.dto.badge.BadgeAffiliationRuleDto;
import com.prosper.prospermentor.dto.badge.BadgeAffiliationRuleRequest;
import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeUpsertRequest;
import com.prosper.prospermentor.entity.BadgeAffiliationRule;
import com.prosper.prospermentor.entity.BadgeType;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ProfileBadgeAward;
import com.prosper.prospermentor.repository.BadgeAffiliationRuleRepository;
import com.prosper.prospermentor.repository.BadgeAuditEventRepository;
import com.prosper.prospermentor.repository.BadgeTypeRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProfileBadgeAwardRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.BadgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock
    BadgeTypeRepository badgeTypeRepository;

    @Mock
    ProfileBadgeAwardRepository awardRepository;

    @Mock
    BadgeAffiliationRuleRepository affiliationRuleRepository;

    @Mock
    BadgeAuditEventRepository auditEventRepository;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    CompanyRepository companyRepository;

    BadgeService service;

    UUID profileId;
    UUID badgeTypeId;
    UUID actorId;
    Profile profile;
    BadgeType affiliationBadge;

    @BeforeEach
    void setUp() {
        service = new BadgeService(
                badgeTypeRepository,
                awardRepository,
                affiliationRuleRepository,
                auditEventRepository,
                profileRepository,
                companyRepository
        );
        profileId = UUID.randomUUID();
        badgeTypeId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        profile = profile(profileId, "mentee");
        affiliationBadge = badgeType(badgeTypeId, "G4G Mentee", "AFFILIATION");
    }

    @Test
    void grantManualBadgeRequiresNote() {
        assertThatThrownBy(() -> service.grantManualBadge(profileId, badgeTypeId, " ", actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Manual badge grants require a note");
    }

    @Test
    void getBadgeTypesReturnsActiveTypesInDisplayOrder() {
        when(badgeTypeRepository.findByStatusOrderByDisplayOrderAscNameAsc("ACTIVE"))
                .thenReturn(List.of(affiliationBadge));

        List<BadgeTypeDto> types = service.getBadgeTypes();

        assertThat(types).hasSize(1);
        assertThat(types.get(0).slug()).isEqualTo("g4g-mentee");
        assertThat(types.get(0).colorHex()).isEqualTo("#8f1f74");
    }

    @Test
    void createBadgeTypePersistsDefaultsAndAudits() {
        BadgeTypeUpsertRequest request = new BadgeTypeUpsertRequest(
                "Founding Mentor",
                "founding-mentor",
                "Manually granted founding mentor badge",
                "ROLE_STATUS",
                "MANUAL",
                "Founding Mentor",
                null,
                null,
                null,
                null
        );
        when(badgeTypeRepository.existsBySlug("founding-mentor")).thenReturn(false);
        when(badgeTypeRepository.save(any(BadgeType.class))).thenAnswer(invocation -> {
            BadgeType saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        BadgeTypeDto dto = service.createBadgeType(request, actorId);

        assertThat(dto.slug()).isEqualTo("founding-mentor");
        assertThat(dto.colorHex()).isEqualTo("#8f1f74");
        assertThat(dto.backgroundHex()).isEqualTo("#f7e8f3");
        assertThat(dto.displayOrder()).isEqualTo(100);
        verify(auditEventRepository).save(any());
    }

    @Test
    void createAffiliationRulePersistsUppercaseRole() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company();
        company.setId(companyId);
        company.setName("Girls for Girls");
        BadgeAffiliationRuleRequest request = new BadgeAffiliationRuleRequest(companyId, "mentor", badgeTypeId);

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(badgeTypeRepository.findById(badgeTypeId)).thenReturn(Optional.of(affiliationBadge));
        when(affiliationRuleRepository.save(any(BadgeAffiliationRule.class))).thenAnswer(invocation -> {
            BadgeAffiliationRule saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        BadgeAffiliationRuleDto dto = service.createAffiliationRule(request, actorId);

        assertThat(dto.companyId()).isEqualTo(companyId);
        assertThat(dto.companyName()).isEqualTo("Girls for Girls");
        assertThat(dto.profileRole()).isEqualTo("MENTOR");
        assertThat(dto.badgeTypeId()).isEqualTo(badgeTypeId);
        verify(auditEventRepository).save(any());
    }

    @Test
    void publicBadgesExcludeHiddenAwards() {
        ProfileBadgeAward shownAward = award(UUID.randomUUID(), profile, affiliationBadge, "SHOWN", true);
        when(awardRepository.findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(profileId, "ACTIVE", "SHOWN"))
                .thenReturn(List.of(shownAward));

        List<BadgeAwardDto> badges = service.getPublicBadges(profileId);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).visibility()).isEqualTo("SHOWN");
        assertThat(badges.get(0).name()).isEqualTo("G4G Mentee");
    }

    @Test
    void hidingPrimaryBadgeRecalculatesPrimary() {
        UUID primaryAwardId = UUID.randomUUID();
        ProfileBadgeAward primaryAward = award(primaryAwardId, profile, affiliationBadge, "SHOWN", true);
        BadgeType recognitionBadge = badgeType(UUID.randomUUID(), "Top Contributor", "RECOGNITION");
        ProfileBadgeAward otherAward = award(UUID.randomUUID(), profile, recognitionBadge, "SHOWN", false);

        when(awardRepository.findById(primaryAwardId)).thenReturn(Optional.of(primaryAward));
        when(awardRepository.save(any(ProfileBadgeAward.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(awardRepository.findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(profileId, "ACTIVE", "SHOWN"))
                .thenReturn(List.of(otherAward));

        BadgeAwardDto updated = service.updateVisibility(profileId, primaryAwardId, "HIDDEN");

        assertThat(updated.visibility()).isEqualTo("HIDDEN");
        assertThat(updated.primary()).isFalse();
        assertThat(otherAward.getIsPrimary()).isTrue();
        verify(awardRepository).save(otherAward);
    }

    @Test
    void duplicateActiveAwardIsReturnedWithoutCreatingAnotherAward() {
        ProfileBadgeAward existingAward = award(UUID.randomUUID(), profile, affiliationBadge, "SHOWN", true);
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(badgeTypeRepository.findById(badgeTypeId)).thenReturn(Optional.of(affiliationBadge));
        when(awardRepository.findByProfile_IdAndBadgeType_IdAndStatus(profileId, badgeTypeId, "ACTIVE"))
                .thenReturn(Optional.of(existingAward));

        BadgeAwardDto dto = service.awardBadge(
                profileId,
                badgeTypeId,
                "AFFILIATION_RULE",
                UUID.randomUUID(),
                "Automatic - company link confirmed",
                "Automatic affiliation badge",
                actorId
        );

        assertThat(dto.id()).isEqualTo(existingAward.getId());
        verify(awardRepository, never()).save(any(ProfileBadgeAward.class));
    }

    @Test
    void affiliationAwardUsesRulesForNormalizedRole() {
        UUID companyId = UUID.randomUUID();
        BadgeAffiliationRule rule = new BadgeAffiliationRule();
        Company company = new Company();
        company.setId(companyId);
        rule.setCompany(company);
        rule.setBadgeType(affiliationBadge);
        rule.setProfileRole("MENTEE");
        rule.setStatus("ACTIVE");

        when(affiliationRuleRepository.findByCompany_IdAndProfileRoleAndStatus(companyId, "MENTEE", "ACTIVE"))
                .thenReturn(List.of(rule));
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(badgeTypeRepository.findById(badgeTypeId)).thenReturn(Optional.of(affiliationBadge));
        when(awardRepository.findByProfile_IdAndBadgeType_IdAndStatus(profileId, badgeTypeId, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(ProfileBadgeAward.class))).thenAnswer(invocation -> {
            ProfileBadgeAward saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(awardRepository.findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(profileId, "ACTIVE", "SHOWN"))
                .thenReturn(List.of());

        service.awardAffiliationBadgesForCompanyLink(
                companyId,
                profileId,
                "mentee",
                companyId,
                "Automatic - company link confirmed"
        );

        verify(affiliationRuleRepository).findByCompany_IdAndProfileRoleAndStatus(companyId, "MENTEE", "ACTIVE");
        verify(awardRepository).save(any(ProfileBadgeAward.class));
    }

    private Profile profile(UUID id, String role) {
        Profile created = new Profile();
        created.setId(id);
        created.setEmail("person@example.com");
        created.setUsername("person");
        created.setRole(role);
        return created;
    }

    private BadgeType badgeType(UUID id, String name, String category) {
        BadgeType type = new BadgeType();
        type.setId(id);
        type.setName(name);
        type.setSlug(name.toLowerCase().replace(" ", "-"));
        type.setLabel(name);
        type.setCategory(category);
        type.setAwardMethod("AFFILIATION".equals(category) ? "AFFILIATION_RULE" : "MANUAL");
        type.setStatus("ACTIVE");
        type.setColorHex("#8f1f74");
        type.setBackgroundHex("#f7e8f3");
        type.setTextHex("#6f1859");
        type.setDisplayOrder(10);
        return type;
    }

    private ProfileBadgeAward award(UUID id, Profile owner, BadgeType type, String visibility, boolean primary) {
        ProfileBadgeAward award = new ProfileBadgeAward();
        award.setId(id);
        award.setProfile(owner);
        award.setBadgeType(type);
        award.setStatus("ACTIVE");
        award.setSourceType("AFFILIATION_RULE");
        award.setSourceLabel("Automatic - company link confirmed");
        award.setVisibility(visibility);
        award.setIsPrimary(primary);
        award.setPrimarySelectedByUser(primary);
        award.setAwardedAt(LocalDateTime.now());
        return award;
    }
}
