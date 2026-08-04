package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.EmployeeCompanyProgramJourneyDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.Program;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionOutcome;
import com.prosper.prospermentor.entity.SessionOutcomeActionItem;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.SessionOutcomeActionItemRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.service.support.CompanyProgramCatalogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramJourneyService {

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> VISIBLE_EMPLOYEE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final SessionRepository sessionRepository;
    private final SessionOutcomeRepository sessionOutcomeRepository;
    private final SessionOutcomeActionItemRepository sessionOutcomeActionItemRepository;
    private final CompanyProgramMentorAssignmentService mentorAssignmentService;
    private final JourneyInstanceService journeyInstanceService;
    private final EmployeeSessionAllocationService employeeSessionAllocationService;

    @Transactional(readOnly = true)
    public List<EmployeeCompanyProgramJourneyDto> getJourneysForProfile(UUID profileId) {
        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByProfileIdAndStatusIn(
                profileId,
                VISIBLE_EMPLOYEE_STATUSES
        );

        if (participants.isEmpty()) {
            return List.of();
        }

        List<UUID> participantIds = participants.stream()
                .map(CompanyProgramParticipant::getId)
                .toList();

        List<Session> sessions = sessionRepository.findByCompanyProgramParticipantIdInOrderByScheduledStartDesc(participantIds);
        Map<UUID, List<Session>> sessionsByParticipantId = sessions.stream()
                .filter(session -> session.getCompanyProgramParticipantId() != null)
                .collect(Collectors.groupingBy(
                        Session::getCompanyProgramParticipantId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<UUID, SessionOutcome> outcomesBySessionId = loadOutcomesBySessionId(sessions);
        Map<UUID, MentorAssignmentSummaryDto> assignmentsByParticipantId =
                mentorAssignmentService.getAssignmentSummaries(participantIds);
        Map<UUID, JourneyInstance> journeyInstancesByParticipantId = journeyInstanceService.getInstancesByParticipantIds(participantIds);
        Map<UUID, List<JourneyInstanceStep>> journeyStepsByInstanceId = journeyInstanceService.getStepsByInstanceIds(
                journeyInstancesByParticipantId.values().stream()
                        .map(JourneyInstance::getId)
                        .toList()
        );
        List<UUID> journeyInstanceStepIds = journeyStepsByInstanceId.values().stream()
                .flatMap(Collection::stream)
                .map(JourneyInstanceStep::getId)
                .toList();
        Map<UUID, MentorAssignmentSummaryDto> assignmentsByJourneyInstanceStepId =
                mentorAssignmentService.getStepAssignmentSummaries(participantIds, journeyInstanceStepIds);
        EmployeeSessionAllocation sessionAllocation = findSessionAllocation(profileId);

        return participants.stream()
                .sorted(Comparator
                        .comparingInt((CompanyProgramParticipant participant) -> participantStatusPriority(participant.getStatus()))
                        .thenComparing(participant -> {
                            CompanyProgram companyProgram = participant.getCompanyProgram();
                            return companyProgram != null && companyProgram.getStartsAt() != null
                                    ? companyProgram.getStartsAt()
                                    : participant.getEnrolledAt();
                        }, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompanyProgramParticipant::getEnrolledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(participant -> {
                    JourneyInstance journeyInstance = journeyInstancesByParticipantId.get(participant.getId());
                    UUID journeyInstanceId = journeyInstance != null ? journeyInstance.getId() : null;
                    List<JourneyInstanceStep> journeySteps = journeyInstanceId != null
                            ? journeyStepsByInstanceId.getOrDefault(journeyInstanceId, List.of())
                            : List.of();

                    return toJourneyDto(
                            participant,
                            assignmentsByParticipantId.get(participant.getId()),
                            journeyInstance,
                            journeySteps,
                            assignmentsByJourneyInstanceStepId,
                            sessionsByParticipantId.getOrDefault(participant.getId(), List.of()),
                            outcomesBySessionId,
                            sessionAllocation
                    );
                })
                .toList();
    }

    public EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto completeJourneyStep(UUID journeyInstanceStepId,
                                                                                        UUID profileId) {
        JourneyInstanceStep step = journeyInstanceService.completeEmployeeJourneyStep(journeyInstanceStepId, profileId);
        return toJourneyStepDto(step);
    }

    public EmployeeCompanyProgramJourneyDto.JourneyActionItemDto updateActionItemCompletion(UUID actionItemId,
                                                                                             UUID profileId,
                                                                                             boolean completed) {
        SessionOutcomeActionItem actionItem = sessionOutcomeActionItemRepository.findByIdWithSessionContext(actionItemId)
                .orElseThrow(() -> new NoSuchElementException("Journey action item not found"));

        Session session = actionItem.getSessionOutcome() != null ? actionItem.getSessionOutcome().getSession() : null;
        CompanyProgramParticipant participant = session != null ? session.getCompanyProgramParticipant() : null;

        if (participant == null || participant.getProfile() == null) {
            throw new IllegalStateException("Action item is not linked to an employee company-program journey");
        }

        if (!participant.getProfile().getId().equals(profileId)) {
            throw new SecurityException("Not authorized to update this action item");
        }

        if (actionItem.getOwnerType() == SessionOutcomeActionItem.ActionItemOwnerType.MENTOR) {
            throw new IllegalStateException("Mentor-owned action items cannot be updated by the employee");
        }

        if (completed) {
            actionItem.markCompleted(profileId);
        } else {
            actionItem.reopen();
        }

        SessionOutcomeActionItem saved = sessionOutcomeActionItemRepository.save(actionItem);
        return toJourneyActionItemDto(session, saved);
    }

    private Map<UUID, SessionOutcome> loadOutcomesBySessionId(List<Session> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }

        List<UUID> sessionIds = sessions.stream()
                .map(Session::getId)
                .toList();

        return sessionOutcomeRepository.findDetailedBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        outcome -> outcome.getSession().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private EmployeeCompanyProgramJourneyDto toJourneyDto(CompanyProgramParticipant participant,
                                                          MentorAssignmentSummaryDto mentorAssignment,
                                                          JourneyInstance journeyInstance,
                                                          List<JourneyInstanceStep> journeyInstanceSteps,
                                                          Map<UUID, MentorAssignmentSummaryDto> assignmentsByJourneyInstanceStepId,
                                                          List<Session> sessions,
                                                          Map<UUID, SessionOutcome> outcomesBySessionId,
                                                          EmployeeSessionAllocation sessionAllocation) {
        List<Session> sortedSessions = new ArrayList<>(sessions);
        sortedSessions.sort(Comparator.comparing(Session::getScheduledStart, Comparator.nullsLast(Comparator.reverseOrder())));

        int totalSessions = sortedSessions.size();
        int completedSessions = (int) sortedSessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .count();

        List<EmployeeCompanyProgramJourneyDto.JourneyActionItemDto> actionItems = sortedSessions.stream()
                .map(session -> outcomesBySessionId.get(session.getId()))
                .filter(java.util.Objects::nonNull)
                .flatMap(outcome -> outcome.getActionItems().stream()
                        .map(actionItem -> toJourneyActionItemDto(outcome.getSession(), actionItem)))
                .sorted(Comparator
                        .comparing(EmployeeCompanyProgramJourneyDto.JourneyActionItemDto::isCompleted)
                        .thenComparing(EmployeeCompanyProgramJourneyDto.JourneyActionItemDto::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EmployeeCompanyProgramJourneyDto.JourneyActionItemDto::getDescription, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        int openActionItemCount = (int) actionItems.stream()
                .filter(actionItem -> !actionItem.isCompleted())
                .count();

        int completedActionItemCount = actionItems.size() - openActionItemCount;

        Session nextSessionEntity = sortedSessions.stream()
                .filter(session -> session.getScheduledStart() != null)
                .filter(session -> session.getScheduledStart().isAfter(ZonedDateTime.now()))
                .filter(session -> session.getStatus() == Session.SessionStatus.PENDING
                        || session.getStatus() == Session.SessionStatus.CONFIRMED
                        || session.getStatus() == Session.SessionStatus.SCHEDULED
                        || session.getStatus() == Session.SessionStatus.IN_PROGRESS)
                .min(Comparator.comparing(Session::getScheduledStart))
                .orElse(null);

        SessionOutcome latestOutcome = sortedSessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .map(session -> outcomesBySessionId.get(session.getId()))
                .filter(java.util.Objects::nonNull)
                .filter(outcome -> hasText(outcome.getSummary()) || hasText(outcome.getReflectionPrompt()) || !outcome.getActionItems().isEmpty())
                .findFirst()
                .orElse(null);

        List<JourneyInstanceStep> sortedJourneySteps = new ArrayList<>(journeyInstanceSteps);
        sortedJourneySteps.sort(Comparator.comparing(
                step -> step.getJourneyStep().getDefaultSequence(),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        List<EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto> stepDtos = sortedJourneySteps.stream()
                .map(step -> toJourneyStepDto(
                        step,
                        participant,
                        mentorAssignment,
                        assignmentsByJourneyInstanceStepId.get(step.getId()),
                        sessionAllocation
                ))
                .toList();

        int totalJourneySteps = stepDtos.size();
        int completedJourneySteps = (int) stepDtos.stream()
                .filter(step -> step.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED
                        || step.getStatus() == JourneyInstanceStep.StepStatus.SKIPPED)
                .count();
        int readyJourneySteps = (int) stepDtos.stream()
                .filter(step -> step.getStatus() == JourneyInstanceStep.StepStatus.READY)
                .count();

        int progressPercent = journeyInstance != null
                ? journeyInstance.getProgressPercent()
                : totalSessions == 0
                ? 0
                : (int) Math.round((completedSessions * 100.0) / totalSessions);

        CompanyProgram companyProgram = participant.getCompanyProgram();
        Program anchorProgram = CompanyProgramCatalogSupport.anchorProgram(companyProgram);
        SessionOutcome nextSessionOutcome = nextSessionEntity != null
                ? outcomesBySessionId.get(nextSessionEntity.getId())
                : null;

        return EmployeeCompanyProgramJourneyDto.builder()
                .participantId(participant.getId())
                .companyProgramId(companyProgram.getId())
                .companyId(companyProgram.getCompany() != null ? companyProgram.getCompany().getId() : null)
                .companyName(companyProgram.getCompany() != null ? companyProgram.getCompany().getName() : null)
                .programName(companyProgram.getName())
                .templateProgramName(anchorProgram != null ? anchorProgram.getName() : null)
                .catalogJourneySummary(CompanyProgramCatalogSupport.buildJourneySummary(companyProgram))
                .catalogStages(CompanyProgramCatalogSupport.toStageDtos(companyProgram))
                .journeyTemplateId(companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getId() : null)
                .journeyTemplateName(companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getName() : null)
                .journeyInstanceId(journeyInstance != null ? journeyInstance.getId() : null)
                .journeyStatus(journeyInstance != null ? journeyInstance.getStatus() : null)
                .programStatus(companyProgram.getStatus())
                .participantStatus(participant.getStatus())
                .mentorAssignment(mentorAssignment)
                .totalSessions(totalSessions)
                .completedSessions(completedSessions)
                .totalJourneySteps(totalJourneySteps)
                .completedJourneySteps(completedJourneySteps)
                .readyJourneySteps(readyJourneySteps)
                .openActionItemCount(openActionItemCount)
                .completedActionItemCount(completedActionItemCount)
                .progressPercent(progressPercent)
                .latestOutcomeSummary(latestOutcome != null ? latestOutcome.getSummary() : null)
                .latestReflectionPrompt(latestOutcome != null ? latestOutcome.getReflectionPrompt() : null)
                .nextSession(toJourneySessionDto(nextSessionEntity, nextSessionOutcome))
                .recentSessions(sortedSessions.stream()
                        .limit(3)
                        .map(session -> toJourneySessionDto(session, outcomesBySessionId.get(session.getId())))
                        .toList())
                .steps(stepDtos)
                .actionItems(actionItems)
                .build();
    }

    private EmployeeCompanyProgramJourneyDto.JourneySessionDto toJourneySessionDto(Session session,
                                                                                   SessionOutcome outcome) {
        if (session == null) {
            return null;
        }

        return EmployeeCompanyProgramJourneyDto.JourneySessionDto.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .scheduledStart(session.getScheduledStart())
                .scheduledEnd(session.getScheduledEnd())
                .status(session.getStatus())
                .outcomeSummary(outcome != null ? outcome.getSummary() : null)
                .reflectionPrompt(outcome != null ? outcome.getReflectionPrompt() : null)
                .actionItemCount(outcome != null ? outcome.getActionItems().size() : 0)
                .build();
    }

    private EmployeeCompanyProgramJourneyDto.JourneyActionItemDto toJourneyActionItemDto(Session session,
                                                                                         SessionOutcomeActionItem actionItem) {
        return EmployeeCompanyProgramJourneyDto.JourneyActionItemDto.builder()
                .actionItemId(actionItem.getId())
                .sessionId(session != null ? session.getId() : null)
                .sessionTitle(session != null ? session.getTitle() : null)
                .description(actionItem.getDescription())
                .ownerType(actionItem.getOwnerType())
                .dueAt(actionItem.getDueAt())
                .completedAt(actionItem.getCompletedAt())
                .completed(actionItem.isCompleted())
                .canBeCompletedByEmployee(actionItem.getOwnerType() != SessionOutcomeActionItem.ActionItemOwnerType.MENTOR)
                .build();
    }

    private EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto toJourneyStepDto(JourneyInstanceStep step) {
        return toJourneyStepDto(step, null, null, null, null);
    }

    private EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto toJourneyStepDto(JourneyInstanceStep step,
                                                                                     CompanyProgramParticipant participant,
                                                                                     MentorAssignmentSummaryDto mentorAssignment,
                                                                                     MentorAssignmentSummaryDto stepMentorAssignment,
                                                                                     EmployeeSessionAllocation sessionAllocation) {
        JourneyStep journeyStep = step.getJourneyStep();
        MentorAssignmentSummaryDto effectiveMentorAssignment = stepMentorAssignment != null
                ? stepMentorAssignment
                : mentorAssignment;
        return EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto.builder()
                .journeyInstanceStepId(step.getId())
                .journeyStepId(journeyStep != null ? journeyStep.getId() : null)
                .stepKey(journeyStep != null ? journeyStep.getStepKey() : null)
                .defaultSequence(journeyStep != null ? journeyStep.getDefaultSequence() : null)
                .title(journeyStep != null ? journeyStep.getTitle() : null)
                .description(journeyStep != null ? journeyStep.getDescription() : null)
                .stepType(journeyStep != null ? journeyStep.getStepType() : null)
                .required(journeyStep != null && Boolean.TRUE.equals(journeyStep.getRequired()))
                .status(step.getStatus())
                .dueAt(step.getDueAt())
                .completedAt(step.getCompletedAt())
                .skippedReason(step.getSkippedReason())
                .blockedReason(step.getBlockedReason())
                .canBeCompletedByEmployee(journeyInstanceService.canBeCompletedByEmployee(journeyStep))
                .mentorAssignment(stepMentorAssignment)
                .primaryAction(resolveJourneyStepAction(step, participant, effectiveMentorAssignment, sessionAllocation))
                .build();
    }

    private EmployeeCompanyProgramJourneyDto.JourneyStepActionDto resolveJourneyStepAction(JourneyInstanceStep step,
                                                                                           CompanyProgramParticipant participant,
                                                                                           MentorAssignmentSummaryDto mentorAssignment,
                                                                                           EmployeeSessionAllocation sessionAllocation) {
        if (step == null || step.getJourneyStep() == null) {
            return null;
        }

        JourneyStep journeyStep = step.getJourneyStep();
        JourneyInstanceStep.StepStatus status = step.getStatus();

        if (status == JourneyInstanceStep.StepStatus.COMPLETED || status == JourneyInstanceStep.StepStatus.SKIPPED) {
            if (journeyStep.getStepType() == JourneyStep.StepType.SESSION) {
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.VIEW_SESSIONS)
                        .label("View sessions")
                        .description("Review your completed and upcoming sessions.")
                        .enabled(true)
                        .targetRoute("/app/sessions")
                        .build();
            }
            return null;
        }

        if (status != JourneyInstanceStep.StepStatus.READY) {
            if (isWaitingForMentorAssignment(step)) {
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.VIEW_MATCHES)
                        .label("Choose mentor")
                        .description("Choose or review your mentor match before booking this session.")
                        .enabled(true)
                        .targetRoute("/app/employee/matches")
                        .build();
            }

            String waitReason = hasText(step.getBlockedReason())
                    ? step.getBlockedReason()
                    : "Complete earlier milestones before this action unlocks.";
            return baseAction(step, participant, mentorAssignment, sessionAllocation)
                    .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.WAIT)
                    .label("Waiting")
                    .description(waitReason)
                    .enabled(false)
                    .disabledReason(waitReason)
                    .build();
        }

        if (journeyStep.getStepType() == JourneyStep.StepType.SESSION) {
            UUID mentorId = mentorAssignment != null ? mentorAssignment.getMentorId() : null;

            if (mentorId == null) {
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.VIEW_MATCHES)
                        .label("Choose mentor")
                        .description("Select or review your assigned mentor before booking this session.")
                        .enabled(true)
                        .targetRoute("/app/employee/matches")
                        .build();
            }

            if (!isLiveCompanyProgram(participant)) {
                String disabledReason = "Sessions can be booked after this company program is live.";
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.WAIT)
                        .label("Program not live")
                        .description(disabledReason)
                        .enabled(false)
                        .disabledReason(disabledReason)
                        .build();
            }

            if (!canParticipantBook(participant)) {
                String disabledReason = "Sessions can only be booked while you are enrolled or active in this program.";
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.WAIT)
                        .label("Program access unavailable")
                        .description(disabledReason)
                        .enabled(false)
                        .disabledReason(disabledReason)
                        .build();
            }

            int availableBalance = availableSessionBalance(sessionAllocation);
            if (availableBalance > 0) {
                return baseAction(step, participant, mentorAssignment, sessionAllocation)
                        .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.BOOK_SESSION)
                        .label("Book session")
                        .description(companyFundedSessionDescription(availableBalance))
                        .enabled(true)
                        .targetRoute(mentorRoute(mentorId))
                        .build();
            }

            int assignedTotal = assignedSessionTotal(sessionAllocation);
            String description = assignedTotal > 0
                    ? "Your assigned company-funded sessions are depleted. Buy a session to continue."
                    : "Buy a session to continue with your assigned mentor.";
            return baseAction(step, participant, mentorAssignment, sessionAllocation)
                    .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.BUY_SESSION)
                    .label("Buy session")
                    .description(description)
                    .enabled(true)
                    .targetRoute(mentorRoute(mentorId))
                    .build();
        }

        if (journeyInstanceService.canBeCompletedByEmployee(journeyStep)) {
            return baseAction(step, participant, mentorAssignment, sessionAllocation)
                    .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.COMPLETE_STEP)
                    .label("Mark complete")
                    .description("Confirm this journey milestone is complete.")
                    .enabled(true)
                    .build();
        }

        if (journeyStep.getStepType() == JourneyStep.StepType.SURVEY) {
            String disabledReason = "This pulse is managed by the program team.";
            return baseAction(step, participant, mentorAssignment, sessionAllocation)
                    .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.WAIT)
                    .label("Awaiting pulse")
                    .description(disabledReason)
                    .enabled(false)
                    .disabledReason(disabledReason)
                    .build();
        }

        String disabledReason = "This milestone is managed by the program team.";
        return baseAction(step, participant, mentorAssignment, sessionAllocation)
                .actionType(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.WAIT)
                .label("Managed")
                .description(disabledReason)
                .enabled(false)
                .disabledReason(disabledReason)
                .build();
    }

    private EmployeeCompanyProgramJourneyDto.JourneyStepActionDto.JourneyStepActionDtoBuilder baseAction(
            JourneyInstanceStep step,
            CompanyProgramParticipant participant,
            MentorAssignmentSummaryDto mentorAssignment,
            EmployeeSessionAllocation sessionAllocation) {
        CompanyProgram companyProgram = participant != null ? participant.getCompanyProgram() : null;
        return EmployeeCompanyProgramJourneyDto.JourneyStepActionDto.builder()
                .journeyInstanceStepId(step != null ? step.getId() : null)
                .mentorId(mentorAssignment != null ? mentorAssignment.getMentorId() : null)
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyProgramParticipantId(participant != null ? participant.getId() : null)
                .availableSessionBalance(availableSessionBalance(sessionAllocation))
                .assignedSessionTotal(assignedSessionTotal(sessionAllocation));
    }

    private EmployeeSessionAllocation findSessionAllocation(UUID profileId) {
        Optional<EmployeeSessionAllocation> allocation = employeeSessionAllocationService.findAllocationForProfile(profileId);
        return allocation != null ? allocation.orElse(null) : null;
    }

    private boolean isWaitingForMentorAssignment(JourneyInstanceStep step) {
        return hasText(step.getBlockedReason())
                && "Waiting for mentor assignment".equalsIgnoreCase(step.getBlockedReason().trim());
    }

    private boolean isLiveCompanyProgram(CompanyProgramParticipant participant) {
        return participant != null
                && participant.getCompanyProgram() != null
                && participant.getCompanyProgram().getStatus() == CompanyProgram.CompanyProgramStatus.LIVE;
    }

    private boolean canParticipantBook(CompanyProgramParticipant participant) {
        if (participant == null) {
            return false;
        }
        return participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ENROLLED
                || participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ACTIVE;
    }

    private int availableSessionBalance(EmployeeSessionAllocation allocation) {
        if (allocation == null || allocation.getAvailableBalance() == null) {
            return 0;
        }
        return Math.max(0, allocation.getAvailableBalance());
    }

    private int assignedSessionTotal(EmployeeSessionAllocation allocation) {
        if (allocation == null || allocation.getAllocatedTotal() == null) {
            return 0;
        }
        return Math.max(0, allocation.getAllocatedTotal());
    }

    private String companyFundedSessionDescription(int availableBalance) {
        return availableBalance == 1
                ? "1 company-funded session available."
                : availableBalance + " company-funded sessions available.";
    }

    private String mentorRoute(UUID mentorId) {
        return mentorId != null ? "/app/mentors/" + mentorId : null;
    }

    private int participantStatusPriority(CompanyProgramParticipant.ParticipantStatus status) {
        if (status == null) {
            return Integer.MAX_VALUE;
        }

        return switch (status) {
            case ACTIVE -> 0;
            case ENROLLED -> 1;
            case COMPLETED -> 2;
            case WITHDRAWN -> 3;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
