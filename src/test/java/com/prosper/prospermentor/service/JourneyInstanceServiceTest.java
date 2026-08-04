package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.JourneyStepDependency;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.JourneyInstanceRepository;
import com.prosper.prospermentor.repository.JourneyInstanceStepRepository;
import com.prosper.prospermentor.repository.JourneyStepDependencyRepository;
import com.prosper.prospermentor.repository.JourneyStepRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JourneyInstanceServiceTest {

    @Mock
    private JourneyInstanceRepository journeyInstanceRepository;
    @Mock
    private JourneyInstanceStepRepository journeyInstanceStepRepository;
    @Mock
    private JourneyStepRepository journeyStepRepository;
    @Mock
    private JourneyStepDependencyRepository journeyStepDependencyRepository;
    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock
    private CompanyProgramMentorAssignmentRepository mentorAssignmentRepository;

    @InjectMocks
    private JourneyInstanceService journeyInstanceService;

    @Test
    void ensureJourneyInstanceForParticipant_shouldCreateReadyFirstStepAndBlockedFollowingStep() {
        CompanyProgramParticipant participant = participant();
        JourneyTemplate template = participant.getCompanyProgram().getJourneyTemplate();

        JourneyStep sessionStep = journeyStep(template, "kickoff_session", 1, JourneyStep.StepType.SESSION);
        JourneyStep reflectionStep = journeyStep(template, "goals_reflection", 2, JourneyStep.StepType.REFLECTION);
        JourneyStepDependency dependency = dependency(template, sessionStep, reflectionStep);

        when(journeyInstanceRepository.findByParticipant_Id(participant.getId())).thenReturn(Optional.empty());
        when(journeyStepRepository.findByJourneyTemplate_IdOrderByDefaultSequenceAsc(template.getId()))
                .thenReturn(List.of(sessionStep, reflectionStep));
        when(journeyInstanceRepository.save(any(JourneyInstance.class))).thenAnswer(invocation -> {
            JourneyInstance instance = invocation.getArgument(0);
            if (instance.getId() == null) {
                instance.setId(UUID.randomUUID());
            }
            return instance;
        });
        when(journeyInstanceStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyStepDependencyRepository.findByJourneyTemplate_Id(template.getId())).thenReturn(List.of(dependency));

        JourneyInstance created = journeyInstanceService.ensureJourneyInstanceForParticipant(participant).orElseThrow();

        assertThat(created.getStatus()).isEqualTo(JourneyInstance.JourneyStatus.IN_PROGRESS);
        assertThat(created.getProgressPercent()).isZero();
        verify(journeyInstanceStepRepository, atLeastOnce()).saveAll(argThat(steps -> {
            List<JourneyInstanceStep> savedSteps = new java.util.ArrayList<>();
            steps.forEach(savedSteps::add);
            JourneyInstanceStep first = savedSteps.get(0);
            JourneyInstanceStep second = savedSteps.get(1);
            return first.getStatus() == JourneyInstanceStep.StepStatus.READY
                    && second.getStatus() == JourneyInstanceStep.StepStatus.BLOCKED;
        }));
    }

    @Test
    void advanceAfterSessionCompletion_shouldCompleteSessionStepAndUnlockNextMilestone() {
        CompanyProgramParticipant participant = participant();
        JourneyTemplate template = participant.getCompanyProgram().getJourneyTemplate();

        JourneyStep sessionStep = journeyStep(template, "kickoff_session", 1, JourneyStep.StepType.SESSION);
        JourneyStep reflectionStep = journeyStep(template, "goals_reflection", 2, JourneyStep.StepType.REFLECTION);
        JourneyStepDependency dependency = dependency(template, sessionStep, reflectionStep);

        JourneyInstance instance = new JourneyInstance();
        instance.setId(UUID.randomUUID());
        instance.setParticipant(participant);
        instance.setJourneyTemplate(template);
        instance.setStatus(JourneyInstance.JourneyStatus.IN_PROGRESS);
        instance.setProgressPercent(0);

        JourneyInstanceStep readySession = new JourneyInstanceStep();
        readySession.setId(UUID.randomUUID());
        readySession.setJourneyInstance(instance);
        readySession.setJourneyStep(sessionStep);
        readySession.setStatus(JourneyInstanceStep.StepStatus.READY);

        JourneyInstanceStep blockedReflection = new JourneyInstanceStep();
        blockedReflection.setId(UUID.randomUUID());
        blockedReflection.setJourneyInstance(instance);
        blockedReflection.setJourneyStep(reflectionStep);
        blockedReflection.setStatus(JourneyInstanceStep.StepStatus.BLOCKED);
        blockedReflection.setBlockedReason("Waiting for earlier journey milestone");

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setCompanyProgramParticipantId(participant.getId());
        session.setScheduledEnd(ZonedDateTime.now().minusMinutes(5));

        when(journeyInstanceRepository.findByParticipant_Id(participant.getId())).thenReturn(Optional.of(instance));
        when(journeyInstanceStepRepository.findDetailedByJourneyInstanceId(instance.getId()))
                .thenReturn(List.of(readySession, blockedReflection));
        when(journeyInstanceStepRepository.save(any(JourneyInstanceStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyInstanceRepository.save(any(JourneyInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyStepDependencyRepository.findByJourneyTemplate_Id(template.getId())).thenReturn(List.of(dependency));
        when(journeyInstanceStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        journeyInstanceService.advanceAfterSessionCompletion(session);

        assertThat(readySession.getStatus()).isEqualTo(JourneyInstanceStep.StepStatus.COMPLETED);
        assertThat(blockedReflection.getStatus()).isEqualTo(JourneyInstanceStep.StepStatus.READY);
        assertThat(instance.getProgressPercent()).isEqualTo(50);
    }

    @Test
    void refreshJourneyForParticipant_shouldOnlyUnlockTheSessionStepWithScopedMentorAssignment() {
        CompanyProgramParticipant participant = participant();
        participant.getCompanyProgram().setRequiresMentorForSessionSteps(true);
        JourneyTemplate template = participant.getCompanyProgram().getJourneyTemplate();
        JourneyInstance instance = journeyInstance(participant);

        JourneyInstanceStep firstSession = instanceStep(instance, journeyStep(template, "kickoff_session", 1, JourneyStep.StepType.SESSION));
        firstSession.setStatus(JourneyInstanceStep.StepStatus.BLOCKED);
        firstSession.setBlockedReason("Waiting for mentor assignment");
        JourneyInstanceStep secondSession = instanceStep(instance, journeyStep(template, "follow_up_session", 2, JourneyStep.StepType.SESSION));
        secondSession.setStatus(JourneyInstanceStep.StepStatus.BLOCKED);
        secondSession.setBlockedReason("Waiting for mentor assignment");

        CompanyProgramMentorAssignment firstStepAssignment = new CompanyProgramMentorAssignment();
        firstStepAssignment.setParticipant(participant);
        firstStepAssignment.setJourneyInstanceStep(firstSession);

        when(journeyInstanceRepository.findByParticipant_Id(participant.getId())).thenReturn(Optional.of(instance));
        when(journeyInstanceStepRepository.findDetailedByJourneyInstanceId(instance.getId()))
                .thenReturn(List.of(firstSession, secondSession));
        when(journeyStepDependencyRepository.findByJourneyTemplate_Id(template.getId())).thenReturn(List.of());
        when(mentorAssignmentRepository.existsByParticipant_IdAndJourneyInstanceStepIsNull(participant.getId()))
                .thenReturn(false);
        when(mentorAssignmentRepository.findByParticipant_IdAndJourneyInstanceStep_IdIn(
                participant.getId(),
                List.of(firstSession.getId(), secondSession.getId())
        )).thenReturn(List.of(firstStepAssignment));
        when(journeyInstanceStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyInstanceRepository.save(any(JourneyInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        journeyInstanceService.refreshJourneyForParticipant(participant.getId());

        assertThat(firstSession.getStatus()).isEqualTo(JourneyInstanceStep.StepStatus.READY);
        assertThat(firstSession.getBlockedReason()).isNull();
        assertThat(secondSession.getStatus()).isEqualTo(JourneyInstanceStep.StepStatus.BLOCKED);
        assertThat(secondSession.getBlockedReason()).isEqualTo("Waiting for mentor assignment");
    }

    private CompanyProgramParticipant participant() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Acme Corp");

        JourneyTemplate template = new JourneyTemplate();
        template.setId(UUID.randomUUID());
        template.setName("Onboarding");
        template.setActive(true);

        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.randomUUID());
        companyProgram.setCompany(company);
        companyProgram.setJourneyTemplate(template);
        companyProgram.setRequiresMentorForSessionSteps(false);

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("employee@example.com");
        profile.setUsername("employee");
        profile.setRole("EMPLOYEE");

        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(UUID.randomUUID());
        participant.setCompanyProgram(companyProgram);
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
        participant.setEnrolledAt(LocalDateTime.now().minusDays(1));
        return participant;
    }

    private JourneyInstance journeyInstance(CompanyProgramParticipant participant) {
        JourneyInstance instance = new JourneyInstance();
        instance.setId(UUID.randomUUID());
        instance.setParticipant(participant);
        instance.setJourneyTemplate(participant.getCompanyProgram().getJourneyTemplate());
        instance.setStatus(JourneyInstance.JourneyStatus.IN_PROGRESS);
        instance.setProgressPercent(0);
        return instance;
    }

    private JourneyInstanceStep instanceStep(JourneyInstance instance, JourneyStep step) {
        JourneyInstanceStep instanceStep = new JourneyInstanceStep();
        instanceStep.setId(UUID.randomUUID());
        instanceStep.setJourneyInstance(instance);
        instanceStep.setJourneyStep(step);
        instanceStep.setStatus(JourneyInstanceStep.StepStatus.PENDING);
        return instanceStep;
    }

    private JourneyStep journeyStep(JourneyTemplate template, String key, int sequence, JourneyStep.StepType stepType) {
        JourneyStep step = new JourneyStep();
        step.setId(UUID.randomUUID());
        step.setJourneyTemplate(template);
        step.setStepKey(key);
        step.setDefaultSequence(sequence);
        step.setTitle(key);
        step.setRequired(true);
        step.setStepType(stepType);
        return step;
    }

    private JourneyStepDependency dependency(JourneyTemplate template, JourneyStep fromStep, JourneyStep toStep) {
        JourneyStepDependency dependency = new JourneyStepDependency();
        dependency.setId(UUID.randomUUID());
        dependency.setJourneyTemplate(template);
        dependency.setFromStep(fromStep);
        dependency.setToStep(toStep);
        dependency.setDependencyType(JourneyStepDependency.DependencyType.FINISH_TO_START);
        return dependency;
    }
}
