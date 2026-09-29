package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.entity.BadgeAffiliationRule;
import com.prosper.prospermentor.entity.BadgeAuditEvent;
import com.prosper.prospermentor.entity.BadgeType;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ProfileBadgeAward;
import com.prosper.prospermentor.repository.BadgeAffiliationRuleRepository;
import com.prosper.prospermentor.repository.BadgeAuditEventRepository;
import com.prosper.prospermentor.repository.BadgeTypeRepository;
import com.prosper.prospermentor.repository.ProfileBadgeAwardRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BadgeService {

    private static final List<String> PRIMARY_CATEGORY_PRIORITY = List.of("AFFILIATION", "ROLE_STATUS", "RECOGNITION");

    private final BadgeTypeRepository badgeTypeRepository;
    private final ProfileBadgeAwardRepository awardRepository;
    private final BadgeAffiliationRuleRepository affiliationRuleRepository;
    private final BadgeAuditEventRepository auditEventRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public List<BadgeAwardDto> getMyBadges(UUID profileId) {
        return awardRepository.findByProfile_IdAndStatusOrderByAwardedAtDesc(profileId, "ACTIVE").stream()
                .map(this::toAwardDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BadgeAwardDto> getPublicBadges(UUID profileId) {
        return awardRepository.findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(profileId, "ACTIVE", "SHOWN").stream()
                .map(this::toAwardDto)
                .toList();
    }

    public BadgeAwardDto grantManualBadge(UUID profileId, UUID badgeTypeId, String note, UUID actorId) {
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("Manual badge grants require a note");
        }
        return awardBadge(profileId, badgeTypeId, "MANUAL", null, "Manually granted", note.trim(), actorId);
    }

    public BadgeAwardDto awardBadge(UUID profileId,
                                    UUID badgeTypeId,
                                    String sourceType,
                                    UUID sourceRefId,
                                    String sourceLabel,
                                    String note,
                                    UUID actorId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));
        BadgeType badgeType = badgeTypeRepository.findById(badgeTypeId)
                .orElseThrow(() -> new NoSuchElementException("Badge type not found"));
        if (!"ACTIVE".equals(badgeType.getStatus())) {
            throw new IllegalStateException("Badge type is not active");
        }

        Optional<ProfileBadgeAward> existing =
                awardRepository.findByProfile_IdAndBadgeType_IdAndStatus(profileId, badgeTypeId, "ACTIVE");
        if (existing.isPresent()) {
            return toAwardDto(existing.get());
        }

        ProfileBadgeAward award = new ProfileBadgeAward();
        award.setProfile(profile);
        award.setBadgeType(badgeType);
        award.setStatus("ACTIVE");
        award.setSourceType(sourceType);
        award.setSourceRefId(sourceRefId);
        award.setSourceLabel(sourceLabel);
        award.setAwardNote(note);
        award.setVisibility("SHOWN");
        award.setAwardedBy(actorId);
        award.setAwardedAt(LocalDateTime.now());

        ProfileBadgeAward saved = awardRepository.save(award);
        audit(saved, actorId, "BADGE_AWARDED", note);
        recalculatePrimary(profileId);
        return toAwardDto(saved);
    }

    public BadgeAwardDto revokeAward(UUID awardId, String note, UUID actorId) {
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("Badge revokes require a note");
        }
        ProfileBadgeAward award = awardRepository.findById(awardId)
                .orElseThrow(() -> new NoSuchElementException("Badge award not found"));
        award.setStatus("REVOKED");
        award.setRevokeNote(note.trim());
        award.setRevokedBy(actorId);
        award.setRevokedAt(LocalDateTime.now());
        award.setIsPrimary(false);
        award.setPrimarySelectedByUser(false);

        ProfileBadgeAward saved = awardRepository.save(award);
        audit(saved, actorId, "BADGE_REVOKED", note.trim());
        recalculatePrimary(saved.getProfile().getId());
        return toAwardDto(saved);
    }

    public BadgeAwardDto makePrimary(UUID profileId, UUID awardId) {
        ProfileBadgeAward award = ownedActiveAward(profileId, awardId);
        if ("HIDDEN".equals(award.getVisibility())) {
            throw new IllegalStateException("Hidden badges cannot be primary");
        }
        awardRepository.findByProfile_IdAndIsPrimaryTrueAndStatusAndVisibility(profileId, "ACTIVE", "SHOWN")
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    existing.setPrimarySelectedByUser(false);
                    awardRepository.save(existing);
                });
        award.setIsPrimary(true);
        award.setPrimarySelectedByUser(true);
        ProfileBadgeAward saved = awardRepository.save(award);
        audit(saved, profileId, "BADGE_PRIMARY_CHANGED", "User selected primary badge");
        return toAwardDto(saved);
    }

    public BadgeAwardDto updateVisibility(UUID profileId, UUID awardId, String visibility) {
        if (!List.of("SHOWN", "HIDDEN").contains(visibility)) {
            throw new IllegalArgumentException("Badge visibility must be SHOWN or HIDDEN");
        }
        ProfileBadgeAward award = ownedActiveAward(profileId, awardId);
        award.setVisibility(visibility);
        if ("HIDDEN".equals(visibility)) {
            award.setIsPrimary(false);
            award.setPrimarySelectedByUser(false);
        }

        ProfileBadgeAward saved = awardRepository.save(award);
        audit(saved, profileId, "BADGE_VISIBILITY_CHANGED", "Visibility changed to " + visibility);
        recalculatePrimary(profileId);
        return toAwardDto(saved);
    }

    public void awardAffiliationBadgesForCompanyLink(UUID companyId,
                                                     UUID profileId,
                                                     String rawRole,
                                                     UUID sourceRefId,
                                                     String sourceLabel) {
        String role = normalizeAffiliationRole(rawRole);
        List<BadgeAffiliationRule> rules =
                affiliationRuleRepository.findByCompany_IdAndProfileRoleAndStatus(companyId, role, "ACTIVE");
        for (BadgeAffiliationRule rule : rules) {
            awardBadge(
                    profileId,
                    rule.getBadgeType().getId(),
                    "AFFILIATION_RULE",
                    sourceRefId,
                    sourceLabel,
                    "Automatic affiliation badge",
                    null
            );
        }
    }

    void recalculatePrimary(UUID profileId) {
        List<ProfileBadgeAward> shownAwards =
                awardRepository.findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(profileId, "ACTIVE", "SHOWN");
        if (shownAwards.stream().anyMatch(award -> Boolean.TRUE.equals(award.getIsPrimary()))) {
            return;
        }
        shownAwards.stream()
                .min(Comparator
                        .comparingInt((ProfileBadgeAward award) -> primaryCategoryRank(award.getBadgeType().getCategory()))
                        .thenComparing(ProfileBadgeAward::getAwardedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .ifPresent(candidate -> {
                    candidate.setIsPrimary(true);
                    candidate.setPrimarySelectedByUser(false);
                    awardRepository.save(candidate);
                });
    }

    private ProfileBadgeAward ownedActiveAward(UUID profileId, UUID awardId) {
        ProfileBadgeAward award = awardRepository.findById(awardId)
                .orElseThrow(() -> new NoSuchElementException("Badge award not found"));
        if (!"ACTIVE".equals(award.getStatus())) {
            throw new IllegalStateException("Badge award is not active");
        }
        UUID ownerId = award.getProfile() != null ? award.getProfile().getId() : null;
        if (!Objects.equals(ownerId, profileId)) {
            throw new SecurityException("Badge award does not belong to this profile");
        }
        return award;
    }

    private int primaryCategoryRank(String category) {
        int index = PRIMARY_CATEGORY_PRIORITY.indexOf(category);
        return index >= 0 ? index : 99;
    }

    private String normalizeAffiliationRole(String rawRole) {
        if (rawRole == null) {
            return "EMPLOYEE";
        }
        String normalized = rawRole.trim().toUpperCase(Locale.ROOT);
        if ("MENTEE".equals(normalized)) {
            return "MENTEE";
        }
        if ("MENTOR".equals(normalized)) {
            return "MENTOR";
        }
        return "EMPLOYEE";
    }

    private BadgeAwardDto toAwardDto(ProfileBadgeAward award) {
        BadgeType badgeType = award.getBadgeType();
        return new BadgeAwardDto(
                award.getId(),
                badgeType.getId(),
                badgeType.getName(),
                badgeType.getLabel(),
                badgeType.getCategory(),
                badgeType.getColorHex(),
                badgeType.getBackgroundHex(),
                badgeType.getTextHex(),
                award.getSourceType(),
                award.getSourceLabel(),
                award.getAwardedAt(),
                Boolean.TRUE.equals(award.getIsPrimary()),
                Boolean.TRUE.equals(award.getPrimarySelectedByUser()),
                award.getVisibility()
        );
    }

    private void audit(ProfileBadgeAward award, UUID actorId, String eventType, String reason) {
        BadgeAuditEvent event = new BadgeAuditEvent();
        event.setBadgeType(award.getBadgeType());
        event.setAward(award);
        event.setProfileId(award.getProfile() != null ? award.getProfile().getId() : null);
        event.setActorId(actorId);
        event.setEventType(eventType);
        event.setReason(reason);
        auditEventRepository.save(event);
    }
}
