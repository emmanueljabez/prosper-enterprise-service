package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyProgramCohortDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortWorkspaceDto;
import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.dto.CommonInterestCircleDto;
import com.prosper.prospermentor.dto.CreateCompanyProgramCohortRequest;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramCohortDto;
import com.prosper.prospermentor.dto.UpdateCompanyProgramCohortRequest;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramCohortService {

    private final CompanyProgramRepository companyProgramRepository;
    private final CompanyProgramCohortRepository cohortRepository;
    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    private final CommonInterestCircleRepository circleRepository;
    private final CommonInterestCircleMembershipRepository membershipRepository;
    private final CompanyProgramCohortGateService cohortGateService;

    @Transactional(readOnly = true)
    public List<CompanyProgramCohortDto> getCohorts(UUID companyProgramId) {
        if (!companyProgramRepository.existsById(companyProgramId)) {
            throw new NoSuchElementException("Company program not found");
        }

        return cohortRepository.findByCompanyProgram_IdOrderByStartsAtDescCreatedAtDesc(companyProgramId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyProgramCohortDto getCohort(UUID cohortId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        return toDto(cohort);
    }

    public CompanyProgramCohortDto createCohort(UUID companyProgramId,
                                                CreateCompanyProgramCohortRequest request,
                                                UUID createdByUserId) {
        CompanyProgram companyProgram = companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));
        validateCircleSizes(request.getCircleMinSize(), request.getCircleMaxSize());

        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setCompanyProgram(companyProgram);
        cohort.setName(request.getName());
        cohort.setCode(request.getCode());
        cohort.setChapter(request.getChapter());
        cohort.setRegion(request.getRegion());
        cohort.setStartsAt(request.getStartsAt());
        cohort.setEndsAt(request.getEndsAt());
        cohort.setSelfJoinEnabled(Boolean.TRUE.equals(request.getSelfJoinEnabled()));
        cohort.setSelfJoinExpiresAt(request.getSelfJoinExpiresAt());
        cohort.setSelfJoinCapacity(request.getSelfJoinCapacity());
        cohort.setCircleMinSize(request.getCircleMinSize() != null ? request.getCircleMinSize() : 5);
        cohort.setCircleMaxSize(request.getCircleMaxSize() != null ? request.getCircleMaxSize() : 10);
        cohort.setInterestTagSet(normalizeTags(request.getInterestTagSet()));
        cohort.setPlenaryEventType(request.getPlenaryEventType());
        cohort.setPlenaryEventId(request.getPlenaryEventId());
        cohort.setMatchingStartsAfterCirclesFinalized(request.getMatchingStartsAfterCirclesFinalized() == null
                ? true
                : request.getMatchingStartsAfterCirclesFinalized());
        cohort.setStatus(CompanyProgramCohort.CohortStatus.DRAFT);
        cohort.setCreatedByUserId(createdByUserId);

        CompanyProgramCohort saved = cohortRepository.save(cohort);
        log.info("Created company program cohort {} for company program {}", saved.getId(), companyProgramId);
        return toDto(saved);
    }

    public CompanyProgramCohortDto updateCohort(UUID cohortId, UpdateCompanyProgramCohortRequest request) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));

        Integer nextMin = request.getCircleMinSize() != null ? request.getCircleMinSize() : cohort.getCircleMinSize();
        Integer nextMax = request.getCircleMaxSize() != null ? request.getCircleMaxSize() : cohort.getCircleMaxSize();
        validateCircleSizes(nextMin, nextMax);

        if (request.getName() != null) {
            cohort.setName(request.getName());
        }
        if (request.getCode() != null) {
            cohort.setCode(request.getCode());
        }
        if (request.getChapter() != null) {
            cohort.setChapter(request.getChapter());
        }
        if (request.getRegion() != null) {
            cohort.setRegion(request.getRegion());
        }
        if (request.getStartsAt() != null) {
            cohort.setStartsAt(request.getStartsAt());
        }
        if (request.getEndsAt() != null) {
            cohort.setEndsAt(request.getEndsAt());
        }
        if (request.getSelfJoinEnabled() != null) {
            cohort.setSelfJoinEnabled(request.getSelfJoinEnabled());
        }
        if (request.getSelfJoinExpiresAt() != null) {
            cohort.setSelfJoinExpiresAt(request.getSelfJoinExpiresAt());
        }
        if (request.getSelfJoinCapacity() != null) {
            cohort.setSelfJoinCapacity(request.getSelfJoinCapacity());
        }
        if (request.getCircleMinSize() != null) {
            cohort.setCircleMinSize(request.getCircleMinSize());
        }
        if (request.getCircleMaxSize() != null) {
            cohort.setCircleMaxSize(request.getCircleMaxSize());
        }
        if (request.getInterestTagSet() != null) {
            cohort.setInterestTagSet(normalizeTags(request.getInterestTagSet()));
        }
        if (request.getPlenaryEventType() != null) {
            cohort.setPlenaryEventType(request.getPlenaryEventType());
        }
        if (request.getPlenaryEventId() != null) {
            cohort.setPlenaryEventId(request.getPlenaryEventId());
        }
        if (request.getMatchingStartsAfterCirclesFinalized() != null) {
            cohort.setMatchingStartsAfterCirclesFinalized(request.getMatchingStartsAfterCirclesFinalized());
        }

        return toDto(cohortRepository.save(cohort));
    }

    public CompanyProgramCohortDto openIntake(UUID cohortId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        if (!List.of(
                CompanyProgramCohort.CohortStatus.DRAFT,
                CompanyProgramCohort.CohortStatus.INTAKE_CLOSED
        ).contains(cohort.getStatus())) {
            throw new IllegalStateException("Cohort intake can only be opened from draft or closed intake");
        }
        cohort.setStatus(CompanyProgramCohort.CohortStatus.INTAKE_OPEN);
        return toDto(cohortRepository.save(cohort));
    }

    public CompanyProgramCohortDto closeIntake(UUID cohortId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        if (cohort.getStatus() != CompanyProgramCohort.CohortStatus.INTAKE_OPEN) {
            throw new IllegalStateException("Cohort intake can only be closed when intake is open");
        }
        cohort.setStatus(CompanyProgramCohort.CohortStatus.INTAKE_CLOSED);
        return toDto(cohortRepository.save(cohort));
    }

    @Transactional(readOnly = true)
    public CompanyProgramCohortWorkspaceDto getCohortDashboard(UUID cohortId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        List<CompanyProgramCohortParticipant> participants = cohortParticipantRepository.findByCohort_Id(cohortId);
        List<CompanyProgramCohortParticipant> enrolledParticipants = participants.stream()
                .filter(participant -> !List.of(
                        CompanyProgramCohortParticipant.CohortParticipantStatus.REJECTED,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.WITHDRAWN
                ).contains(participant.getStatus()))
                .toList();
        long enrolledCount = enrolledParticipants.size();
        long selfJoinedCount = enrolledParticipants.stream()
                .filter(participant -> participant.getSource() == CompanyProgramCohortParticipant.ParticipantSource.SELF_JOIN)
                .count();
        long pendingConfirmationCount = enrolledParticipants.stream()
                .filter(participant -> participant.getStatus() == CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING)
                .count();
        long duplicateReviewCount = enrolledParticipants.stream()
                .filter(participant -> participant.getDuplicateStatus() == CompanyProgramCohortParticipant.DuplicateStatus.POSSIBLE_DUPLICATE)
                .count();

        long plenaryAttendedCount = attendanceRepository.findByCohort_Id(cohortId).stream()
                .filter(attendance -> attendance.getStatus() == CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED)
                .map(CompanyProgramCohortPlenaryAttendance::getCohortParticipant)
                .filter(Objects::nonNull)
                .map(CompanyProgramCohortParticipant::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        List<CommonInterestCircle> circles = circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId);
        long placedParticipantCount = membershipRepository.findByCircle_Cohort_Id(cohortId).stream()
                .filter(membership -> membership.getStatus() == CommonInterestCircleMembership.MembershipStatus.PLACED)
                .map(CommonInterestCircleMembership::getCohortParticipant)
                .filter(Objects::nonNull)
                .map(CompanyProgramCohortParticipant::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
        long matchedCount = enrolledParticipants.stream()
                .filter(participant -> List.of(
                        CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED
                ).contains(participant.getStatus()))
                .count();
        long unplacedCount = Math.max(enrolledCount - placedParticipantCount, 0);

        double plenaryAttendanceRate = percentage(plenaryAttendedCount, enrolledCount);
        double matchCompletionRate = percentage(matchedCount, enrolledCount);
        List<String> riskIndicators = new ArrayList<>();
        if (enrolledCount > 0 && plenaryAttendanceRate < 70.0) {
            riskIndicators.add("LOW_PLENARY_ATTENDANCE");
        }
        if (unplacedCount > 0 && cohort.getStatus() == CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED) {
            riskIndicators.add("UNPLACED_AFTER_FINALIZATION");
        } else if (unplacedCount > 0) {
            riskIndicators.add("UNPLACED_PARTICIPANTS");
        }
        if (enrolledCount > 0 && matchCompletionRate < 50.0
                && List.of(
                        CompanyProgramCohort.CohortStatus.MATCHING,
                        CompanyProgramCohort.CohortStatus.ACTIVE,
                        CompanyProgramCohort.CohortStatus.COMPLETED
                ).contains(cohort.getStatus())) {
            riskIndicators.add("LOW_MATCH_COMPLETION");
        }

        return CompanyProgramCohortWorkspaceDto.builder()
                .cohortId(cohortId)
                .enrolledCount(enrolledCount)
                .selfJoinedCount(selfJoinedCount)
                .pendingConfirmationCount(pendingConfirmationCount)
                .duplicateReviewCount(duplicateReviewCount)
                .plenaryAttendedCount(plenaryAttendedCount)
                .plenaryAttendanceRate(plenaryAttendanceRate)
                .circleCount(circles.size())
                .unplacedCount(unplacedCount)
                .matchedCount(matchedCount)
                .matchCompletionRate(matchCompletionRate)
                .additionalSessionRequestCount(0)
                .feedbackResponseRate(0.0)
                .riskIndicators(riskIndicators)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompanyProgramCohortDto> getEmployeeCohorts(UUID profileId) {
        return cohortParticipantRepository.findByProfile_Id(profileId).stream()
                .map(this::toEmployeeCohortDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeCompanyProgramCohortDto getEmployeeCohort(UUID cohortId, UUID profileId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findByCohort_IdAndProfile_Id(cohortId, profileId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort membership not found"));
        return toEmployeeCohortDto(participant);
    }

    public EmployeeCompanyProgramCohortDto requestEmployeeCircle(UUID cohortId, UUID profileId, UUID circleId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findByCohort_IdAndProfile_Id(cohortId, profileId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort membership not found"));
        CommonInterestCircle circle = circleRepository.findByIdAndCohort_Id(circleId, cohortId)
                .orElseThrow(() -> new NoSuchElementException("Common interest circle not found"));

        if (membershipRepository.findByCohortParticipant_IdAndStatus(
                participant.getId(),
                CommonInterestCircleMembership.MembershipStatus.PLACED
        ).isPresent()) {
            throw new IllegalStateException("Participant is already placed in a circle");
        }
        if (membershipRepository.findByCohortParticipant_IdAndStatus(
                participant.getId(),
                CommonInterestCircleMembership.MembershipStatus.PENDING_REQUEST
        ).isPresent()) {
            throw new IllegalStateException("A circle request is already pending");
        }

        CommonInterestCircleMembership request = new CommonInterestCircleMembership();
        request.setCircle(circle);
        request.setCohortParticipant(participant);
        request.setPlacementSource(CommonInterestCircleMembership.PlacementSource.MENTEE_REQUESTED);
        request.setStatus(CommonInterestCircleMembership.MembershipStatus.PENDING_REQUEST);
        membershipRepository.save(request);
        return toEmployeeCohortDto(participant);
    }

    public CompanyProgramCohortDto toDto(CompanyProgramCohort cohort) {
        if (cohort == null) {
            return null;
        }

        List<CompanyProgramCohortParticipant> participants = cohort.getId() == null
                ? List.of()
                : cohortParticipantRepository.findByCohort_Id(cohort.getId());
        List<CommonInterestCircle> circles = cohort.getId() == null
                ? List.of()
                : circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohort.getId());
        long placedOrMatchedCount = participants.stream()
                .filter(participant -> List.of(
                        CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                        CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED
                ).contains(participant.getStatus()))
                .count();

        CompanyProgram companyProgram = cohort.getCompanyProgram();
        Company company = companyProgram != null ? companyProgram.getCompany() : null;

        return CompanyProgramCohortDto.builder()
                .id(cohort.getId())
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyProgramName(companyProgram != null ? companyProgram.getName() : null)
                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getName() : null)
                .name(cohort.getName())
                .code(cohort.getCode())
                .chapter(cohort.getChapter())
                .region(cohort.getRegion())
                .status(cohort.getStatus())
                .startsAt(cohort.getStartsAt())
                .endsAt(cohort.getEndsAt())
                .selfJoinEnabled(Boolean.TRUE.equals(cohort.getSelfJoinEnabled()))
                .selfJoinExpiresAt(cohort.getSelfJoinExpiresAt())
                .selfJoinCapacity(cohort.getSelfJoinCapacity())
                .circleMinSize(cohort.getCircleMinSize())
                .circleMaxSize(cohort.getCircleMaxSize())
                .interestTagSet(cohort.getInterestTagSet() != null ? cohort.getInterestTagSet() : List.of())
                .plenaryEventType(cohort.getPlenaryEventType())
                .plenaryEventId(cohort.getPlenaryEventId())
                .matchingStartsAfterCirclesFinalized(cohort.getMatchingStartsAfterCirclesFinalized())
                .createdByUserId(cohort.getCreatedByUserId())
                .participantCount(participants.size())
                .pendingCount(countByStatus(participants, CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING))
                .confirmedCount(countByStatus(participants, CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED))
                .circleCount(circles.size())
                .unplacedCount(Math.max(participants.size() - placedOrMatchedCount, 0))
                .matchedCount(countByStatus(participants, CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED)
                        + countByStatus(participants, CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE)
                        + countByStatus(participants, CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED))
                .version(cohort.getVersion())
                .createdAt(cohort.getCreatedAt())
                .updatedAt(cohort.getUpdatedAt())
                .build();
    }

    private EmployeeCompanyProgramCohortDto toEmployeeCohortDto(CompanyProgramCohortParticipant participant) {
        CompanyProgramCohort cohort = participant.getCohort();
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Company company = companyProgram != null ? companyProgram.getCompany() : null;
        CohortGateStatusDto gateStatus = cohortGateService.resolveGateStatusForCohortParticipant(participant.getId());
        CommonInterestCircleDto circle = membershipRepository.findByCohortParticipant_IdAndStatus(
                        participant.getId(),
                        CommonInterestCircleMembership.MembershipStatus.PLACED
                )
                .map(CommonInterestCircleMembership::getCircle)
                .map(this::toEmployeeCircleDto)
                .orElse(null);
        CompanyProgramParticipant programParticipant = participant.getCompanyProgramParticipant();

        return EmployeeCompanyProgramCohortDto.builder()
                .cohortId(cohort != null ? cohort.getId() : null)
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .cohortParticipantId(participant.getId())
                .companyProgramParticipantId(programParticipant != null ? programParticipant.getId() : null)
                .cohortName(cohort != null ? cohort.getName() : null)
                .companyProgramName(companyProgram != null ? companyProgram.getName() : null)
                .companyName(company != null ? company.getName() : null)
                .chapter(cohort != null ? cohort.getChapter() : participant.getChapter())
                .region(cohort != null ? cohort.getRegion() : participant.getRegion())
                .cohortStatus(cohort != null ? cohort.getStatus() : null)
                .participantStatus(participant.getStatus())
                .stages(toStageSummary(gateStatus))
                .circle(circle)
                .mentorAssignment(null)
                .build();
    }

    private CommonInterestCircleDto toEmployeeCircleDto(CommonInterestCircle circle) {
        if (circle == null) {
            return null;
        }
        return CommonInterestCircleDto.builder()
                .id(circle.getId())
                .cohortId(circle.getCohort() != null ? circle.getCohort().getId() : null)
                .name(circle.getName())
                .theme(circle.getTheme())
                .interestTags(circle.getInterestTags() != null ? circle.getInterestTags() : List.of())
                .facilitatorProfileId(circle.getFacilitatorProfile() != null ? circle.getFacilitatorProfile().getId() : null)
                .minSize(circle.getMinSize())
                .maxSize(circle.getMaxSize())
                .status(circle.getStatus())
                .nextSessionAt(circle.getNextSessionAt())
                .memberCount(0)
                .members(List.of())
                .version(circle.getVersion())
                .createdAt(circle.getCreatedAt())
                .updatedAt(circle.getUpdatedAt())
                .build();
    }

    private EmployeeCompanyProgramCohortDto.StageSummaryDto toStageSummary(CohortGateStatusDto gateStatus) {
        return EmployeeCompanyProgramCohortDto.StageSummaryDto.builder()
                .plenary(EmployeeCompanyProgramCohortDto.StageDto.builder()
                        .status(gateStatus != null && gateStatus.isPlenaryAttended()
                                ? "ATTENDED"
                                : gateStatus != null && gateStatus.isConfirmed() ? "READY" : "PENDING_CONFIRMATION")
                        .blockedReason(null)
                        .build())
                .circle(EmployeeCompanyProgramCohortDto.StageDto.builder()
                        .status(gateStatus != null && gateStatus.isPlacedInCircle()
                                ? "ACTIVE"
                                : gateStatus != null && gateStatus.isPlenaryAttended() ? "READY_TO_JOIN" : "LOCKED")
                        .blockedReason(gateStatus != null && !gateStatus.isPlacedInCircle() ? gateStatus.getBlockedReason() : null)
                        .build())
                .oneToOne(EmployeeCompanyProgramCohortDto.StageDto.builder()
                        .status(gateStatus != null && gateStatus.isEligibleForMatching()
                                ? "READY_FOR_MATCHING"
                                : "BLOCKED")
                        .blockedReason(gateStatus != null && !gateStatus.isEligibleForMatching() ? gateStatus.getBlockedReason() : null)
                        .build())
                .build();
    }

    private long countByStatus(List<CompanyProgramCohortParticipant> participants,
                               CompanyProgramCohortParticipant.CohortParticipantStatus status) {
        return participants.stream()
                .filter(participant -> participant.getStatus() == status)
                .count();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator * 1000.0) / (double) denominator) / 10.0;
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
            throw new IllegalArgumentException("circleMinSize must be greater than 0");
        }
        if (resolvedMax < resolvedMin) {
            throw new IllegalArgumentException("circleMaxSize must be greater than or equal to circleMinSize");
        }
    }
}
