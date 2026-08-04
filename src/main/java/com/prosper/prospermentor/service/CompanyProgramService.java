package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyProgramDto;
import com.prosper.prospermentor.dto.CompanyProgramCatalogStageRequest;
import com.prosper.prospermentor.dto.CreateCompanyProgramRequest;
import com.prosper.prospermentor.dto.UpdateCompanyProgramRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCatalogProgram;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.entity.Program;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyProgramCatalogProgramRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProgramRepository;
import com.prosper.prospermentor.service.support.CompanyProgramCatalogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramService {

    private static final LocalDateTime LIST_RANGE_START_INCLUSIVE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime LIST_RANGE_END_EXCLUSIVE = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999);
    private static final int DEFAULT_EMPLOYEE_SELECTION_WINDOW_HOURS = 48;
    private static final int DEFAULT_EMPLOYEE_SELECTION_SHORTLIST_SIZE = 5;
    private static final int MIN_EMPLOYEE_SELECTION_WINDOW_HOURS = 1;
    private static final int MAX_EMPLOYEE_SELECTION_WINDOW_HOURS = 168;
    private static final int MIN_EMPLOYEE_SELECTION_SHORTLIST_SIZE = 1;
    private static final int MAX_EMPLOYEE_SELECTION_SHORTLIST_SIZE = 20;

    private final CompanyProgramRepository companyProgramRepository;
    private final CompanyProgramCatalogProgramRepository companyProgramCatalogProgramRepository;
    private final CompanyRepository companyRepository;
    private final ProgramRepository programRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final JourneyTemplateService journeyTemplateService;
    private final JourneyInstanceService journeyInstanceService;
    private final CompanyProgramMatchWorkspaceService matchWorkspaceService;
    private final ParticipantPulseService participantPulseService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<CompanyProgramDto> getCompanyPrograms(UUID companyId,
                                                      String search,
                                                      CompanyProgram.CompanyProgramStatus status,
                                                      boolean liveOnly,
                                                      Pageable pageable) {
        return getCompanyPrograms(companyId, search, status, liveOnly, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CompanyProgramDto> getCompanyPrograms(UUID companyId,
                                                      String search,
                                                      CompanyProgram.CompanyProgramStatus status,
                                                      boolean liveOnly,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      Pageable pageable) {
        ensureCompanyExists(companyId);

        CompanyProgram.CompanyProgramStatus effectiveStatus = liveOnly
                ? CompanyProgram.CompanyProgramStatus.LIVE
                : status;

        String normalizedSearch = search != null && !search.trim().isBlank() ? search.trim() : "";
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        LocalDateTime rangeStart = startDate != null ? startDate.atStartOfDay() : LIST_RANGE_START_INCLUSIVE;
        LocalDateTime rangeEndExclusive = endDate != null ? endDate.plusDays(1).atStartOfDay() : LIST_RANGE_END_EXCLUSIVE;

        return companyProgramRepository.findByCompanyIdWithFilters(
                        companyId,
                        effectiveStatus,
                        normalizedSearch,
                        rangeStart,
                        rangeEndExclusive,
                        pageable
                )
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyProgram> getCompanyProgram(UUID companyProgramId) {
        return companyProgramRepository.findById(companyProgramId);
    }

    @Transactional(readOnly = true)
    public Optional<CompanyProgramDto> getCompanyProgramDto(UUID companyProgramId) {
        return getCompanyProgram(companyProgramId).map(this::toDto);
    }

    public ApiResponse<CompanyProgramDto> createCompanyProgram(UUID companyId,
                                                               CreateCompanyProgramRequest request,
                                                               UUID createdByUserId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NoSuchElementException("Company not found"));

        validateDates(request.getStartsAt(), request.getEndsAt());
        List<ResolvedCatalogStage> resolvedStages = resolveCatalogStages(request.getCatalogStages(), request.getProgramId());

        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setCompany(company);
        companyProgram.setProgram(resolvedStages.get(0).program());
        companyProgram.setJourneyTemplate(resolveJourneyTemplate(request.getJourneyTemplateId()));
        companyProgram.setName(request.getName().trim());
        companyProgram.setObjective(trimToNull(request.getObjective()));
        companyProgram.setTargetAudienceDescription(trimToNull(request.getTargetAudienceDescription()));
        companyProgram.setMatchingMode(request.getMatchingMode() != null
                ? request.getMatchingMode()
                : CompanyProgram.MatchingMode.ADMIN_ASSIGN);
        companyProgram.setEmployeeSelectionWindowHours(
                resolveEmployeeSelectionWindowHours(request.getEmployeeSelectionWindowHours(), DEFAULT_EMPLOYEE_SELECTION_WINDOW_HOURS)
        );
        companyProgram.setEmployeeSelectionShortlistSize(
                resolveEmployeeSelectionShortlistSize(request.getEmployeeSelectionShortlistSize(), DEFAULT_EMPLOYEE_SELECTION_SHORTLIST_SIZE)
        );
        companyProgram.setRequiresMentorForSessionSteps(
                request.getRequiresMentorForSessionSteps() == null || request.getRequiresMentorForSessionSteps()
        );
        companyProgram.setVisibilityPolicyCode(trimToNull(request.getVisibilityPolicyCode()));
        companyProgram.setMaxParticipants(request.getMaxParticipants());
        companyProgram.setStartsAt(request.getStartsAt());
        companyProgram.setEndsAt(request.getEndsAt());
        companyProgram.setCreatedByUserId(createdByUserId);
        companyProgram.setStatus(CompanyProgram.CompanyProgramStatus.DRAFT);
        applyCatalogStages(companyProgram, resolvedStages);

        CompanyProgram saved = companyProgramRepository.save(companyProgram);
        log.info("Created company program {} for company {}", saved.getId(), companyId);
        return ApiResponse.success("Company program created successfully", toDto(saved));
    }

    public ApiResponse<CompanyProgramDto> updateCompanyProgram(UUID companyProgramId,
                                                               UpdateCompanyProgramRequest request) {
        CompanyProgram companyProgram = companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));
        UUID previousJourneyTemplateId = companyProgram.getJourneyTemplate() != null
                ? companyProgram.getJourneyTemplate().getId()
                : null;
        UpdateCompanyProgramRequest.JourneyTemplateUpdateScope journeyTemplateUpdateScope =
                request.getJourneyTemplateUpdateScope() != null
                        ? request.getJourneyTemplateUpdateScope()
                        : UpdateCompanyProgramRequest.JourneyTemplateUpdateScope.FUTURE_ENROLLMENTS_ONLY;

        if (companyProgram.getStatus() == CompanyProgram.CompanyProgramStatus.CANCELLED
                || companyProgram.getStatus() == CompanyProgram.CompanyProgramStatus.ARCHIVED) {
            return ApiResponse.error("Cancelled or archived programs cannot be edited");
        }

        boolean hasCatalogStageUpdate = hasCatalogStageUpdate(request);
        if (hasCatalogStageUpdate) {
            List<ResolvedCatalogStage> resolvedStages = resolveCatalogStages(request.getCatalogStages(), request.getProgramId());
            boolean stagesChanged = !matchesCurrentCatalogJourney(companyProgram, resolvedStages);
            if (stagesChanged && companyProgramParticipantRepository.countByCompanyProgram_Id(companyProgramId) > 0) {
                return ApiResponse.error("Prosper program journey cannot be changed after employees are enrolled");
            }

            if (stagesChanged) {
                companyProgram = resetCatalogStages(companyProgramId);
            }
            companyProgram.setProgram(resolvedStages.get(0).program());
            applyCatalogStages(companyProgram, resolvedStages);
        }
        if (request.getJourneyTemplateId() != null) {
            companyProgram.setJourneyTemplate(resolveJourneyTemplate(request.getJourneyTemplateId()));
        }
        if (request.getName() != null && !request.getName().trim().isBlank()) {
            companyProgram.setName(request.getName().trim());
        }
        if (request.getObjective() != null) {
            companyProgram.setObjective(trimToNull(request.getObjective()));
        }
        if (request.getTargetAudienceDescription() != null) {
            companyProgram.setTargetAudienceDescription(trimToNull(request.getTargetAudienceDescription()));
        }
        if (request.getMatchingMode() != null) {
            companyProgram.setMatchingMode(request.getMatchingMode());
        }
        if (request.getEmployeeSelectionWindowHours() != null) {
            companyProgram.setEmployeeSelectionWindowHours(
                    resolveEmployeeSelectionWindowHours(
                            request.getEmployeeSelectionWindowHours(),
                            companyProgram.getEmployeeSelectionWindowHours()
                    )
            );
        }
        if (request.getEmployeeSelectionShortlistSize() != null) {
            companyProgram.setEmployeeSelectionShortlistSize(
                    resolveEmployeeSelectionShortlistSize(
                            request.getEmployeeSelectionShortlistSize(),
                            companyProgram.getEmployeeSelectionShortlistSize()
                    )
            );
        }
        if (request.getRequiresMentorForSessionSteps() != null) {
            companyProgram.setRequiresMentorForSessionSteps(request.getRequiresMentorForSessionSteps());
        }
        if (request.getVisibilityPolicyCode() != null) {
            companyProgram.setVisibilityPolicyCode(trimToNull(request.getVisibilityPolicyCode()));
        }
        if (request.getMaxParticipants() != null) {
            companyProgram.setMaxParticipants(request.getMaxParticipants());
        }
        java.time.LocalDateTime resolvedStartsAt = request.getStartsAt() != null
                ? request.getStartsAt()
                : companyProgram.getStartsAt();
        java.time.LocalDateTime resolvedEndsAt = request.getEndsAt() != null
                ? request.getEndsAt()
                : companyProgram.getEndsAt();
        validateDates(resolvedStartsAt, resolvedEndsAt);
        companyProgram.setStartsAt(resolvedStartsAt);
        companyProgram.setEndsAt(resolvedEndsAt);

        CompanyProgram saved = companyProgramRepository.save(companyProgram);
        UUID currentJourneyTemplateId = saved.getJourneyTemplate() != null
                ? saved.getJourneyTemplate().getId()
                : null;
        boolean journeyTemplateChanged = !Objects.equals(previousJourneyTemplateId, currentJourneyTemplateId);
        if (saved.getJourneyTemplate() != null) {
            if (journeyTemplateChanged
                    && journeyTemplateUpdateScope == UpdateCompanyProgramRequest.JourneyTemplateUpdateScope.MIGRATE_NOT_STARTED_PARTICIPANTS) {
                JourneyInstanceService.JourneyTemplateMigrationSummary migrationSummary =
                        journeyInstanceService.migrateTemplateForNotStartedParticipants(saved);
                log.info("Migrated journey template for program {}: migrated={}, created={}, retained={}",
                        saved.getId(),
                        migrationSummary.migratedParticipants(),
                        migrationSummary.createdParticipants(),
                        migrationSummary.retainedParticipants());
            } else {
                journeyInstanceService.ensureJourneyInstancesForProgram(saved);
            }
            journeyInstanceService.refreshJourneysForProgram(saved);
        }
        matchWorkspaceService.syncProgramParticipants(saved);
        return ApiResponse.success("Company program updated successfully", toDto(saved));
    }

    public ApiResponse<CompanyProgramDto> launchProgram(UUID companyProgramId) {
        return updateStatus(companyProgramId, CompanyProgram.CompanyProgramStatus.LIVE, "Company program launched successfully");
    }

    public ApiResponse<CompanyProgramDto> pauseProgram(UUID companyProgramId) {
        return updateStatus(companyProgramId, CompanyProgram.CompanyProgramStatus.PAUSED, "Company program paused successfully");
    }

    public ApiResponse<CompanyProgramDto> completeProgram(UUID companyProgramId) {
        return updateStatus(companyProgramId, CompanyProgram.CompanyProgramStatus.COMPLETED, "Company program completed successfully");
    }

    public ApiResponse<CompanyProgramDto> cancelProgram(UUID companyProgramId) {
        return updateStatus(companyProgramId, CompanyProgram.CompanyProgramStatus.CANCELLED, "Company program cancelled successfully");
    }

    private ApiResponse<CompanyProgramDto> updateStatus(UUID companyProgramId,
                                                        CompanyProgram.CompanyProgramStatus targetStatus,
                                                        String successMessage) {
        CompanyProgram companyProgram = companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));

        if (!isValidTransition(companyProgram.getStatus(), targetStatus)) {
            return ApiResponse.error("Cannot change company program status from "
                    + companyProgram.getStatus() + " to " + targetStatus);
        }

        companyProgram.setStatus(targetStatus);
        CompanyProgram saved = companyProgramRepository.save(companyProgram);
        if (targetStatus == CompanyProgram.CompanyProgramStatus.COMPLETED) {
            participantPulseService.createProgramEndPulsesForProgram(saved);
        }
        matchWorkspaceService.syncProgramParticipants(saved);
        return ApiResponse.success(successMessage, toDto(saved));
    }

    private boolean isValidTransition(CompanyProgram.CompanyProgramStatus currentStatus,
                                      CompanyProgram.CompanyProgramStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return true;
        }

        return switch (currentStatus) {
            case DRAFT -> targetStatus == CompanyProgram.CompanyProgramStatus.LIVE
                    || targetStatus == CompanyProgram.CompanyProgramStatus.CANCELLED
                    || targetStatus == CompanyProgram.CompanyProgramStatus.ARCHIVED;
            case LIVE -> targetStatus == CompanyProgram.CompanyProgramStatus.PAUSED
                    || targetStatus == CompanyProgram.CompanyProgramStatus.COMPLETED
                    || targetStatus == CompanyProgram.CompanyProgramStatus.CANCELLED;
            case PAUSED -> targetStatus == CompanyProgram.CompanyProgramStatus.LIVE
                    || targetStatus == CompanyProgram.CompanyProgramStatus.COMPLETED
                    || targetStatus == CompanyProgram.CompanyProgramStatus.CANCELLED;
            case COMPLETED, CANCELLED -> targetStatus == CompanyProgram.CompanyProgramStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private Program resolveTemplateProgram(UUID programId) {
        if (programId == null) {
            return null;
        }
        return programRepository.findById(programId)
                .orElseThrow(() -> new NoSuchElementException("Template program not found"));
    }

    private JourneyTemplate resolveJourneyTemplate(UUID journeyTemplateId) {
        if (journeyTemplateId == null) {
            return null;
        }
        return journeyTemplateService.getTemplate(journeyTemplateId)
                .orElseThrow(() -> new NoSuchElementException("Journey template not found"));
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new NoSuchElementException("Company not found");
        }
    }

    private void validateDates(java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("Program end date must be after the start date");
        }
    }

    private Integer resolveEmployeeSelectionWindowHours(Integer value, Integer fallback) {
        int resolved = value != null
                ? value
                : (fallback != null ? fallback : DEFAULT_EMPLOYEE_SELECTION_WINDOW_HOURS);
        if (resolved < MIN_EMPLOYEE_SELECTION_WINDOW_HOURS || resolved > MAX_EMPLOYEE_SELECTION_WINDOW_HOURS) {
            throw new IllegalArgumentException("Employee selection window must be between "
                    + MIN_EMPLOYEE_SELECTION_WINDOW_HOURS + " and "
                    + MAX_EMPLOYEE_SELECTION_WINDOW_HOURS + " hours");
        }
        return resolved;
    }

    private Integer resolveEmployeeSelectionShortlistSize(Integer value, Integer fallback) {
        int resolved = value != null
                ? value
                : (fallback != null ? fallback : DEFAULT_EMPLOYEE_SELECTION_SHORTLIST_SIZE);
        if (resolved < MIN_EMPLOYEE_SELECTION_SHORTLIST_SIZE || resolved > MAX_EMPLOYEE_SELECTION_SHORTLIST_SIZE) {
            throw new IllegalArgumentException("Employee selection shortlist size must be between "
                    + MIN_EMPLOYEE_SELECTION_SHORTLIST_SIZE + " and "
                    + MAX_EMPLOYEE_SELECTION_SHORTLIST_SIZE);
        }
        return resolved;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CompanyProgramDto toDto(CompanyProgram companyProgram) {
        Program anchorProgram = CompanyProgramCatalogSupport.anchorProgram(companyProgram);
        return CompanyProgramDto.builder()
                .id(companyProgram.getId())
                .companyId(companyProgram.getCompany() != null ? companyProgram.getCompany().getId() : null)
                .companyName(companyProgram.getCompany() != null ? companyProgram.getCompany().getName() : null)
                .templateProgramId(anchorProgram != null ? anchorProgram.getId() : null)
                .templateProgramName(anchorProgram != null ? anchorProgram.getName() : null)
                .catalogJourneySummary(CompanyProgramCatalogSupport.buildJourneySummary(companyProgram))
                .catalogProgramCount(CompanyProgramCatalogSupport.orderedStages(companyProgram).size())
                .catalogStages(CompanyProgramCatalogSupport.toStageDtos(companyProgram))
                .journeyTemplateId(companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getId() : null)
                .journeyTemplateName(companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getName() : null)
                .name(companyProgram.getName())
                .objective(companyProgram.getObjective())
                .targetAudienceDescription(companyProgram.getTargetAudienceDescription())
                .status(companyProgram.getStatus())
                .matchingMode(companyProgram.getMatchingMode())
                .employeeSelectionWindowHours(companyProgram.getEmployeeSelectionWindowHours())
                .employeeSelectionShortlistSize(companyProgram.getEmployeeSelectionShortlistSize())
                .requiresMentorForSessionSteps(companyProgram.getRequiresMentorForSessionSteps())
                .visibilityPolicyCode(companyProgram.getVisibilityPolicyCode())
                .maxParticipants(companyProgram.getMaxParticipants())
                .startsAt(companyProgram.getStartsAt())
                .endsAt(companyProgram.getEndsAt())
                .createdByUserId(companyProgram.getCreatedByUserId())
                .version(companyProgram.getVersion())
                .createdAt(companyProgram.getCreatedAt())
                .updatedAt(companyProgram.getUpdatedAt())
                .build();
    }

    private boolean hasCatalogStageUpdate(UpdateCompanyProgramRequest request) {
        return request.getProgramId() != null
                || (request.getCatalogStages() != null && !request.getCatalogStages().isEmpty());
    }

    private List<ResolvedCatalogStage> resolveCatalogStages(List<CompanyProgramCatalogStageRequest> stageRequests, UUID fallbackProgramId) {
        List<CompanyProgramCatalogStageRequest> normalizedRequests = new ArrayList<>();
        if (stageRequests != null) {
            normalizedRequests.addAll(stageRequests.stream()
                    .filter(Objects::nonNull)
                    .filter(stage -> stage.getProgramId() != null)
                    .toList());
        }

        if (normalizedRequests.isEmpty() && fallbackProgramId != null) {
            normalizedRequests.add(new CompanyProgramCatalogStageRequest(
                    fallbackProgramId,
                    null,
                    CompanyProgramCatalogProgram.StageType.CORE
            ));
        }

        if (normalizedRequests.isEmpty()) {
            throw new IllegalArgumentException("At least one Prosper program is required");
        }

        return IntStream.range(0, normalizedRequests.size())
                .mapToObj(index -> {
                    CompanyProgramCatalogStageRequest request = normalizedRequests.get(index);
                    Program program = resolveTemplateProgram(request.getProgramId());
                    return new ResolvedCatalogStage(
                            program,
                            index + 1,
                            trimToNull(request.getJourneyStageName()),
                            request.getStageType() != null ? request.getStageType() : CompanyProgramCatalogProgram.StageType.CORE
                    );
                })
                .toList();
    }

    private void applyCatalogStages(CompanyProgram companyProgram, List<ResolvedCatalogStage> stages) {
        List<CompanyProgramCatalogProgram> catalogPrograms = companyProgram.getCatalogPrograms();
        if (catalogPrograms == null) {
            catalogPrograms = new ArrayList<>();
            companyProgram.setCatalogPrograms(catalogPrograms);
        } else {
            catalogPrograms.clear();
        }

        for (ResolvedCatalogStage stage : stages) {
            CompanyProgramCatalogProgram catalogProgram = new CompanyProgramCatalogProgram();
            catalogProgram.setCompanyProgram(companyProgram);
            catalogProgram.setProgram(stage.program());
            catalogProgram.setJourneyOrder(stage.order());
            catalogProgram.setJourneyStageName(stage.stageName());
            catalogProgram.setStageType(stage.stageType());
            catalogPrograms.add(catalogProgram);
        }
    }

    private CompanyProgram resetCatalogStages(UUID companyProgramId) {
        companyProgramCatalogProgramRepository.deleteAllForCompanyProgram(companyProgramId);
        entityManager.flush();
        entityManager.clear();
        return companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));
    }

    private boolean matchesCurrentCatalogJourney(CompanyProgram companyProgram, List<ResolvedCatalogStage> resolvedStages) {
        List<CompanyProgramCatalogProgram> currentStages = CompanyProgramCatalogSupport.orderedStages(companyProgram);
        if (currentStages.size() != resolvedStages.size()) {
            return false;
        }

        for (int index = 0; index < currentStages.size(); index++) {
            CompanyProgramCatalogProgram current = currentStages.get(index);
            ResolvedCatalogStage updated = resolvedStages.get(index);

            UUID currentProgramId = current.getProgram() != null ? current.getProgram().getId() : null;
            UUID updatedProgramId = updated.program() != null ? updated.program().getId() : null;
            if (!Objects.equals(currentProgramId, updatedProgramId)) {
                return false;
            }
            if (!Objects.equals(trimToNull(current.getJourneyStageName()), updated.stageName())) {
                return false;
            }
            if (current.getStageType() != updated.stageType()) {
                return false;
            }
        }

        return true;
    }

    private record ResolvedCatalogStage(
            Program program,
            int order,
            String stageName,
            CompanyProgramCatalogProgram.StageType stageType
    ) {
    }
}
