package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.dto.MatchWorkspaceSummaryDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyProgramMatchOptionRepository;
import com.prosper.prospermentor.repository.CompanyProgramMatchWorkspaceRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.JourneyInstanceStepRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramMatchWorkspaceServiceTest {

    @Mock
    private CompanyProgramMatchWorkspaceRepository workspaceRepository;
    @Mock
    private CompanyProgramMatchOptionRepository optionRepository;
    @Mock
    private CompanyProgramParticipantRepository participantRepository;
    @Mock
    private CompanyProgramMentorAssignmentRepository assignmentRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private JourneyInstanceStepRepository journeyInstanceStepRepository;
    @Mock
    private CompanyProgramMentorAssignmentService mentorAssignmentService;
    @Mock
    private JourneyInstanceService journeyInstanceService;
    @Mock
    private CompanyProgramCohortGateService cohortGateService;

    @InjectMocks
    private CompanyProgramMatchWorkspaceService matchWorkspaceService;

    @Test
    void getWorkspaceSummaries_shouldExposeBlockedReasonWhenCohortGateBlocksMatching() {
        UUID participantId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramParticipant participant = participant(participantId, UUID.fromString("22222222-2222-2222-2222-222222222222"));

        when(participantRepository.findAllById(List.of(participantId))).thenReturn(List.of(participant));
        when(workspaceRepository.findByParticipant_IdIn(List.of(participantId))).thenReturn(List.of());
        when(assignmentRepository.findByParticipant_IdInAndJourneyInstanceStepIsNull(List.of(participantId))).thenReturn(List.of());
        when(cohortGateService.resolveGateStatusForProgramParticipant(participantId)).thenReturn(
                CohortGateStatusDto.builder()
                        .companyProgramParticipantId(participantId)
                        .eligibleForMatching(false)
                        .blockedReason("PLENARY_NOT_ATTENDED")
                        .build()
        );

        Map<UUID, MatchWorkspaceSummaryDto> summaries = matchWorkspaceService.getWorkspaceSummaries(List.of(participantId));

        assertThat(summaries.get(participantId).getStatus()).isEqualTo(CompanyProgramMatchWorkspace.MatchStatus.INACTIVE);
        assertThat(summaries.get(participantId).getBlockedReason()).isEqualTo("PLENARY_NOT_ATTENDED");
    }

    @Test
    void selectMarketplaceMentorForEmployee_shouldAssignMentorOutsideShortlistForJourneyStep() {
        UUID participantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID profileId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID mentorId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID journeyInstanceStepId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        CompanyProgramParticipant participant = participant(participantId, profileId);
        CompanyProgramMatchWorkspace workspace = workspace(participant);
        JourneyInstanceStep journeyInstanceStep = readySessionStep(participant, journeyInstanceStepId);
        MentorAssignmentSummaryDto assignment = MentorAssignmentSummaryDto.builder()
                .mentorId(mentorId)
                .mentorName("Marketplace Mentor")
                .journeyInstanceStepId(journeyInstanceStepId)
                .build();

        when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(journeyInstanceStepRepository.findById(journeyInstanceStepId)).thenReturn(Optional.of(journeyInstanceStep));
        when(assignmentRepository.findByParticipant_IdAndJourneyInstanceStep_Id(participantId, journeyInstanceStepId))
                .thenReturn(Optional.empty());
        when(workspaceRepository.findByParticipant_Id(participantId)).thenReturn(Optional.of(workspace));
        when(mentorAssignmentService.assignMarketplaceMentor(participantId, mentorId, profileId, journeyInstanceStep))
                .thenReturn(ApiResponse.success("Mentor assigned successfully", assignment));
        when(workspaceRepository.save(any(CompanyProgramMatchWorkspace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MentorAssignmentSummaryDto result = matchWorkspaceService.selectMarketplaceMentorForEmployee(
                participantId,
                profileId,
                mentorId,
                journeyInstanceStepId
        );

        assertThat(result.getMentorId()).isEqualTo(mentorId);
        verify(optionRepository, never()).findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(workspace.getId());
        verify(journeyInstanceService).refreshJourneyForParticipant(participantId);

        ArgumentCaptor<CompanyProgramMatchWorkspace> workspaceCaptor =
                ArgumentCaptor.forClass(CompanyProgramMatchWorkspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getStatus()).isEqualTo(CompanyProgramMatchWorkspace.MatchStatus.ASSIGNED);
        assertThat(workspaceCaptor.getValue().getResolvedBy()).isEqualTo(CompanyProgramMatchWorkspace.ResolverType.EMPLOYEE);
        assertThat(workspaceCaptor.getValue().getResolvedByUserId()).isEqualTo(profileId);
    }

    private JourneyInstanceStep readySessionStep(CompanyProgramParticipant participant, UUID journeyInstanceStepId) {
        JourneyTemplate template = new JourneyTemplate();
        template.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));

        JourneyInstance journeyInstance = new JourneyInstance();
        journeyInstance.setId(UUID.fromString("77777777-1111-1111-1111-777777777777"));
        journeyInstance.setParticipant(participant);
        journeyInstance.setJourneyTemplate(template);

        JourneyStep journeyStep = new JourneyStep();
        journeyStep.setId(UUID.fromString("88888888-8888-8888-8888-888888888888"));
        journeyStep.setJourneyTemplate(template);
        journeyStep.setStepKey("kickoff_session");
        journeyStep.setDefaultSequence(1);
        journeyStep.setTitle("Kickoff session");
        journeyStep.setStepType(JourneyStep.StepType.SESSION);
        journeyStep.setRequired(true);

        JourneyInstanceStep instanceStep = new JourneyInstanceStep();
        instanceStep.setId(journeyInstanceStepId);
        instanceStep.setJourneyInstance(journeyInstance);
        instanceStep.setJourneyStep(journeyStep);
        instanceStep.setStatus(JourneyInstanceStep.StepStatus.READY);
        return instanceStep;
    }

    private CompanyProgramParticipant participant(UUID participantId, UUID profileId) {
        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        companyProgram.setName("Leadership Accelerator");
        companyProgram.setStatus(CompanyProgram.CompanyProgramStatus.LIVE);
        companyProgram.setMatchingMode(CompanyProgram.MatchingMode.ADMIN_ASSIGN);

        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setEmail("employee@example.com");
        profile.setRole("mentee");

        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(participantId);
        participant.setCompanyProgram(companyProgram);
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ENROLLED);
        return participant;
    }

    private CompanyProgramMatchWorkspace workspace(CompanyProgramParticipant participant) {
        CompanyProgramMatchWorkspace workspace = new CompanyProgramMatchWorkspace();
        workspace.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        workspace.setParticipant(participant);
        workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.ADMIN_REVIEW);
        workspace.setSelectionDeadlineAt(LocalDateTime.now().plusHours(4));
        return workspace;
    }
}
