package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.dto.CompanyProgramMentorCandidateDto;
import com.prosper.prospermentor.dto.EmployeeMentorSelectionOptionsDto;
import com.prosper.prospermentor.dto.MatchWorkspaceSummaryDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.dto.ParticipantMatchWorkspaceUpdateDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMatchOption;
import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyProgramMatchOptionRepository;
import com.prosper.prospermentor.repository.CompanyProgramMatchWorkspaceRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.JourneyInstanceStepRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramMatchWorkspaceService {

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> SELECTABLE_PARTICIPANT_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE
    );

    private static final EnumSet<CompanyProgram.CompanyProgramStatus> MATCHING_PROGRAM_STATUSES = EnumSet.of(
            CompanyProgram.CompanyProgramStatus.DRAFT,
            CompanyProgram.CompanyProgramStatus.LIVE,
            CompanyProgram.CompanyProgramStatus.PAUSED
    );

    private final CompanyProgramMatchWorkspaceRepository workspaceRepository;
    private final CompanyProgramMatchOptionRepository optionRepository;
    private final CompanyProgramParticipantRepository participantRepository;
    private final CompanyProgramMentorAssignmentRepository assignmentRepository;
    private final ProfileRepository profileRepository;
    private final JourneyInstanceStepRepository journeyInstanceStepRepository;
    private final CompanyProgramMentorAssignmentService mentorAssignmentService;
    private final JourneyInstanceService journeyInstanceService;
    private final CompanyProgramCohortGateService cohortGateService;

    @Value("${company-programs.employee-select.shortlist-size:5}")
    private int shortlistSize;

    @Value("${company-programs.employee-select.window-hours:48}")
    private int employeeSelectionWindowHours;

    @Value("${company-programs.employee-select.expiry-batch-size:200}")
    private int expiryBatchSize;

    public void initializeWorkspacesForParticipants(Collection<CompanyProgramParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }

        for (CompanyProgramParticipant participant : participants) {
            syncWorkspaceForParticipant(participant, true);
        }
    }

    public void syncProgramParticipants(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null) {
            return;
        }

        EnumSet<CompanyProgramParticipant.ParticipantStatus> statuses = EnumSet.of(
                CompanyProgramParticipant.ParticipantStatus.ENROLLED,
                CompanyProgramParticipant.ParticipantStatus.ACTIVE,
                CompanyProgramParticipant.ParticipantStatus.COMPLETED
        );
        List<CompanyProgramParticipant> participants = participantRepository.findByCompanyProgram_IdAndStatusIn(
                companyProgram.getId(),
                statuses
        );
        initializeWorkspacesForParticipants(participants);
    }

    public ParticipantMatchWorkspaceUpdateDto refreshParticipantWorkspace(UUID participantId, boolean resetDeadline) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        CompanyProgramMatchWorkspace workspace = syncWorkspaceForParticipant(participant, resetDeadline);
        MentorAssignmentSummaryDto assignmentSummary = assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId)
                .map(mentorAssignmentService::toAssignmentSummary)
                .orElse(null);

        return ParticipantMatchWorkspaceUpdateDto.builder()
                .participantId(participantId)
                .matchWorkspace(toSummary(workspace, resolveShortlistCount(workspace.getId()), assignmentSummary != null))
                .mentorAssignment(assignmentSummary)
                .build();
    }

    public ParticipantMatchWorkspaceUpdateDto forceAutoAssign(UUID participantId, UUID resolvedByUserId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        CompanyProgramMatchWorkspace workspace = syncWorkspaceForParticipant(participant, true);
        if (assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId).isPresent()) {
            MentorAssignmentSummaryDto summary = assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId)
                    .map(mentorAssignmentService::toAssignmentSummary)
                    .orElse(null);
            return ParticipantMatchWorkspaceUpdateDto.builder()
                    .participantId(participantId)
                    .matchWorkspace(toSummary(workspace, resolveShortlistCount(workspace.getId()), summary != null))
                    .mentorAssignment(summary)
                    .build();
        }

        MentorAssignmentSummaryDto assigned = tryAssignFromWorkspaceOptions(
                participant,
                workspace,
                resolvedByUserId,
                CompanyProgramMatchWorkspace.ResolverType.ADMIN
        );

        return ParticipantMatchWorkspaceUpdateDto.builder()
                .participantId(participantId)
                .matchWorkspace(toSummary(workspace, resolveShortlistCount(workspace.getId()), assigned != null))
                .mentorAssignment(assigned)
                .build();
    }

    public void markAdminAssignment(UUID participantId, UUID adminUserId) {
        CompanyProgramMatchWorkspace workspace = workspaceRepository.findByParticipant_Id(participantId)
                .orElseGet(() -> {
                    CompanyProgramParticipant participant = participantRepository.findById(participantId)
                            .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));
                    CompanyProgramMatchWorkspace created = new CompanyProgramMatchWorkspace();
                    created.setParticipant(participant);
                    return created;
                });

        markAssigned(workspace, CompanyProgramMatchWorkspace.ResolverType.ADMIN, adminUserId);
        workspaceRepository.save(workspace);
        journeyInstanceService.refreshJourneyForParticipant(participantId);
    }

    public void onAssignmentRemoved(UUID participantId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));
        syncWorkspaceForParticipant(participant, true);
        journeyInstanceService.refreshJourneyForParticipant(participantId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, MatchWorkspaceSummaryDto> getWorkspaceSummaries(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CompanyProgramMatchWorkspace> workspacesByParticipant = workspaceRepository.findByParticipant_IdIn(participantIds).stream()
                .collect(Collectors.toMap(
                        workspace -> workspace.getParticipant().getId(),
                        workspace -> workspace
                ));
        Map<UUID, CompanyProgramParticipant> participantsById = participantRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(CompanyProgramParticipant::getId, participant -> participant));

        Map<UUID, Boolean> hasAssignmentsByParticipant = assignmentRepository.findByParticipant_IdInAndJourneyInstanceStepIsNull(participantIds).stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getParticipant().getId(),
                        assignment -> Boolean.TRUE,
                        (first, second) -> first
                ));

        Map<UUID, Integer> shortlistCountByWorkspaceId = resolveShortlistCounts(
                workspacesByParticipant.values().stream()
                        .map(CompanyProgramMatchWorkspace::getId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        Map<UUID, MatchWorkspaceSummaryDto> summaries = new LinkedHashMap<>();
        for (UUID participantId : participantIds) {
            CompanyProgramMatchWorkspace workspace = workspacesByParticipant.get(participantId);
            boolean hasAssignment = Boolean.TRUE.equals(hasAssignmentsByParticipant.get(participantId));
            if (workspace == null) {
                CompanyProgramParticipant participant = participantsById.get(participantId);
                summaries.put(participantId, buildDerivedSummary(participant, hasAssignment));
                continue;
            }

            Integer shortlistCount = shortlistCountByWorkspaceId.getOrDefault(workspace.getId(), 0);
            summaries.put(participantId, toSummary(workspace, shortlistCount, hasAssignment));
        }

        return summaries;
    }

    public EmployeeMentorSelectionOptionsDto getEmployeeSelectionOptions(UUID participantId, UUID profileId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));
        ensureParticipantOwnership(participant, profileId);

        CompanyProgram companyProgram = participant.getCompanyProgram();
        if (companyProgram == null) {
            throw new IllegalStateException("Employee mentor selection is not enabled for this program");
        }

        CompanyProgramMatchWorkspace workspace = syncWorkspaceForParticipant(participant, false);
        MatchWorkspaceSummaryDto summary = toSummary(workspace, resolveShortlistCount(workspace.getId()), false);
        if (workspace.getStatus() == CompanyProgramMatchWorkspace.MatchStatus.INACTIVE) {
            return EmployeeMentorSelectionOptionsDto.builder()
                    .participantId(participantId)
                    .companyProgramId(companyProgram.getId())
                    .companyProgramName(companyProgram.getName())
                    .matchingMode(companyProgram.getMatchingMode())
                    .matchWorkspace(summary)
                    .options(List.of())
                    .count(0)
                    .build();
        }

        List<CompanyProgramMatchOption> options = optionRepository.findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(workspace.getId());
        List<CompanyProgramMentorCandidateDto> enrichedOptions = enrichOptions(companyProgram, options);
        boolean hasAssignment = assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId).isPresent();

        return EmployeeMentorSelectionOptionsDto.builder()
                .participantId(participantId)
                .companyProgramId(companyProgram.getId())
                .companyProgramName(companyProgram.getName())
                .matchingMode(companyProgram.getMatchingMode())
                .matchWorkspace(toSummary(workspace, enrichedOptions.size(), hasAssignment))
                .options(enrichedOptions)
                .count(enrichedOptions.size())
                .build();
    }

    public MentorAssignmentSummaryDto selectMentorForEmployee(UUID participantId, UUID profileId, UUID mentorId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));
        ensureParticipantOwnership(participant, profileId);

        CompanyProgram companyProgram = participant.getCompanyProgram();
        if (companyProgram == null || companyProgram.getMatchingMode() != CompanyProgram.MatchingMode.EMPLOYEE_SELECT) {
            throw new IllegalStateException("Employee mentor selection is not enabled for this program");
        }
        ensureCohortGateAllowsMatching(participant);

        CompanyProgramMatchWorkspace workspace = syncWorkspaceForParticipant(participant, false);
        LocalDateTime now = LocalDateTime.now();
        if (workspace.getSelectionDeadlineAt() != null
                && workspace.getSelectionDeadlineAt().isBefore(now)
                && workspace.getStatus() == CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION) {
            throw new IllegalStateException("Your mentor selection window has expired. Contact your company admin.");
        }

        if (workspace.getStatus() != CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION) {
            throw new IllegalStateException("Mentor selection is not currently open for this program");
        }

        boolean mentorInShortlist = optionRepository.findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(workspace.getId()).stream()
                .anyMatch(option -> option.getMentor() != null && mentorId.equals(option.getMentor().getId()));
        if (!mentorInShortlist) {
            throw new IllegalStateException("Selected mentor is not in your current shortlist");
        }

        ApiResponse<MentorAssignmentSummaryDto> response = mentorAssignmentService.assignMentor(participantId, mentorId, profileId);
        if (!response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException(response.getMessage() != null ? response.getMessage() : "Failed to assign mentor");
        }

        markAssigned(workspace, CompanyProgramMatchWorkspace.ResolverType.EMPLOYEE, profileId);
        workspaceRepository.save(workspace);
        journeyInstanceService.refreshJourneyForParticipant(participantId);
        return response.getData();
    }

    public MentorAssignmentSummaryDto selectMarketplaceMentorForEmployee(UUID participantId, UUID profileId, UUID mentorId) {
        return selectMarketplaceMentorForEmployee(participantId, profileId, mentorId, null);
    }

    public MentorAssignmentSummaryDto selectMarketplaceMentorForEmployee(UUID participantId,
                                                                         UUID profileId,
                                                                         UUID mentorId,
                                                                         UUID journeyInstanceStepId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));
        ensureParticipantOwnership(participant, profileId);

        CompanyProgram companyProgram = participant.getCompanyProgram();
        if (companyProgram == null) {
            throw new IllegalStateException("Employee mentor selection is not enabled for this program");
        }

        if (!MATCHING_PROGRAM_STATUSES.contains(companyProgram.getStatus())) {
            throw new IllegalStateException("Mentor selection is not currently open for this program");
        }

        if (!SELECTABLE_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
            throw new IllegalStateException("Mentor selection is only available while you are enrolled or active in this program");
        }
        ensureCohortGateAllowsMatching(participant);

        JourneyInstanceStep journeyInstanceStep = resolveJourneyInstanceStep(participantId, journeyInstanceStepId);
        boolean assignmentAlreadyExists = journeyInstanceStep != null
                ? assignmentRepository.findByParticipant_IdAndJourneyInstanceStep_Id(participantId, journeyInstanceStep.getId()).isPresent()
                : assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId).isPresent();
        if (assignmentAlreadyExists) {
            throw new IllegalStateException(journeyInstanceStep != null
                    ? "A mentor is already assigned for this journey step"
                    : "A mentor is already assigned for this program");
        }

        CompanyProgramMatchWorkspace workspace = workspaceRepository.findByParticipant_Id(participantId)
                .orElseGet(() -> {
                    CompanyProgramMatchWorkspace created = new CompanyProgramMatchWorkspace();
                    created.setParticipant(participant);
                    created.setStatus(CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION);
                    created.setSelectionDeadlineAt(resolveSelectionDeadline(null, true, companyProgram));
                    return created;
                });

        LocalDateTime now = LocalDateTime.now();
        if (workspace.getSelectionDeadlineAt() != null && workspace.getSelectionDeadlineAt().isBefore(now)) {
            throw new IllegalStateException("Your mentor selection window has expired. Contact your company admin.");
        }

        ApiResponse<MentorAssignmentSummaryDto> response =
                journeyInstanceStep != null
                        ? mentorAssignmentService.assignMarketplaceMentor(participantId, mentorId, profileId, journeyInstanceStep)
                        : mentorAssignmentService.assignMarketplaceMentor(participantId, mentorId, profileId);
        if (!response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException(response.getMessage() != null ? response.getMessage() : "Failed to assign mentor");
        }

        markAssigned(workspace, CompanyProgramMatchWorkspace.ResolverType.EMPLOYEE, profileId);
        workspaceRepository.save(workspace);
        journeyInstanceService.refreshJourneyForParticipant(participantId);
        return response.getData();
    }

    private JourneyInstanceStep resolveJourneyInstanceStep(UUID participantId, UUID journeyInstanceStepId) {
        if (journeyInstanceStepId == null) {
            return null;
        }

        JourneyInstanceStep step = journeyInstanceStepRepository.findById(journeyInstanceStepId)
                .orElseThrow(() -> new NoSuchElementException("Journey step not found"));
        CompanyProgramParticipant stepParticipant = step.getJourneyInstance() != null
                ? step.getJourneyInstance().getParticipant()
                : null;
        if (stepParticipant == null || !participantId.equals(stepParticipant.getId())) {
            throw new IllegalStateException("Journey step does not belong to this company program participant");
        }
        boolean waitingForMentor = step.getStatus() == JourneyInstanceStep.StepStatus.BLOCKED
                && step.getBlockedReason() != null
                && "Waiting for mentor assignment".equalsIgnoreCase(step.getBlockedReason().trim());
        if (step.getStatus() != JourneyInstanceStep.StepStatus.READY && !waitingForMentor) {
            throw new IllegalStateException("Mentor selection is only available for ready journey steps");
        }
        if (step.getJourneyStep() == null || step.getJourneyStep().getStepType() != JourneyStep.StepType.SESSION) {
            throw new IllegalStateException("Mentor selection is only available for session journey steps");
        }
        return step;
    }

    public int processExpiredEmployeeSelections() {
        List<CompanyProgramMatchWorkspace> expired = workspaceRepository.findByStatusAndSelectionDeadlineAtBeforeOrderBySelectionDeadlineAtAsc(
                CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION,
                LocalDateTime.now(),
                PageRequest.of(0, Math.max(expiryBatchSize, 1))
        );

        int autoAssigned = 0;
        for (CompanyProgramMatchWorkspace workspace : expired) {
            try {
                CompanyProgramParticipant participant = workspace.getParticipant();
                if (participant == null || participant.getId() == null) {
                    continue;
                }

                CompanyProgramMatchWorkspace synced = syncWorkspaceForParticipant(participant, false);
                if (synced.getStatus() != CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION) {
                    continue;
                }

                MentorAssignmentSummaryDto assignment = tryAssignFromWorkspaceOptions(
                        participant,
                        synced,
                        null,
                        CompanyProgramMatchWorkspace.ResolverType.SYSTEM
                );
                if (assignment != null) {
                    autoAssigned += 1;
                }
            } catch (Exception error) {
                log.error("Failed processing expired employee selection for workspace {}: {}",
                        workspace.getId(), error.getMessage(), error);
            }
        }

        return autoAssigned;
    }

    private CompanyProgramMatchWorkspace syncWorkspaceForParticipant(CompanyProgramParticipant participant, boolean resetDeadline) {
        if (participant == null || participant.getId() == null) {
            throw new IllegalArgumentException("Participant context is required");
        }

        CompanyProgramMatchWorkspace workspace = workspaceRepository.findByParticipant_Id(participant.getId())
                .orElseGet(() -> {
                    CompanyProgramMatchWorkspace created = new CompanyProgramMatchWorkspace();
                    created.setParticipant(participant);
                    return created;
                });

        CompanyProgram companyProgram = participant.getCompanyProgram();
        CompanyProgramMentorAssignment assignment = assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participant.getId()).orElse(null);

        if (assignment != null) {
            markAssigned(workspace, resolveResolverType(assignment, participant), assignment.getAssignedByUserId());
            return workspaceRepository.save(workspace);
        }

        CohortGateStatusDto gateStatus = resolveGateStatus(participant);
        if (isGateBlocked(gateStatus)) {
            markInactive(workspace);
            return workspaceRepository.save(workspace);
        }

        boolean programActiveForMatching = companyProgram != null && MATCHING_PROGRAM_STATUSES.contains(companyProgram.getStatus());
        boolean participantSelectable = SELECTABLE_PARTICIPANT_STATUSES.contains(participant.getStatus());
        if (!programActiveForMatching || !participantSelectable) {
            markInactive(workspace);
            return workspaceRepository.save(workspace);
        }

        List<CompanyProgramMentorCandidateDto> shortlist = buildShortlist(companyProgram);
        replaceWorkspaceOptions(workspace, shortlist);

        CompanyProgram.MatchingMode matchingMode = companyProgram.getMatchingMode();
        if (matchingMode == CompanyProgram.MatchingMode.ADMIN_ASSIGN) {
            workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.ADMIN_REVIEW);
            workspace.setSelectionDeadlineAt(null);
            workspace.setResolvedAt(null);
            workspace.setResolvedBy(null);
            workspace.setResolvedByUserId(null);
        } else if (matchingMode == CompanyProgram.MatchingMode.EMPLOYEE_SELECT) {
            workspace.setStatus(shortlist.isEmpty()
                    ? CompanyProgramMatchWorkspace.MatchStatus.EXPIRED_NO_CANDIDATE
                    : CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION);
            workspace.setSelectionDeadlineAt(shortlist.isEmpty()
                    ? null
                    : resolveSelectionDeadline(workspace.getSelectionDeadlineAt(), resetDeadline, companyProgram));
            workspace.setResolvedAt(null);
            workspace.setResolvedBy(null);
            workspace.setResolvedByUserId(null);
        } else {
            workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.ADMIN_REVIEW);
            workspace.setSelectionDeadlineAt(null);
            workspace.setResolvedAt(null);
            workspace.setResolvedBy(null);
            workspace.setResolvedByUserId(null);
            MentorAssignmentSummaryDto assigned = tryAssignFromWorkspaceOptions(
                    participant,
                    workspace,
                    null,
                    CompanyProgramMatchWorkspace.ResolverType.SYSTEM
            );
            if (assigned == null && workspace.getStatus() != CompanyProgramMatchWorkspace.MatchStatus.ASSIGNED) {
                workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.EXPIRED_NO_CANDIDATE);
            }
        }

        return workspaceRepository.save(workspace);
    }

    private MentorAssignmentSummaryDto tryAssignFromWorkspaceOptions(CompanyProgramParticipant participant,
                                                                     CompanyProgramMatchWorkspace workspace,
                                                                     UUID resolvedByUserId,
                                                                     CompanyProgramMatchWorkspace.ResolverType resolverType) {
        if (participant == null || participant.getId() == null || workspace == null || workspace.getId() == null) {
            return null;
        }

        List<CompanyProgramMatchOption> options = optionRepository.findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(workspace.getId());
        if (options.isEmpty()) {
            List<CompanyProgramMentorCandidateDto> shortlist = buildShortlist(participant.getCompanyProgram());
            replaceWorkspaceOptions(workspace, shortlist);
            workspaceRepository.save(workspace);
            options = optionRepository.findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(workspace.getId());
        }

        for (CompanyProgramMatchOption option : options) {
            UUID mentorId = option.getMentor() != null ? option.getMentor().getId() : null;
            if (mentorId == null) {
                continue;
            }

            ApiResponse<MentorAssignmentSummaryDto> response =
                    mentorAssignmentService.assignMentor(participant.getId(), mentorId, resolvedByUserId);
            if (response.isSuccess() && response.getData() != null) {
                markAssigned(workspace, resolverType, resolvedByUserId);
                workspaceRepository.save(workspace);
                journeyInstanceService.refreshJourneyForParticipant(participant.getId());
                return response.getData();
            }
        }

        workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.EXPIRED_NO_CANDIDATE);
        workspace.setSelectionDeadlineAt(null);
        workspace.setResolvedAt(null);
        workspace.setResolvedBy(null);
        workspace.setResolvedByUserId(null);
        workspaceRepository.save(workspace);
        return null;
    }

    private List<CompanyProgramMentorCandidateDto> buildShortlist(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null) {
            return List.of();
        }

        int effectiveShortlistSize = resolveShortlistSize(companyProgram);
        return mentorAssignmentService.getMentorCandidates(companyProgram.getId(), null).stream()
                .sorted(Comparator
                        .comparing((CompanyProgramMentorCandidateDto candidate) -> candidate.getRating() == null
                                ? BigDecimal.ZERO
                                : candidate.getRating())
                        .reversed()
                        .thenComparing(candidate -> Optional.ofNullable(candidate.getTotalSessions()).orElse(0), Comparator.reverseOrder())
                        .thenComparing(CompanyProgramMentorCandidateDto::getMentorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(effectiveShortlistSize)
                .toList();
    }

    private void replaceWorkspaceOptions(CompanyProgramMatchWorkspace workspace, List<CompanyProgramMentorCandidateDto> shortlist) {
        if (workspace.getId() != null) {
            optionRepository.deleteByWorkspace_Id(workspace.getId());
        }

        workspace.setShortlistGeneratedAt(LocalDateTime.now());
        workspace.setOptions(new ArrayList<>());
        if (shortlist == null || shortlist.isEmpty()) {
            return;
        }

        List<CompanyProgramMatchOption> options = new ArrayList<>();
        for (int index = 0; index < shortlist.size(); index++) {
            CompanyProgramMentorCandidateDto candidate = shortlist.get(index);
            if (candidate.getMentorId() == null) {
                continue;
            }

            Profile mentor = profileRepository.findById(candidate.getMentorId()).orElse(null);
            if (mentor == null) {
                continue;
            }

            CompanyProgramMatchOption option = new CompanyProgramMatchOption();
            option.setWorkspace(workspace);
            option.setMentor(mentor);
            option.setRankOrder(index + 1);
            option.setRecommendationScore(computeRecommendationScore(candidate, index, shortlist.size()));
            option.setRecommendationReason(buildRecommendationReason(candidate));
            option.setActive(true);
            options.add(option);
        }

        workspace.setOptions(options);
    }

    private LocalDateTime resolveSelectionDeadline(LocalDateTime currentDeadline,
                                                   boolean resetDeadline,
                                                   CompanyProgram companyProgram) {
        if (!resetDeadline && currentDeadline != null && currentDeadline.isAfter(LocalDateTime.now())) {
            return currentDeadline;
        }

        return LocalDateTime.now().plusHours(resolveSelectionWindowHours(companyProgram));
    }

    private MatchWorkspaceSummaryDto toSummary(CompanyProgramMatchWorkspace workspace, Integer shortlistCount, boolean hasAssignment) {
        if (workspace == null) {
            return null;
        }

        CompanyProgramParticipant participant = workspace.getParticipant();
        CompanyProgram companyProgram = participant != null ? participant.getCompanyProgram() : null;
        CohortGateStatusDto gateStatus = resolveGateStatus(participant);
        if (!hasAssignment && isGateBlocked(gateStatus)) {
            return blockedSummary(gateStatus);
        }

        boolean selectionWindowExpired = workspace.getSelectionDeadlineAt() != null
                && workspace.getSelectionDeadlineAt().isBefore(LocalDateTime.now())
                && !hasAssignment;

        boolean canEmployeeSelect = companyProgram != null
                && companyProgram.getMatchingMode() == CompanyProgram.MatchingMode.EMPLOYEE_SELECT
                && workspace.getStatus() == CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION
                && !selectionWindowExpired
                && participant != null
                && SELECTABLE_PARTICIPANT_STATUSES.contains(participant.getStatus())
                && MATCHING_PROGRAM_STATUSES.contains(companyProgram.getStatus())
                && !hasAssignment;

        return MatchWorkspaceSummaryDto.builder()
                .status(workspace.getStatus())
                .selectionDeadlineAt(workspace.getSelectionDeadlineAt())
                .shortlistGeneratedAt(workspace.getShortlistGeneratedAt())
                .resolvedAt(workspace.getResolvedAt())
                .resolvedBy(workspace.getResolvedBy())
                .shortlistCount(shortlistCount != null ? shortlistCount : 0)
                .canEmployeeSelect(canEmployeeSelect)
                .selectionWindowExpired(selectionWindowExpired)
                .build();
    }

    private MatchWorkspaceSummaryDto buildDerivedSummary(CompanyProgramParticipant participant, boolean hasAssignment) {
        if (participant == null || participant.getCompanyProgram() == null) {
            return null;
        }
        CohortGateStatusDto gateStatus = resolveGateStatus(participant);
        if (!hasAssignment && isGateBlocked(gateStatus)) {
            return blockedSummary(gateStatus);
        }

        CompanyProgram.MatchingMode matchingMode = participant.getCompanyProgram().getMatchingMode();
        CompanyProgramMatchWorkspace.MatchStatus status = hasAssignment
                ? CompanyProgramMatchWorkspace.MatchStatus.ASSIGNED
                : matchingMode == CompanyProgram.MatchingMode.EMPLOYEE_SELECT
                ? CompanyProgramMatchWorkspace.MatchStatus.PENDING_EMPLOYEE_SELECTION
                : CompanyProgramMatchWorkspace.MatchStatus.ADMIN_REVIEW;

        return MatchWorkspaceSummaryDto.builder()
                .status(status)
                .selectionDeadlineAt(null)
                .shortlistGeneratedAt(null)
                .resolvedAt(null)
                .resolvedBy(null)
                .shortlistCount(0)
                .canEmployeeSelect(false)
                .selectionWindowExpired(false)
                .build();
    }

    private MatchWorkspaceSummaryDto blockedSummary(CohortGateStatusDto gateStatus) {
        return MatchWorkspaceSummaryDto.builder()
                .status(CompanyProgramMatchWorkspace.MatchStatus.INACTIVE)
                .shortlistCount(0)
                .canEmployeeSelect(false)
                .selectionWindowExpired(false)
                .blockedReason(gateStatus != null ? gateStatus.getBlockedReason() : null)
                .build();
    }

    private void markInactive(CompanyProgramMatchWorkspace workspace) {
        workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.INACTIVE);
        workspace.setSelectionDeadlineAt(null);
        workspace.setResolvedAt(null);
        workspace.setResolvedBy(null);
        workspace.setResolvedByUserId(null);
        if (workspace.getId() != null) {
            optionRepository.deleteByWorkspace_Id(workspace.getId());
        }
    }

    private void ensureCohortGateAllowsMatching(CompanyProgramParticipant participant) {
        CohortGateStatusDto gateStatus = resolveGateStatus(participant);
        if (isGateBlocked(gateStatus)) {
            throw new IllegalStateException("Mentor matching is blocked: " + gateStatus.getBlockedReason());
        }
    }

    private CohortGateStatusDto resolveGateStatus(CompanyProgramParticipant participant) {
        if (participant == null || participant.getId() == null) {
            return null;
        }
        return cohortGateService.resolveGateStatusForProgramParticipant(participant.getId());
    }

    private boolean isGateBlocked(CohortGateStatusDto gateStatus) {
        return gateStatus != null && !gateStatus.isEligibleForMatching();
    }

    private List<CompanyProgramMentorCandidateDto> enrichOptions(CompanyProgram companyProgram, List<CompanyProgramMatchOption> options) {
        Map<UUID, CompanyProgramMentorCandidateDto> candidateByMentorId = mentorAssignmentService.getMentorCandidates(
                        companyProgram.getId(),
                        null
                ).stream()
                .filter(candidate -> candidate.getMentorId() != null)
                .collect(Collectors.toMap(
                        CompanyProgramMentorCandidateDto::getMentorId,
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<CompanyProgramMentorCandidateDto> enriched = new ArrayList<>();
        for (CompanyProgramMatchOption option : options) {
            UUID mentorId = option.getMentor() != null ? option.getMentor().getId() : null;
            if (mentorId == null) {
                continue;
            }

            CompanyProgramMentorCandidateDto candidate = candidateByMentorId.get(mentorId);
            if (candidate == null) {
                Profile mentor = profileRepository.findById(mentorId).orElse(null);
                candidate = CompanyProgramMentorCandidateDto.builder()
                        .mentorId(mentorId)
                        .mentorName(buildDisplayName(mentor))
                        .mentorEmail(mentor != null ? mentor.getEmail() : null)
                        .source("PROGRAM_POOL")
                        .isAvailable(false)
                        .build();
            }

            candidate.setRankOrder(option.getRankOrder());
            candidate.setRecommendationScore(option.getRecommendationScore());
            candidate.setRecommendationReason(option.getRecommendationReason());
            enriched.add(candidate);
        }

        return enriched;
    }

    private void ensureParticipantOwnership(CompanyProgramParticipant participant, UUID profileId) {
        if (participant == null || participant.getProfile() == null || profileId == null
                || !profileId.equals(participant.getProfile().getId())) {
            throw new SecurityException("Not authorized to manage mentor matching for this participant");
        }
    }

    private void markAssigned(CompanyProgramMatchWorkspace workspace,
                              CompanyProgramMatchWorkspace.ResolverType resolverType,
                              UUID resolverUserId) {
        workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.ASSIGNED);
        workspace.setSelectionDeadlineAt(null);
        workspace.setResolvedAt(LocalDateTime.now());
        workspace.setResolvedBy(resolverType);
        workspace.setResolvedByUserId(resolverUserId);
    }

    private CompanyProgramMatchWorkspace.ResolverType resolveResolverType(CompanyProgramMentorAssignment assignment,
                                                                          CompanyProgramParticipant participant) {
        if (assignment == null) {
            return CompanyProgramMatchWorkspace.ResolverType.ADMIN;
        }

        UUID assignedBy = assignment.getAssignedByUserId();
        UUID participantProfileId = participant != null && participant.getProfile() != null
                ? participant.getProfile().getId()
                : null;

        if (assignedBy != null && participantProfileId != null && assignedBy.equals(participantProfileId)) {
            return CompanyProgramMatchWorkspace.ResolverType.EMPLOYEE;
        }

        return assignedBy == null
                ? CompanyProgramMatchWorkspace.ResolverType.SYSTEM
                : CompanyProgramMatchWorkspace.ResolverType.ADMIN;
    }

    private Map<UUID, Integer> resolveShortlistCounts(Collection<UUID> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return Map.of();
        }

        return optionRepository.countActiveByWorkspaceIds(workspaceIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }

    private int resolveShortlistCount(UUID workspaceId) {
        if (workspaceId == null) {
            return 0;
        }
        return (int) optionRepository.countByWorkspace_IdAndActiveTrue(workspaceId);
    }

    private BigDecimal computeRecommendationScore(CompanyProgramMentorCandidateDto candidate,
                                                  int index,
                                                  int shortlistCount) {
        BigDecimal rating = candidate.getRating() != null ? candidate.getRating() : BigDecimal.ZERO;
        BigDecimal normalizedRating = rating.multiply(BigDecimal.valueOf(20));
        BigDecimal sessions = BigDecimal.valueOf(Math.min(Optional.ofNullable(candidate.getTotalSessions()).orElse(0), 100));
        BigDecimal rankBonus = BigDecimal.valueOf(Math.max(0, Math.max(shortlistCount, 1) - index));
        return normalizedRating
                .add(sessions)
                .add(rankBonus)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveShortlistSize(CompanyProgram companyProgram) {
        int programValue = companyProgram != null && companyProgram.getEmployeeSelectionShortlistSize() != null
                ? companyProgram.getEmployeeSelectionShortlistSize()
                : shortlistSize;
        return Math.max(programValue, 1);
    }

    private int resolveSelectionWindowHours(CompanyProgram companyProgram) {
        int programValue = companyProgram != null && companyProgram.getEmployeeSelectionWindowHours() != null
                ? companyProgram.getEmployeeSelectionWindowHours()
                : employeeSelectionWindowHours;
        return Math.max(programValue, 1);
    }

    private String buildRecommendationReason(CompanyProgramMentorCandidateDto candidate) {
        List<String> segments = new ArrayList<>();
        if (candidate.getRating() != null) {
            segments.add("Rating " + candidate.getRating());
        }
        if (candidate.getTotalSessions() != null) {
            segments.add(candidate.getTotalSessions() + " completed sessions");
        }
        if (candidate.getSpecializations() != null && !candidate.getSpecializations().isEmpty()) {
            segments.add("Specializations: " + String.join(", ", candidate.getSpecializations().stream()
                    .filter(Objects::nonNull)
                    .limit(2)
                    .toList()));
        }
        if (segments.isEmpty()) {
            return "Candidate is available in the program mentor pool.";
        }
        return String.join(" | ", segments);
    }

    private String buildDisplayName(Profile profile) {
        if (profile == null) {
            return "Mentor";
        }

        String fullName = String.join(" ",
                Optional.ofNullable(profile.getFirstName()).orElse("").trim(),
                Optional.ofNullable(profile.getLastName()).orElse("").trim()
        ).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }

        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername().trim();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        return "Mentor";
    }
}
