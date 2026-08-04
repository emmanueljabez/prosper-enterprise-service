package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.JourneyStepDependency;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.JourneyInstanceRepository;
import com.prosper.prospermentor.repository.JourneyInstanceStepRepository;
import com.prosper.prospermentor.repository.JourneyStepDependencyRepository;
import com.prosper.prospermentor.repository.JourneyStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class JourneyInstanceService {

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> JOURNEY_ELIGIBLE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final JourneyInstanceRepository journeyInstanceRepository;
    private final JourneyInstanceStepRepository journeyInstanceStepRepository;
    private final JourneyStepRepository journeyStepRepository;
    private final JourneyStepDependencyRepository journeyStepDependencyRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramMentorAssignmentRepository mentorAssignmentRepository;

    public Optional<JourneyInstance> ensureJourneyInstanceForParticipant(CompanyProgramParticipant participant) {
        if (participant == null || participant.getId() == null) {
            return Optional.empty();
        }

        CompanyProgram companyProgram = participant.getCompanyProgram();
        JourneyTemplate journeyTemplate = companyProgram != null ? companyProgram.getJourneyTemplate() : null;

        if (journeyTemplate == null || !JOURNEY_ELIGIBLE_STATUSES.contains(participant.getStatus())) {
            return Optional.empty();
        }

        Optional<JourneyInstance> existing = journeyInstanceRepository.findByParticipant_Id(participant.getId());
        if (existing.isPresent()) {
            return existing;
        }

        List<JourneyStep> templateSteps = journeyStepRepository.findByJourneyTemplate_IdOrderByDefaultSequenceAsc(journeyTemplate.getId());
        if (templateSteps.isEmpty()) {
            return Optional.empty();
        }

        JourneyInstance instance = new JourneyInstance();
        instance.setParticipant(participant);
        instance.setJourneyTemplate(journeyTemplate);
        instance.setStatus(JourneyInstance.JourneyStatus.NOT_STARTED);
        instance.setProgressPercent(0);
        instance.setStartedAt(participant.getEnrolledAt());
        JourneyInstance savedInstance = journeyInstanceRepository.save(instance);

        List<JourneyInstanceStep> instanceSteps = templateSteps.stream()
                .map(step -> buildInstanceStep(savedInstance, step))
                .toList();
        List<JourneyInstanceStep> savedSteps = journeyInstanceStepRepository.saveAll(instanceSteps);

        recalculateJourney(savedInstance, savedSteps);
        log.info("Created journey instance {} for participant {} using template {}",
                savedInstance.getId(), participant.getId(), journeyTemplate.getId());

        return Optional.of(savedInstance);
    }

    public void ensureJourneyInstancesForParticipants(Collection<CompanyProgramParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }

        for (CompanyProgramParticipant participant : participants) {
            ensureJourneyInstanceForParticipant(participant);
        }
    }

    public void ensureJourneyInstancesForProgram(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null || companyProgram.getJourneyTemplate() == null) {
            return;
        }

        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                companyProgram.getId(),
                JOURNEY_ELIGIBLE_STATUSES
        );
        ensureJourneyInstancesForParticipants(participants);
    }

    public void refreshJourneyForParticipant(UUID participantId) {
        if (participantId == null) {
            return;
        }

        JourneyInstance instance = journeyInstanceRepository.findByParticipant_Id(participantId).orElse(null);
        if (instance == null) {
            return;
        }

        List<JourneyInstanceStep> steps = journeyInstanceStepRepository.findDetailedByJourneyInstanceId(instance.getId());
        recalculateJourney(instance, steps);
    }

    public JourneyTemplateMigrationSummary migrateTemplateForNotStartedParticipants(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null || companyProgram.getJourneyTemplate() == null) {
            return new JourneyTemplateMigrationSummary(0, 0, 0);
        }

        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                companyProgram.getId(),
                JOURNEY_ELIGIBLE_STATUSES
        );
        if (participants.isEmpty()) {
            return new JourneyTemplateMigrationSummary(0, 0, 0);
        }

        List<UUID> participantIds = participants.stream().map(CompanyProgramParticipant::getId).toList();
        Map<UUID, JourneyInstance> instancesByParticipantId = getInstancesByParticipantIds(participantIds);
        Map<UUID, List<JourneyInstanceStep>> stepsByInstanceId = getStepsByInstanceIds(
                instancesByParticipantId.values().stream().map(JourneyInstance::getId).toList()
        );
        List<JourneyStep> templateSteps = journeyStepRepository.findByJourneyTemplate_IdOrderByDefaultSequenceAsc(
                companyProgram.getJourneyTemplate().getId()
        );
        if (templateSteps.isEmpty()) {
            return new JourneyTemplateMigrationSummary(0, 0, participants.size());
        }

        int migrated = 0;
        int created = 0;
        int retained = 0;

        for (CompanyProgramParticipant participant : participants) {
            JourneyInstance existingInstance = instancesByParticipantId.get(participant.getId());
            if (existingInstance == null) {
                ensureJourneyInstanceForParticipant(participant);
                created += 1;
                continue;
            }

            List<JourneyInstanceStep> existingSteps = stepsByInstanceId.getOrDefault(existingInstance.getId(), List.of());
            if (!canMigrateToProgramTemplate(existingInstance, existingSteps, companyProgram)) {
                retained += 1;
                continue;
            }

            journeyInstanceStepRepository.deleteByJourneyInstance_Id(existingInstance.getId());
            existingInstance.setJourneyTemplate(companyProgram.getJourneyTemplate());
            existingInstance.setStatus(JourneyInstance.JourneyStatus.NOT_STARTED);
            existingInstance.setProgressPercent(0);
            existingInstance.setCompletedAt(null);
            existingInstance.setStartedAt(participant.getEnrolledAt());
            JourneyInstance savedInstance = journeyInstanceRepository.save(existingInstance);

            List<JourneyInstanceStep> newSteps = templateSteps.stream()
                    .map(step -> buildInstanceStep(savedInstance, step))
                    .toList();
            List<JourneyInstanceStep> savedSteps = journeyInstanceStepRepository.saveAll(newSteps);
            recalculateJourney(savedInstance, savedSteps);
            migrated += 1;
        }

        return new JourneyTemplateMigrationSummary(migrated, created, retained);
    }

    public void refreshJourneysForProgram(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null) {
            return;
        }

        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                companyProgram.getId(),
                JOURNEY_ELIGIBLE_STATUSES
        );
        for (CompanyProgramParticipant participant : participants) {
            if (participant != null && participant.getId() != null) {
                refreshJourneyForParticipant(participant.getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, JourneyInstance> getInstancesByParticipantIds(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return Map.of();
        }

        return journeyInstanceRepository.findByParticipant_IdIn(participantIds).stream()
                .collect(Collectors.toMap(
                        instance -> instance.getParticipant().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<JourneyInstanceStep>> getStepsByInstanceIds(Collection<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Map.of();
        }

        return journeyInstanceStepRepository.findByJourneyInstance_IdIn(instanceIds).stream()
                .sorted(Comparator.comparing(step -> step.getJourneyStep().getDefaultSequence(), Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(
                        step -> step.getJourneyInstance().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public JourneyInstanceStep completeEmployeeJourneyStep(UUID journeyInstanceStepId, UUID profileId) {
        JourneyInstanceStep step = journeyInstanceStepRepository.findById(journeyInstanceStepId)
                .orElseThrow(() -> new NoSuchElementException("Journey step not found"));

        CompanyProgramParticipant participant = step.getJourneyInstance() != null
                ? step.getJourneyInstance().getParticipant()
                : null;
        if (participant == null || participant.getProfile() == null) {
            throw new IllegalStateException("Journey step is not linked to an employee journey");
        }
        if (!participant.getProfile().getId().equals(profileId)) {
            throw new SecurityException("Not authorized to update this journey step");
        }
        if (!canBeCompletedByEmployee(step.getJourneyStep())) {
            throw new IllegalStateException("This journey step is completed automatically");
        }
        if (step.getStatus() != JourneyInstanceStep.StepStatus.READY
                && step.getStatus() != JourneyInstanceStep.StepStatus.BLOCKED) {
            if (step.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED) {
                return step;
            }
            throw new IllegalStateException("This journey step is not ready to complete");
        }

        step.setStatus(JourneyInstanceStep.StepStatus.COMPLETED);
        step.setCompletedAt(ZonedDateTime.now());
        step.setBlockedReason(null);
        journeyInstanceStepRepository.save(step);

        List<JourneyInstanceStep> instanceSteps = journeyInstanceStepRepository.findDetailedByJourneyInstanceId(step.getJourneyInstance().getId());
        recalculateJourney(step.getJourneyInstance(), instanceSteps);
        return step;
    }

    public void advanceAfterSessionCompletion(Session session) {
        if (session == null || session.getCompanyProgramParticipantId() == null) {
            return;
        }

        JourneyInstance instance = journeyInstanceRepository.findByParticipant_Id(session.getCompanyProgramParticipantId())
                .orElse(null);
        if (instance == null) {
            return;
        }

        List<JourneyInstanceStep> instanceSteps = journeyInstanceStepRepository.findDetailedByJourneyInstanceId(instance.getId());
        JourneyInstanceStep nextSessionStep = instanceSteps.stream()
                .filter(step -> step.getJourneyStep() != null)
                .filter(step -> step.getJourneyStep().getStepType() == JourneyStep.StepType.SESSION)
                .filter(step -> step.getStatus() == JourneyInstanceStep.StepStatus.READY)
                .min(Comparator.comparing(step -> step.getJourneyStep().getDefaultSequence(), Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (nextSessionStep == null) {
            return;
        }

        nextSessionStep.setStatus(JourneyInstanceStep.StepStatus.COMPLETED);
        nextSessionStep.setCompletedAt(session.getScheduledEnd() != null ? session.getScheduledEnd() : ZonedDateTime.now());
        nextSessionStep.setBlockedReason(null);
        journeyInstanceStepRepository.save(nextSessionStep);

        recalculateJourney(instance, instanceSteps);
    }

    private JourneyInstanceStep buildInstanceStep(JourneyInstance instance, JourneyStep step) {
        JourneyInstanceStep instanceStep = new JourneyInstanceStep();
        instanceStep.setJourneyInstance(instance);
        instanceStep.setJourneyStep(step);
        instanceStep.setStatus(JourneyInstanceStep.StepStatus.PENDING);
        instanceStep.setBlockedReason("Waiting for earlier journey milestone");
        instanceStep.setDueAt(resolveDueAt(instance, step));
        return instanceStep;
    }

    private ZonedDateTime resolveDueAt(JourneyInstance instance, JourneyStep step) {
        if (step.getDefaultDueOffsetDays() == null) {
            return null;
        }

        return instance.getStartedAt()
                .atZone(ZoneId.systemDefault())
                .plusDays(step.getDefaultDueOffsetDays());
    }

    private void recalculateJourney(JourneyInstance instance, List<JourneyInstanceStep> currentSteps) {
        if (instance == null || currentSteps == null || currentSteps.isEmpty()) {
            return;
        }

        List<JourneyInstanceStep> steps = new ArrayList<>(currentSteps);
        steps.sort(Comparator.comparing(step -> step.getJourneyStep().getDefaultSequence(), Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, JourneyInstanceStep> byTemplateStepId = steps.stream()
                .filter(step -> step.getJourneyStep() != null && step.getJourneyStep().getId() != null)
                .collect(Collectors.toMap(
                        step -> step.getJourneyStep().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        UUID templateId = instance.getJourneyTemplate() != null ? instance.getJourneyTemplate().getId() : null;
        List<JourneyStepDependency> dependencies = templateId != null
                ? journeyStepDependencyRepository.findByJourneyTemplate_Id(templateId)
                : List.of();
        Map<UUID, List<JourneyStepDependency>> dependenciesByTargetStepId = dependencies.stream()
                .collect(Collectors.groupingBy(
                        dependency -> dependency.getToStep().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        UUID participantId = instance.getParticipant() != null ? instance.getParticipant().getId() : null;
        boolean hasProgramMentorAssignment = participantId != null
                && mentorAssignmentRepository.existsByParticipant_IdAndJourneyInstanceStepIsNull(participantId);
        List<UUID> journeyInstanceStepIds = steps.stream().map(JourneyInstanceStep::getId).toList();
        List<CompanyProgramMentorAssignment> stepAssignments =
                participantId != null && !journeyInstanceStepIds.isEmpty()
                        ? mentorAssignmentRepository.findByParticipant_IdAndJourneyInstanceStep_IdIn(
                                participantId,
                                journeyInstanceStepIds
                        )
                        : List.of();
        if (stepAssignments == null) {
            stepAssignments = List.of();
        }
        Set<UUID> assignedJourneyStepIds = stepAssignments.stream()
                .filter(assignment -> assignment.getJourneyInstanceStep() != null
                        && assignment.getJourneyInstanceStep().getId() != null)
                .map(assignment -> assignment.getJourneyInstanceStep().getId())
                .collect(Collectors.toCollection(HashSet::new));

        boolean changed = false;
        for (JourneyInstanceStep step : steps) {
            if (step.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED
                    || step.getStatus() == JourneyInstanceStep.StepStatus.SKIPPED) {
                continue;
            }

            List<JourneyStepDependency> incomingDependencies = dependenciesByTargetStepId.getOrDefault(
                    step.getJourneyStep().getId(),
                    List.of()
            );

            boolean dependenciesSatisfied = incomingDependencies.isEmpty() || incomingDependencies.stream()
                    .allMatch(dependency -> {
                        JourneyInstanceStep sourceStep = byTemplateStepId.get(dependency.getFromStep().getId());
                        if (sourceStep == null) {
                            return false;
                        }
                        return sourceStep.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED
                                || sourceStep.getStatus() == JourneyInstanceStep.StepStatus.SKIPPED;
                    });

            boolean hasMentorAssignmentForStep = hasProgramMentorAssignment
                    || (step.getId() != null && assignedJourneyStepIds.contains(step.getId()));

            boolean mentorRequirementBlocked = dependenciesSatisfied
                    && requiresMentorAssignment(step)
                    && !hasMentorAssignmentForStep;
            String blockedReason = !dependenciesSatisfied
                    ? "Waiting for earlier journey milestone"
                    : mentorRequirementBlocked
                    ? "Waiting for mentor assignment"
                    : null;

            if (blockedReason == null) {
                if (step.getStatus() != JourneyInstanceStep.StepStatus.READY || step.getBlockedReason() != null) {
                    step.setStatus(JourneyInstanceStep.StepStatus.READY);
                    step.setBlockedReason(null);
                    changed = true;
                }
            } else if (step.getStatus() != JourneyInstanceStep.StepStatus.BLOCKED
                    || !blockedReason.equals(step.getBlockedReason())) {
                step.setStatus(JourneyInstanceStep.StepStatus.BLOCKED);
                step.setBlockedReason(blockedReason);
                changed = true;
            }
        }

        long completedCount = steps.stream()
                .filter(step -> step.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED
                        || step.getStatus() == JourneyInstanceStep.StepStatus.SKIPPED)
                .count();
        int progressPercent = steps.isEmpty()
                ? 0
                : (int) Math.round((completedCount * 100.0) / steps.size());

        JourneyInstance.JourneyStatus nextStatus;
        if (completedCount == 0) {
            nextStatus = steps.stream().anyMatch(step -> step.getStatus() == JourneyInstanceStep.StepStatus.READY)
                    ? JourneyInstance.JourneyStatus.IN_PROGRESS
                    : JourneyInstance.JourneyStatus.NOT_STARTED;
        } else if (completedCount >= steps.size()) {
            nextStatus = JourneyInstance.JourneyStatus.COMPLETED;
        } else {
            nextStatus = JourneyInstance.JourneyStatus.IN_PROGRESS;
        }

        instance.setProgressPercent(progressPercent);
        instance.setStatus(nextStatus);
        instance.setCompletedAt(nextStatus == JourneyInstance.JourneyStatus.COMPLETED ? java.time.LocalDateTime.now() : null);
        journeyInstanceRepository.save(instance);

        if (changed) {
            journeyInstanceStepRepository.saveAll(steps);
        }
    }

    private boolean requiresMentorAssignment(JourneyInstanceStep step) {
        if (step == null || step.getJourneyStep() == null || step.getJourneyInstance() == null) {
            return false;
        }

        JourneyStep journeyStep = step.getJourneyStep();
        if (journeyStep.getStepType() != JourneyStep.StepType.SESSION) {
            return false;
        }

        CompanyProgramParticipant participant = step.getJourneyInstance().getParticipant();
        CompanyProgram companyProgram = participant != null ? participant.getCompanyProgram() : null;
        return companyProgram != null && Boolean.TRUE.equals(companyProgram.getRequiresMentorForSessionSteps());
    }

    private boolean canMigrateToProgramTemplate(JourneyInstance instance,
                                                List<JourneyInstanceStep> steps,
                                                CompanyProgram companyProgram) {
        if (instance == null || companyProgram == null || companyProgram.getJourneyTemplate() == null) {
            return false;
        }

        UUID currentTemplateId = instance.getJourneyTemplate() != null ? instance.getJourneyTemplate().getId() : null;
        UUID nextTemplateId = companyProgram.getJourneyTemplate().getId();
        if (currentTemplateId == null || currentTemplateId.equals(nextTemplateId)) {
            return false;
        }

        return steps.stream().noneMatch(step ->
                step.getStatus() == JourneyInstanceStep.StepStatus.COMPLETED
                        || step.getStatus() == JourneyInstanceStep.StepStatus.SKIPPED
        );
    }

    public boolean canBeCompletedByEmployee(JourneyStep journeyStep) {
        if (journeyStep == null || journeyStep.getStepType() == null) {
            return false;
        }

        return journeyStep.getStepType() == JourneyStep.StepType.REFLECTION
                || journeyStep.getStepType() == JourneyStep.StepType.CHECK_IN
                || journeyStep.getStepType() == JourneyStep.StepType.ACTION_ITEM;
    }

    public record JourneyTemplateMigrationSummary(int migratedParticipants,
                                                  int createdParticipants,
                                                  int retainedParticipants) {
    }
}
