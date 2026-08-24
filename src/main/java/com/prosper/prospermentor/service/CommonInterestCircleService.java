package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CircleSuggestionDto;
import com.prosper.prospermentor.dto.CircleSuggestionResultDto;
import com.prosper.prospermentor.dto.CommonInterestCircleDto;
import com.prosper.prospermentor.dto.CommonInterestCircleMemberDto;
import com.prosper.prospermentor.dto.CreateCommonInterestCircleRequest;
import com.prosper.prospermentor.dto.UpdateCommonInterestCircleRequest;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommonInterestCircleService {

    private final CompanyProgramCohortRepository cohortRepository;
    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CommonInterestCircleRepository circleRepository;
    private final CommonInterestCircleMembershipRepository membershipRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public List<CommonInterestCircleDto> getCircles(UUID cohortId) {
        if (!cohortRepository.existsById(cohortId)) {
            throw new NoSuchElementException("Company program cohort not found");
        }
        List<CommonInterestCircle> circles = circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId);
        List<CommonInterestCircleMembership> memberships = membershipRepository.findByCircle_Cohort_Id(cohortId);
        return circles.stream()
                .map(circle -> toDto(circle, memberships))
                .toList();
    }

    public CommonInterestCircleDto createCircle(UUID cohortId, CreateCommonInterestCircleRequest request) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        int minSize = request.getMinSize() != null ? request.getMinSize() : resolvedMinSize(cohort);
        int maxSize = request.getMaxSize() != null ? request.getMaxSize() : resolvedMaxSize(cohort);
        validateCircleSizes(minSize, maxSize);

        CommonInterestCircle circle = new CommonInterestCircle();
        circle.setCohort(cohort);
        circle.setName(request.getName());
        circle.setTheme(request.getTheme());
        circle.setInterestTags(normalizeTags(request.getInterestTags()));
        circle.setMinSize(minSize);
        circle.setMaxSize(maxSize);
        circle.setNextSessionAt(request.getNextSessionAt());
        if (request.getFacilitatorProfileId() != null) {
            circle.setFacilitatorProfile(profileRepository.findById(request.getFacilitatorProfileId())
                    .orElseThrow(() -> new NoSuchElementException("Facilitator profile not found")));
        }

        CommonInterestCircle saved = circleRepository.save(circle);
        log.info("Created common interest circle {} for cohort {}", saved.getId(), cohortId);
        return toDto(saved, List.of());
    }

    public CommonInterestCircleDto updateCircle(UUID circleId, UpdateCommonInterestCircleRequest request) {
        CommonInterestCircle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new NoSuchElementException("Common interest circle not found"));
        int nextMin = request.getMinSize() != null ? request.getMinSize() : resolvedMinSize(circle);
        int nextMax = request.getMaxSize() != null ? request.getMaxSize() : resolvedMaxSize(circle);
        validateCircleSizes(nextMin, nextMax);

        if (request.getName() != null) {
            circle.setName(request.getName());
        }
        if (request.getTheme() != null) {
            circle.setTheme(request.getTheme());
        }
        if (request.getInterestTags() != null) {
            circle.setInterestTags(normalizeTags(request.getInterestTags()));
        }
        if (request.getFacilitatorProfileId() != null) {
            circle.setFacilitatorProfile(profileRepository.findById(request.getFacilitatorProfileId())
                    .orElseThrow(() -> new NoSuchElementException("Facilitator profile not found")));
        }
        if (request.getMinSize() != null) {
            circle.setMinSize(request.getMinSize());
        }
        if (request.getMaxSize() != null) {
            circle.setMaxSize(request.getMaxSize());
        }
        if (request.getStatus() != null) {
            circle.setStatus(request.getStatus());
        }
        if (request.getNextSessionAt() != null) {
            circle.setNextSessionAt(request.getNextSessionAt());
        }

        CommonInterestCircle saved = circleRepository.save(circle);
        return toDto(saved, membershipsForCircle(saved));
    }

    @Transactional(readOnly = true)
    public CircleSuggestionResultDto suggestCircles(UUID cohortId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        int minSize = resolvedMinSize(cohort);
        Map<String, List<CompanyProgramCohortParticipant>> byPrimaryTag = new LinkedHashMap<>();
        List<CompanyProgramCohortParticipant> unplaced = new ArrayList<>();

        for (CompanyProgramCohortParticipant participant : cohortParticipantRepository.findByCohort_Id(cohortId)) {
            if (!isSuggestionEligible(participant) || hasPlacedMembership(participant)) {
                continue;
            }
            String tag = primaryTag(participant);
            if (tag == null) {
                unplaced.add(participant);
                continue;
            }
            byPrimaryTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(participant);
        }

        List<CircleSuggestionDto> suggested = new ArrayList<>();
        byPrimaryTag.forEach((tag, participants) -> {
            if (participants.size() >= minSize) {
                suggested.add(CircleSuggestionDto.builder()
                        .name(tag + " Circle")
                        .theme(tag)
                        .interestTags(List.of(tag))
                        .cohortParticipantIds(participants.stream().map(CompanyProgramCohortParticipant::getId).toList())
                        .participantNames(participants.stream().map(this::participantName).toList())
                        .participantCount(participants.size())
                        .build());
            } else {
                unplaced.addAll(participants);
            }
        });

        return CircleSuggestionResultDto.builder()
                .cohortId(cohortId)
                .suggestedCircles(suggested)
                .unplacedParticipantIds(unplaced.stream().map(CompanyProgramCohortParticipant::getId).toList())
                .unplacedParticipantNames(unplaced.stream().map(this::participantName).toList())
                .build();
    }

    public CommonInterestCircleDto placeParticipant(UUID circleId,
                                                    UUID cohortParticipantId,
                                                    CommonInterestCircleMembership.PlacementSource source,
                                                    UUID placedByUserId) {
        CommonInterestCircle circle = circleRepository.findByIdForUpdate(circleId)
                .orElseThrow(() -> new NoSuchElementException("Common interest circle not found"));
        long placedCount = membershipRepository.countByCircle_IdAndStatus(
                circleId,
                CommonInterestCircleMembership.MembershipStatus.PLACED
        );
        if (placedCount >= resolvedMaxSize(circle)) {
            throw new IllegalStateException("Circle is full");
        }

        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        ensureParticipantBelongsToCircleCohort(circle, participant);
        Optional<CommonInterestCircleMembership> existingPlaced = membershipRepository
                .findByCohortParticipant_IdAndStatus(cohortParticipantId, CommonInterestCircleMembership.MembershipStatus.PLACED);
        if (existingPlaced.isPresent()) {
            throw new IllegalStateException("Participant is already placed in a circle");
        }

        CommonInterestCircleMembership membership = new CommonInterestCircleMembership();
        membership.setCircle(circle);
        membership.setCohortParticipant(participant);
        membership.setPlacementSource(source != null ? source : CommonInterestCircleMembership.PlacementSource.ADMIN_PLACED);
        membership.setStatus(CommonInterestCircleMembership.MembershipStatus.PLACED);
        membership.setPlacedByUserId(placedByUserId);
        membership.setPlacedAt(LocalDateTime.now());
        CommonInterestCircleMembership savedMembership = membershipRepository.save(membership);

        updateParticipantStatusAfterPlacement(participant, circle.getCohort());
        return toDto(circle, List.of(savedMembership));
    }

    public CommonInterestCircleDto removeMembership(UUID membershipId, UUID removedByUserId) {
        CommonInterestCircleMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Common interest circle membership not found"));
        membership.setStatus(CommonInterestCircleMembership.MembershipStatus.REMOVED);
        membership.setPlacedByUserId(removedByUserId);
        CommonInterestCircleMembership saved = membershipRepository.save(membership);

        CompanyProgramCohortParticipant participant = saved.getCohortParticipant();
        if (participant != null && isPlacedStatus(participant)) {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
            cohortParticipantRepository.save(participant);
        }
        return toDto(saved.getCircle(), membershipsForCircle(saved.getCircle()));
    }

    public CommonInterestCircleDto moveMembership(UUID membershipId, UUID targetCircleId, UUID movedByUserId) {
        CommonInterestCircleMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Common interest circle membership not found"));
        CommonInterestCircle targetCircle = circleRepository.findByIdForUpdate(targetCircleId)
                .orElseThrow(() -> new NoSuchElementException("Target common interest circle not found"));
        long placedCount = membershipRepository.countByCircle_IdAndStatus(
                targetCircleId,
                CommonInterestCircleMembership.MembershipStatus.PLACED
        );
        if (placedCount >= resolvedMaxSize(targetCircle)) {
            throw new IllegalStateException("Circle is full");
        }
        ensureParticipantBelongsToCircleCohort(targetCircle, membership.getCohortParticipant());

        membership.setCircle(targetCircle);
        membership.setPlacementSource(CommonInterestCircleMembership.PlacementSource.ADMIN_MOVED);
        membership.setStatus(CommonInterestCircleMembership.MembershipStatus.PLACED);
        membership.setPlacedByUserId(movedByUserId);
        membership.setPlacedAt(LocalDateTime.now());
        CommonInterestCircleMembership saved = membershipRepository.save(membership);
        updateParticipantStatusAfterPlacement(saved.getCohortParticipant(), targetCircle.getCohort());
        return toDto(targetCircle, membershipsForCircle(targetCircle));
    }

    public List<CommonInterestCircleDto> finalizeCircles(UUID cohortId, UUID finalizedByUserId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        List<CommonInterestCircle> circles = circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId);
        if (circles.isEmpty()) {
            throw new IllegalStateException("At least one circle is required before finalization");
        }
        List<CommonInterestCircleMembership> memberships = membershipRepository.findByCircle_Cohort_Id(cohortId);

        for (CommonInterestCircle circle : circles) {
            validateCircleReadyForFinalization(circle);
            circle.setStatus(CommonInterestCircle.CircleStatus.FINALIZED);
            circleRepository.save(circle);
        }

        memberships.stream()
                .filter(membership -> membership.getStatus() == CommonInterestCircleMembership.MembershipStatus.PLACED)
                .map(CommonInterestCircleMembership::getCohortParticipant)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(participant -> updateParticipantStatusAfterFinalization(participant, cohort));

        cohort.setStatus(CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED);
        cohortRepository.save(cohort);
        return circles.stream()
                .map(circle -> toDto(circle, memberships))
                .toList();
    }

    public CommonInterestCircleDto toDto(CommonInterestCircle circle, List<CommonInterestCircleMembership> memberships) {
        if (circle == null) {
            return null;
        }
        List<CommonInterestCircleMemberDto> members = memberships == null ? List.of() : memberships.stream()
                .filter(membership -> membership.getCircle() != null && circle.getId() != null
                        && circle.getId().equals(membership.getCircle().getId()))
                .filter(membership -> membership.getStatus() == CommonInterestCircleMembership.MembershipStatus.PLACED)
                .map(this::toMemberDto)
                .toList();
        Profile facilitator = circle.getFacilitatorProfile();
        CompanyProgramCohort cohort = circle.getCohort();
        return CommonInterestCircleDto.builder()
                .id(circle.getId())
                .cohortId(cohort != null ? cohort.getId() : null)
                .name(circle.getName())
                .theme(circle.getTheme())
                .interestTags(circle.getInterestTags() != null ? circle.getInterestTags() : List.of())
                .facilitatorProfileId(facilitator != null ? facilitator.getId() : null)
                .facilitatorName(profileName(facilitator))
                .minSize(circle.getMinSize())
                .maxSize(circle.getMaxSize())
                .status(circle.getStatus())
                .nextSessionAt(circle.getNextSessionAt())
                .memberCount(members.size())
                .members(members)
                .version(circle.getVersion())
                .createdAt(circle.getCreatedAt())
                .updatedAt(circle.getUpdatedAt())
                .build();
    }

    private CommonInterestCircleMemberDto toMemberDto(CommonInterestCircleMembership membership) {
        CompanyProgramCohortParticipant participant = membership.getCohortParticipant();
        Profile profile = participant != null ? participant.getProfile() : null;
        return CommonInterestCircleMemberDto.builder()
                .membershipId(membership.getId())
                .circleId(membership.getCircle() != null ? membership.getCircle().getId() : null)
                .cohortParticipantId(participant != null ? participant.getId() : null)
                .profileId(profile != null ? profile.getId() : null)
                .profileName(participant != null ? participantName(participant) : null)
                .profileEmail(profile != null ? profile.getEmail() : participant != null ? participant.getEmailSnapshot() : null)
                .placementSource(membership.getPlacementSource())
                .status(membership.getStatus())
                .placedByUserId(membership.getPlacedByUserId())
                .placedAt(membership.getPlacedAt())
                .createdAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }

    private List<CommonInterestCircleMembership> membershipsForCircle(CommonInterestCircle circle) {
        if (circle == null || circle.getCohort() == null || circle.getCohort().getId() == null) {
            return List.of();
        }
        return membershipRepository.findByCircle_Cohort_Id(circle.getCohort().getId());
    }

    private void validateCircleReadyForFinalization(CommonInterestCircle circle) {
        if (circle.getFacilitatorProfile() == null) {
            throw new IllegalStateException("Every finalized circle must have a facilitator");
        }
        long placedCount = membershipRepository.countByCircle_IdAndStatus(
                circle.getId(),
                CommonInterestCircleMembership.MembershipStatus.PLACED
        );
        int minSize = resolvedMinSize(circle);
        int maxSize = resolvedMaxSize(circle);
        if (placedCount < minSize || placedCount > maxSize) {
            throw new IllegalStateException("Every finalized circle must have between minSize and maxSize placed members");
        }
    }

    private void updateParticipantStatusAfterPlacement(CompanyProgramCohortParticipant participant,
                                                       CompanyProgramCohort cohort) {
        if (participant == null || isTerminalParticipantStatus(participant)) {
            return;
        }
        if (isPlenaryAttended(participant) && isCohortFinalized(cohort)) {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING);
        } else {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE);
        }
        cohortParticipantRepository.save(participant);
    }

    private void updateParticipantStatusAfterFinalization(CompanyProgramCohortParticipant participant,
                                                          CompanyProgramCohort cohort) {
        if (participant == null || isTerminalParticipantStatus(participant)) {
            return;
        }
        if (isPlenaryAttended(participant)) {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING);
        } else {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE);
        }
        cohortParticipantRepository.save(participant);
    }

    private boolean isSuggestionEligible(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED
        ).contains(participant.getStatus());
    }

    private boolean hasPlacedMembership(CompanyProgramCohortParticipant participant) {
        return participant.getId() != null && membershipRepository.existsByCohortParticipant_IdAndStatus(
                participant.getId(),
                CommonInterestCircleMembership.MembershipStatus.PLACED
        );
    }

    private boolean isPlenaryAttended(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING,
                CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED
        ).contains(participant.getStatus());
    }

    private boolean isPlacedStatus(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING
        ).contains(participant.getStatus());
    }

    private boolean isTerminalParticipantStatus(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.WITHDRAWN,
                CompanyProgramCohortParticipant.CohortParticipantStatus.REJECTED
        ).contains(participant.getStatus());
    }

    private boolean isCohortFinalized(CompanyProgramCohort cohort) {
        return cohort != null && List.of(
                CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED,
                CompanyProgramCohort.CohortStatus.MATCHING,
                CompanyProgramCohort.CohortStatus.ACTIVE,
                CompanyProgramCohort.CohortStatus.COMPLETED
        ).contains(cohort.getStatus());
    }

    private void ensureParticipantBelongsToCircleCohort(CommonInterestCircle circle,
                                                        CompanyProgramCohortParticipant participant) {
        UUID circleCohortId = circle.getCohort() != null ? circle.getCohort().getId() : null;
        UUID participantCohortId = participant != null && participant.getCohort() != null ? participant.getCohort().getId() : null;
        if (circleCohortId != null && participantCohortId != null && !circleCohortId.equals(participantCohortId)) {
            throw new IllegalArgumentException("Participant does not belong to this cohort");
        }
    }

    private String primaryTag(CompanyProgramCohortParticipant participant) {
        if (participant.getInterestTags() == null) {
            return null;
        }
        return participant.getInterestTags().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String participantName(CompanyProgramCohortParticipant participant) {
        Profile profile = participant.getProfile();
        if (profile != null) {
            return profileName(profile);
        }
        return Stream.of(participant.getFirstNameSnapshot(), participant.getLastNameSnapshot())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse("Cohort participant");
    }

    private String profileName(Profile profile) {
        if (profile == null) {
            return null;
        }
        return Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse(profile.getEmail() != null ? profile.getEmail() : profile.getUsername());
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void validateCircleSizes(Integer minSize, Integer maxSize) {
        int resolvedMin = minSize != null ? minSize : 5;
        int resolvedMax = maxSize != null ? maxSize : 10;
        if (resolvedMin < 1) {
            throw new IllegalArgumentException("minSize must be greater than 0");
        }
        if (resolvedMax < resolvedMin) {
            throw new IllegalArgumentException("maxSize must be greater than or equal to minSize");
        }
    }

    private int resolvedMinSize(CompanyProgramCohort cohort) {
        return cohort != null && cohort.getCircleMinSize() != null ? cohort.getCircleMinSize() : 5;
    }

    private int resolvedMaxSize(CompanyProgramCohort cohort) {
        return cohort != null && cohort.getCircleMaxSize() != null ? cohort.getCircleMaxSize() : 10;
    }

    private int resolvedMinSize(CommonInterestCircle circle) {
        return circle != null && circle.getMinSize() != null ? circle.getMinSize() : 5;
    }

    private int resolvedMaxSize(CommonInterestCircle circle) {
        return circle != null && circle.getMaxSize() != null ? circle.getMaxSize() : 10;
    }
}
