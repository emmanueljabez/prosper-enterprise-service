package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.EmployeeCompanyProgramJourneyDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.SessionOutcomeActionItemRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramJourneyServiceTest {

    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionOutcomeRepository sessionOutcomeRepository;
    @Mock
    private SessionOutcomeActionItemRepository sessionOutcomeActionItemRepository;
    @Mock
    private CompanyProgramMentorAssignmentService mentorAssignmentService;
    @Mock
    private JourneyInstanceService journeyInstanceService;
    @Mock
    private EmployeeSessionAllocationService employeeSessionAllocationService;

    @InjectMocks
    private CompanyProgramJourneyService companyProgramJourneyService;

    @Test
    void getJourneysForProfile_shouldReturnJourneyWhenParticipantHasNoSessionsYet() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramParticipant participant = participant(profileId);

        when(companyProgramParticipantRepository.findByProfileIdAndStatusIn(eq(profileId), anyCollection()))
                .thenReturn(List.of(participant));
        when(sessionRepository.findByCompanyProgramParticipantIdInOrderByScheduledStartDesc(List.of(participant.getId())))
                .thenReturn(List.of());
        when(mentorAssignmentService.getAssignmentSummaries(List.of(participant.getId())))
                .thenReturn(Map.of());
        when(journeyInstanceService.getInstancesByParticipantIds(List.of(participant.getId())))
                .thenReturn(Map.of());
        when(journeyInstanceService.getStepsByInstanceIds(List.of()))
                .thenReturn(Map.of());

        List<EmployeeCompanyProgramJourneyDto> journeys = companyProgramJourneyService.getJourneysForProfile(profileId);

        assertThat(journeys).hasSize(1);
        assertThat(journeys.get(0).getParticipantId()).isEqualTo(participant.getId());
        assertThat(journeys.get(0).getNextSession()).isNull();
        assertThat(journeys.get(0).getRecentSessions()).isEmpty();
        assertThat(journeys.get(0).getOpenActionItemCount()).isZero();
        verify(sessionOutcomeRepository, never()).findDetailedBySessionIds(anyCollection());
    }

    @Test
    void getJourneysForProfile_shouldExposeBookSessionActionForReadySessionWhenMentorAssignedAndBalanceAvailable() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramParticipant participant = participant(profileId);
        participant.getCompanyProgram().setStatus(CompanyProgram.CompanyProgramStatus.LIVE);
        JourneyInstance journeyInstance = journeyInstance(participant);
        JourneyInstanceStep sessionStep = instanceStep(journeyInstance, "kickoff_session", JourneyStep.StepType.SESSION, JourneyInstanceStep.StepStatus.READY);
        MentorAssignmentSummaryDto assignment = MentorAssignmentSummaryDto.builder()
                .mentorId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
                .mentorName("Assigned Mentor")
                .build();
        EmployeeSessionAllocation allocation = allocation(participant, 4, 2);

        mockJourney(profileId, participant, journeyInstance, List.of(sessionStep), Map.of(participant.getId(), assignment), Optional.of(allocation));

        List<EmployeeCompanyProgramJourneyDto> journeys = companyProgramJourneyService.getJourneysForProfile(profileId);

        EmployeeCompanyProgramJourneyDto.JourneyStepActionDto action = journeys.get(0).getSteps().get(0).getPrimaryAction();
        assertThat(action.getActionType()).isEqualTo(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.BOOK_SESSION);
        assertThat(action.getLabel()).isEqualTo("Book session");
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.getAvailableSessionBalance()).isEqualTo(2);
        assertThat(action.getMentorId()).isEqualTo(assignment.getMentorId());
        assertThat(action.getCompanyProgramParticipantId()).isEqualTo(participant.getId());
    }

    @Test
    void getJourneysForProfile_shouldExposeBuySessionActionForReadySessionWhenAssignedSessionsAreDepleted() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramParticipant participant = participant(profileId);
        participant.getCompanyProgram().setStatus(CompanyProgram.CompanyProgramStatus.LIVE);
        JourneyInstance journeyInstance = journeyInstance(participant);
        JourneyInstanceStep sessionStep = instanceStep(journeyInstance, "kickoff_session", JourneyStep.StepType.SESSION, JourneyInstanceStep.StepStatus.READY);
        MentorAssignmentSummaryDto assignment = MentorAssignmentSummaryDto.builder()
                .mentorId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
                .mentorName("Assigned Mentor")
                .build();
        EmployeeSessionAllocation allocation = allocation(participant, 4, 0);

        mockJourney(profileId, participant, journeyInstance, List.of(sessionStep), Map.of(participant.getId(), assignment), Optional.of(allocation));

        List<EmployeeCompanyProgramJourneyDto> journeys = companyProgramJourneyService.getJourneysForProfile(profileId);

        EmployeeCompanyProgramJourneyDto.JourneyStepActionDto action = journeys.get(0).getSteps().get(0).getPrimaryAction();
        assertThat(action.getActionType()).isEqualTo(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.BUY_SESSION);
        assertThat(action.getLabel()).isEqualTo("Buy session");
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.getAvailableSessionBalance()).isZero();
        assertThat(action.getDescription()).contains("depleted");
    }

    @Test
    void getJourneysForProfile_shouldScopeEmployeeChosenMentorToTheSelectedJourneyStep() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramParticipant participant = participant(profileId);
        participant.getCompanyProgram().setStatus(CompanyProgram.CompanyProgramStatus.LIVE);
        JourneyInstance journeyInstance = journeyInstance(participant);
        JourneyInstanceStep selectedSessionStep = instanceStep(journeyInstance, "kickoff_session", JourneyStep.StepType.SESSION, JourneyInstanceStep.StepStatus.READY);
        selectedSessionStep.getJourneyStep().setDefaultSequence(1);
        JourneyInstanceStep laterSessionStep = instanceStep(journeyInstance, "follow_up_session", JourneyStep.StepType.SESSION, JourneyInstanceStep.StepStatus.READY);
        laterSessionStep.getJourneyStep().setDefaultSequence(2);
        MentorAssignmentSummaryDto stepAssignment = MentorAssignmentSummaryDto.builder()
                .mentorId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
                .mentorName("Selected Mentor")
                .journeyInstanceStepId(selectedSessionStep.getId())
                .build();
        EmployeeSessionAllocation allocation = allocation(participant, 4, 2);

        mockJourney(
                profileId,
                participant,
                journeyInstance,
                List.of(selectedSessionStep, laterSessionStep),
                Map.of(),
                Map.of(selectedSessionStep.getId(), stepAssignment),
                Optional.of(allocation)
        );

        List<EmployeeCompanyProgramJourneyDto> journeys = companyProgramJourneyService.getJourneysForProfile(profileId);

        List<EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto> steps = journeys.get(0).getSteps();
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getMentorAssignment()).isEqualTo(stepAssignment);
        assertThat(steps.get(0).getPrimaryAction().getActionType()).isEqualTo(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.BOOK_SESSION);
        assertThat(steps.get(0).getPrimaryAction().getMentorId()).isEqualTo(stepAssignment.getMentorId());

        assertThat(steps.get(1).getMentorAssignment()).isNull();
        assertThat(steps.get(1).getPrimaryAction().getActionType()).isEqualTo(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.VIEW_MATCHES);
        assertThat(steps.get(1).getPrimaryAction().getLabel()).isEqualTo("Choose mentor");
        assertThat(steps.get(1).getPrimaryAction().getMentorId()).isNull();
    }

    @Test
    void getJourneysForProfile_shouldExposeCompleteStepActionForReadyEmployeeOwnedMilestone() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramParticipant participant = participant(profileId);
        JourneyInstance journeyInstance = journeyInstance(participant);
        JourneyInstanceStep reflectionStep = instanceStep(journeyInstance, "goals_reflection", JourneyStep.StepType.REFLECTION, JourneyInstanceStep.StepStatus.READY);

        mockJourney(profileId, participant, journeyInstance, List.of(reflectionStep), Map.of(), Optional.empty());
        when(journeyInstanceService.canBeCompletedByEmployee(reflectionStep.getJourneyStep())).thenReturn(true);

        List<EmployeeCompanyProgramJourneyDto> journeys = companyProgramJourneyService.getJourneysForProfile(profileId);

        EmployeeCompanyProgramJourneyDto.JourneyStepActionDto action = journeys.get(0).getSteps().get(0).getPrimaryAction();
        assertThat(action.getActionType()).isEqualTo(EmployeeCompanyProgramJourneyDto.JourneyStepActionType.COMPLETE_STEP);
        assertThat(action.getLabel()).isEqualTo("Mark complete");
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.getJourneyInstanceStepId()).isEqualTo(reflectionStep.getId());
    }

    private CompanyProgramParticipant participant(UUID profileId) {
        Company company = new Company();
        company.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        company.setName("Kenya Airways");

        JourneyTemplate journeyTemplate = new JourneyTemplate();
        journeyTemplate.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        journeyTemplate.setName("First-Time Manager Journey");

        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        companyProgram.setCompany(company);
        companyProgram.setJourneyTemplate(journeyTemplate);
        companyProgram.setName("First-Time Manager");
        companyProgram.setStatus(CompanyProgram.CompanyProgramStatus.DRAFT);
        companyProgram.setMatchingMode(CompanyProgram.MatchingMode.ADMIN_ASSIGN);

        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setEmail("employee@example.com");
        profile.setRole("mentee");

        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        participant.setCompanyProgram(companyProgram);
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ENROLLED);
        participant.setEnrolledAt(LocalDateTime.of(2026, 5, 10, 8, 15));
        return participant;
    }

    private void mockJourney(UUID profileId,
                             CompanyProgramParticipant participant,
                             JourneyInstance journeyInstance,
                             List<JourneyInstanceStep> steps,
                             Map<UUID, MentorAssignmentSummaryDto> assignments,
                             Optional<EmployeeSessionAllocation> allocation) {
        mockJourney(profileId, participant, journeyInstance, steps, assignments, Map.of(), allocation);
    }

    private void mockJourney(UUID profileId,
                             CompanyProgramParticipant participant,
                             JourneyInstance journeyInstance,
                             List<JourneyInstanceStep> steps,
                             Map<UUID, MentorAssignmentSummaryDto> assignments,
                             Map<UUID, MentorAssignmentSummaryDto> stepAssignments,
                             Optional<EmployeeSessionAllocation> allocation) {
        when(companyProgramParticipantRepository.findByProfileIdAndStatusIn(eq(profileId), anyCollection()))
                .thenReturn(List.of(participant));
        when(sessionRepository.findByCompanyProgramParticipantIdInOrderByScheduledStartDesc(List.of(participant.getId())))
                .thenReturn(List.of());
        when(mentorAssignmentService.getAssignmentSummaries(List.of(participant.getId())))
                .thenReturn(assignments);
        if (!steps.isEmpty()) {
            when(mentorAssignmentService.getStepAssignmentSummaries(
                    List.of(participant.getId()),
                    steps.stream().map(JourneyInstanceStep::getId).toList()
            )).thenReturn(stepAssignments);
        }
        when(journeyInstanceService.getInstancesByParticipantIds(List.of(participant.getId())))
                .thenReturn(Map.of(participant.getId(), journeyInstance));
        when(journeyInstanceService.getStepsByInstanceIds(List.of(journeyInstance.getId())))
                .thenReturn(Map.of(journeyInstance.getId(), steps));
        when(employeeSessionAllocationService.findAllocationForProfile(profileId)).thenReturn(allocation);
    }

    private JourneyInstance journeyInstance(CompanyProgramParticipant participant) {
        JourneyInstance journeyInstance = new JourneyInstance();
        journeyInstance.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        journeyInstance.setParticipant(participant);
        journeyInstance.setJourneyTemplate(participant.getCompanyProgram().getJourneyTemplate());
        journeyInstance.setStatus(JourneyInstance.JourneyStatus.IN_PROGRESS);
        journeyInstance.setProgressPercent(0);
        return journeyInstance;
    }

    private JourneyInstanceStep instanceStep(JourneyInstance journeyInstance,
                                             String stepKey,
                                             JourneyStep.StepType stepType,
                                             JourneyInstanceStep.StepStatus status) {
        JourneyStep step = new JourneyStep();
        step.setId(UUID.randomUUID());
        step.setJourneyTemplate(journeyInstance.getJourneyTemplate());
        step.setStepKey(stepKey);
        step.setDefaultSequence(1);
        step.setTitle(stepKey);
        step.setStepType(stepType);
        step.setRequired(true);

        JourneyInstanceStep instanceStep = new JourneyInstanceStep();
        instanceStep.setId(UUID.randomUUID());
        instanceStep.setJourneyInstance(journeyInstance);
        instanceStep.setJourneyStep(step);
        instanceStep.setStatus(status);
        return instanceStep;
    }

    private EmployeeSessionAllocation allocation(CompanyProgramParticipant participant,
                                                 int allocatedTotal,
                                                 int availableBalance) {
        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompany(participant.getCompanyProgram().getCompany());
        allocation.setProfile(participant.getProfile());
        allocation.setAllocatedTotal(allocatedTotal);
        allocation.setAvailableBalance(availableBalance);
        return allocation;
    }
}
