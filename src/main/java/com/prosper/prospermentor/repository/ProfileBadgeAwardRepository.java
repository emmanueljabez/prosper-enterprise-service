package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ProfileBadgeAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileBadgeAwardRepository extends JpaRepository<ProfileBadgeAward, UUID> {
    List<ProfileBadgeAward> findByProfile_IdAndStatusOrderByAwardedAtDesc(UUID profileId, String status);

    List<ProfileBadgeAward> findByProfile_IdAndStatusAndVisibilityOrderByAwardedAtDesc(UUID profileId, String status, String visibility);

    Optional<ProfileBadgeAward> findByProfile_IdAndBadgeType_IdAndStatus(UUID profileId, UUID badgeTypeId, String status);

    Optional<ProfileBadgeAward> findByProfile_IdAndIsPrimaryTrueAndStatusAndVisibility(UUID profileId, String status, String visibility);
}
