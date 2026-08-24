package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortWorkspaceDto;
import com.prosper.prospermentor.dto.CreateCompanyProgramCohortRequest;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramCohortDto;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortJoinRequestRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramCohortServiceTest {

    @Mock
    private CompanyProgramRepository companyProgramRepository;
    @Mock
    private CompanyProgramCohortRepository cohortRepository;
    @Mock
    private CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    @Mock
    private CompanyProgramCohortJoinRequestRepository joinRequestRepository;
    @Mock
    private CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    @Mock
    private CommonInterestCircleRepository circleRepository;
    @Mock
    private CommonInterestCircleMembershipRepository membershipRepository;
    @Mock
    private CompanyProgramCohortGateService cohortGateService;

    @InjectMocks
    private CompanyProgramCohortService service;

    @Test
    void createCohort_shouldDefaultCircleSizeAndDraftStatus() {
        UUID programId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgram program = new CompanyProgram();
        program.setId(programId);
        program.setName("G4G Mentorship");

        when(companyProgramRepository.findById(programId)).thenReturn(Optional.of(program));
        when(cohortRepository.save(any(CompanyProgramCohort.class))).thenAnswer(invocation -> {
            CompanyProgramCohort cohort = invocation.getArgument(0);
            cohort.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
            return cohort;
        });

        CreateCompanyProgramCohortRequest request = CreateCompanyProgramCohortRequest.builder()
                .name("G4G Nairobi - Q3 2026")
                .code("G4G-NBO-Q3-2026")
                .region("Nairobi")
                .chapter("Nairobi")
                .selfJoinEnabled(true)
                .build();

        CompanyProgramCohortDto dto = service.createCohort(programId, request, userId);

        assertThat(dto.getCompanyProgramId()).isEqualTo(programId);
        assertThat(dto.getStatus()).isEqualTo(CompanyProgramCohort.CohortStatus.DRAFT);
        assertThat(dto.getCircleMinSize()).isEqualTo(5);
        assertThat(dto.getCircleMaxSize()).isEqualTo(10);
        assertThat(dto.isSelfJoinEnabled()).isTrue();
    }

    @Test
    void createCohort_shouldHashSelfJoinCodeWhenSelfJoinIsEnabled() {
        UUID programId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgram program = new CompanyProgram();
        program.setId(programId);

        when(companyProgramRepository.findById(programId)).thenReturn(Optional.of(program));
        when(cohortRepository.save(argThat(cohort ->
                "fb1c4aff6ab042ebd20a88b815d25c987a481c59d1c37732489ab878e1d4b9f8".equals(cohort.getSelfJoinCodeHash())
        ))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createCohort(programId, CreateCompanyProgramCohortRequest.builder()
                .name("G4G Nairobi - Q3 2026")
                .code("join-code")
                .selfJoinEnabled(true)
                .build(), userId);
    }

    @Test
    void getCohortDashboard_shouldSummarizeCohortProgress() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setId(cohortId);

        CompanyProgramCohortParticipant pending = participant(CompanyProgramCohortParticipant.ParticipantSource.SELF_JOIN,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING,
                CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        CompanyProgramCohortParticipant duplicate = participant(CompanyProgramCohortParticipant.ParticipantSource.SELF_JOIN,
                CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED,
                CompanyProgramCohortParticipant.DuplicateStatus.POSSIBLE_DUPLICATE);
        CompanyProgramCohortParticipant attended = participant(CompanyProgramCohortParticipant.ParticipantSource.MANUAL_ADD,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED,
                CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        CompanyProgramCohortParticipant matched = participant(CompanyProgramCohortParticipant.ParticipantSource.MANUAL_ADD,
                CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        List<CompanyProgramCohortParticipant> participants = List.of(pending, duplicate, attended, matched);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(cohortParticipantRepository.findByCohort_Id(cohortId)).thenReturn(participants);
        when(attendanceRepository.findByCohort_Id(cohortId)).thenReturn(List.of(attendance(attended), attendance(matched)));
        when(circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId)).thenReturn(List.of(new CommonInterestCircle(), new CommonInterestCircle()));
        when(membershipRepository.findByCircle_Cohort_Id(cohortId)).thenReturn(List.of(membership(attended), membership(matched)));

        CompanyProgramCohortWorkspaceDto dashboard = service.getCohortDashboard(cohortId);

        assertThat(dashboard.getEnrolledCount()).isEqualTo(4);
        assertThat(dashboard.getSelfJoinedCount()).isEqualTo(2);
        assertThat(dashboard.getPendingConfirmationCount()).isEqualTo(1);
        assertThat(dashboard.getDuplicateReviewCount()).isEqualTo(1);
        assertThat(dashboard.getPlenaryAttendedCount()).isEqualTo(2);
        assertThat(dashboard.getPlenaryAttendanceRate()).isEqualTo(50.0);
        assertThat(dashboard.getCircleCount()).isEqualTo(2);
        assertThat(dashboard.getUnplacedCount()).isEqualTo(2);
        assertThat(dashboard.getMatchedCount()).isEqualTo(1);
        assertThat(dashboard.getMatchCompletionRate()).isEqualTo(25.0);
    }

    @Test
    void getEmployeeCohorts_shouldReturnStageStatusForProfile() {
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramCohortParticipant participant = cohortParticipant(profileId);
        when(cohortParticipantRepository.findByProfile_Id(profileId)).thenReturn(List.of(participant));
        when(cohortGateService.resolveGateStatusForCohortParticipant(participant.getId())).thenReturn(
                CohortGateStatusDto.builder()
                        .cohortParticipantId(participant.getId())
                        .confirmed(true)
                        .plenaryAttended(true)
                        .placedInCircle(true)
                        .eligibleForMatching(true)
                        .build()
        );

        List<EmployeeCompanyProgramCohortDto> cohorts = service.getEmployeeCohorts(profileId);

        assertThat(cohorts).hasSize(1);
        assertThat(cohorts.get(0).getStages().getPlenary().getStatus()).isEqualTo("ATTENDED");
        assertThat(cohorts.get(0).getStages().getCircle().getStatus()).isEqualTo("ACTIVE");
        assertThat(cohorts.get(0).getStages().getOneToOne().getStatus()).isEqualTo("READY_FOR_MATCHING");
    }

    private CompanyProgramCohortParticipant participant(CompanyProgramCohortParticipant.ParticipantSource source,
                                                       CompanyProgramCohortParticipant.CohortParticipantStatus status,
                                                       CompanyProgramCohortParticipant.DuplicateStatus duplicateStatus) {
        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(UUID.randomUUID());
        participant.setSource(source);
        participant.setStatus(status);
        participant.setDuplicateStatus(duplicateStatus);
        return participant;
    }

    private CompanyProgramCohortPlenaryAttendance attendance(CompanyProgramCohortParticipant participant) {
        CompanyProgramCohortPlenaryAttendance attendance = new CompanyProgramCohortPlenaryAttendance();
        attendance.setCohortParticipant(participant);
        attendance.setStatus(CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED);
        return attendance;
    }

    private CommonInterestCircleMembership membership(CompanyProgramCohortParticipant participant) {
        CommonInterestCircleMembership membership = new CommonInterestCircleMembership();
        membership.setCohortParticipant(participant);
        membership.setStatus(CommonInterestCircleMembership.MembershipStatus.PLACED);
        return membership;
    }

    private CompanyProgramCohortParticipant cohortParticipant(UUID profileId) {
        Company company = new Company();
        company.setId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        company.setName("Girls 4 Girls");

        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        companyProgram.setName("G4G Mentorship");
        companyProgram.setCompany(company);

        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        cohort.setName("G4G Nairobi - Q3 2026");
        cohort.setChapter("Nairobi");
        cohort.setRegion("Kenya");
        cohort.setStatus(CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED);
        cohort.setCompanyProgram(companyProgram);

        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setEmail("amina@example.com");

        CompanyProgramParticipant companyProgramParticipant = new CompanyProgramParticipant();
        companyProgramParticipant.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        companyProgramParticipant.setCompanyProgram(companyProgram);
        companyProgramParticipant.setProfile(profile);

        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        participant.setCohort(cohort);
        participant.setProfile(profile);
        participant.setCompanyProgramParticipant(companyProgramParticipant);
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE);
        return participant;
    }
}
