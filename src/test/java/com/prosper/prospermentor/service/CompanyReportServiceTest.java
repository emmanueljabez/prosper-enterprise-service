package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyReportDtos;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramMatchWorkspaceRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.ParticipantPulseRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyReportServiceTest {

    @Mock
    private CompanyProgramRepository companyProgramRepository;

    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;

    @Mock
    private CompanyProgramMentorAssignmentRepository mentorAssignmentRepository;

    @Mock
    private CompanyProgramMatchWorkspaceRepository matchWorkspaceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ParticipantPulseRepository participantPulseRepository;

    @Mock
    private ReviewAlertRepository reviewAlertRepository;

    @Mock
    private ReviewCycleRepository reviewCycleRepository;

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private CompanyReportService companyReportService;

    @Test
    void participantReportFlattensProgramParticipantMentorAndMatchRows() {
        UUID companyId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        CompanyProgram program = companyProgram(companyId, "Leadership Accelerator");
        Profile employee = profile("Amina", "Mensah", "amina@example.com", "mentee");
        Profile mentor = profile("Daniel", "Kimani", "daniel@example.com", "mentor");
        CompanyProgramParticipant participant = participant(participantId, program, employee);

        CompanyProgramMentorAssignment assignment = new CompanyProgramMentorAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setParticipant(participant);
        assignment.setMentor(mentor);
        assignment.setAssignedAt(LocalDateTime.parse("2026-05-01T10:00:00"));

        CompanyProgramMatchWorkspace workspace = new CompanyProgramMatchWorkspace();
        workspace.setId(UUID.randomUUID());
        workspace.setParticipant(participant);
        workspace.setStatus(CompanyProgramMatchWorkspace.MatchStatus.ASSIGNED);
        workspace.setResolvedAt(LocalDateTime.parse("2026-05-01T12:00:00"));

        when(companyProgramParticipantRepository.findByCompanyProgram_Company_Id(companyId))
                .thenReturn(List.of(participant));
        when(mentorAssignmentRepository.findByParticipant_IdInAndJourneyInstanceStepIsNull(List.of(participantId)))
                .thenReturn(List.of(assignment));
        when(matchWorkspaceRepository.findByParticipant_IdIn(List.of(participantId)))
                .thenReturn(List.of(workspace));

        CompanyReportDtos.ReportListDto<CompanyReportDtos.ParticipantReportRowDto> report =
                companyReportService.getParticipantReport(companyId, 0, 20, null, null, null, null);

        assertThat(report.getRows()).hasSize(1);
        CompanyReportDtos.ParticipantReportRowDto row = report.getRows().get(0);
        assertThat(row.getId()).isEqualTo(participantId);
        assertThat(row.getCompanyProgramName()).isEqualTo("Leadership Accelerator");
        assertThat(row.getProfileName()).isEqualTo("Amina Mensah");
        assertThat(row.getProfileEmail()).isEqualTo("amina@example.com");
        assertThat(row.getProfileRole()).isEqualTo("mentee");
        assertThat(row.getMentorName()).isEqualTo("Daniel Kimani");
        assertThat(row.getMentorEmail()).isEqualTo("daniel@example.com");
        assertThat(row.getMatchStatus()).isEqualTo("ASSIGNED");
        assertThat(report.getTotalItems()).isEqualTo(1);
    }

    @Test
    void pulseCoverageReportReturnsProgramRowsWithCompletionRate() {
        UUID companyId = UUID.randomUUID();
        CompanyProgram program = companyProgram(companyId, "Career Growth");
        CompanyProgramParticipant participant = participant(UUID.randomUUID(), program, profile("Joy", "Ouma", "joy@example.com", "mentee"));

        ParticipantPulse completedBaseline = pulse(participant, ParticipantPulse.PulseType.BASELINE, ParticipantPulse.PulseStatus.COMPLETED);
        ParticipantPulse pendingProgramEnd = pulse(participant, ParticipantPulse.PulseType.PROGRAM_END, ParticipantPulse.PulseStatus.PENDING);

        when(participantPulseRepository.findByCompanyIdWithinCreatedAt(
                org.mockito.ArgumentMatchers.eq(companyId),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of(completedBaseline, pendingProgramEnd));

        CompanyReportDtos.ReportListDto<CompanyReportDtos.PulseCoverageReportRowDto> report =
                companyReportService.getPulseCoverageReport(companyId, 0, 20, null, null, null, null);

        assertThat(report.getRows()).hasSize(1);
        CompanyReportDtos.PulseCoverageReportRowDto row = report.getRows().get(0);
        assertThat(row.getCompanyProgramName()).isEqualTo("Career Growth");
        assertThat(row.getTotalPulses()).isEqualTo(2);
        assertThat(row.getCompletedPulses()).isEqualTo(1);
        assertThat(row.getPendingPulses()).isEqualTo(1);
        assertThat(row.getExpiredPulses()).isZero();
        assertThat(row.getBaselinePulses()).isEqualTo(1);
        assertThat(row.getProgramEndPulses()).isEqualTo(1);
        assertThat(row.getCompletionRate()).isEqualByComparingTo("50.00");
    }

    private CompanyProgram companyProgram(UUID companyId, String name) {
        Company company = new Company();
        company.setId(companyId);
        company.setName("Acme");

        CompanyProgram program = new CompanyProgram();
        program.setId(UUID.randomUUID());
        program.setCompany(company);
        program.setName(name);
        program.setStatus(CompanyProgram.CompanyProgramStatus.LIVE);
        program.setMatchingMode(CompanyProgram.MatchingMode.ADMIN_ASSIGN);
        program.setCreatedAt(LocalDateTime.parse("2026-04-01T09:00:00"));
        return program;
    }

    private CompanyProgramParticipant participant(UUID participantId, CompanyProgram program, Profile profile) {
        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(participantId);
        participant.setCompanyProgram(program);
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
        participant.setEnrolledAt(LocalDateTime.parse("2026-04-02T09:00:00"));
        participant.setCreatedAt(LocalDateTime.parse("2026-04-02T09:00:00"));
        return participant;
    }

    private Profile profile(String firstName, String lastName, String email, String role) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setEmail(email);
        profile.setUsername(email);
        profile.setRole(role);
        profile.setIndustry("Product");
        return profile;
    }

    private ParticipantPulse pulse(CompanyProgramParticipant participant,
                                   ParticipantPulse.PulseType pulseType,
                                   ParticipantPulse.PulseStatus status) {
        ParticipantPulse pulse = new ParticipantPulse();
        pulse.setId(UUID.randomUUID());
        pulse.setParticipant(participant);
        pulse.setPulseType(pulseType);
        pulse.setStatus(status);
        pulse.setCreatedAt(LocalDateTime.parse("2026-04-10T09:00:00"));
        return pulse;
    }
}
