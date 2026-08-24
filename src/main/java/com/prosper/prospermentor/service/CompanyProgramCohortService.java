package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyProgramCohortDto;
import com.prosper.prospermentor.dto.CreateCompanyProgramCohortRequest;
import com.prosper.prospermentor.dto.UpdateCompanyProgramCohortRequest;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramCohortService {

    private final CompanyProgramRepository companyProgramRepository;
    private final CompanyProgramCohortRepository cohortRepository;
    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CommonInterestCircleRepository circleRepository;

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

    private long countByStatus(List<CompanyProgramCohortParticipant> participants,
                               CompanyProgramCohortParticipant.CohortParticipantStatus status) {
        return participants.stream()
                .filter(participant -> participant.getStatus() == status)
                .count();
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
