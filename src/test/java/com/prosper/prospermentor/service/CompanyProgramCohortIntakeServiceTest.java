package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortSelfJoinRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinResponseDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramCohortJoinRequestRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramCohortIntakeServiceTest {

    @Mock
    private CompanyProgramCohortRepository cohortRepository;
    @Mock
    private CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    @Mock
    private CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    @Mock
    private CompanyProgramCohortJoinRequestRepository joinRequestRepository;
    @Mock
    private CompanyProgramParticipantRepository programParticipantRepository;
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private CompanyProgramCohortIntakeService service;

    @Test
    void submitSelfJoin_shouldCreatePendingJoinRequestWhenNoDuplicateExists() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);
        when(cohortRepository.findBySelfJoinCodeHashAndSelfJoinEnabledTrue(anyString())).thenReturn(Optional.of(cohort));
        when(profileRepository.findByEmailIgnoreCase("amina@example.com")).thenReturn(Optional.empty());
        when(profileRepository.findByPhoneNormalized("254712000000")).thenReturn(Optional.empty());
        when(joinRequestRepository.save(any(CompanyProgramCohortJoinRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CohortSelfJoinResponseDto response = service.submitSelfJoin("JOIN-CODE", CohortSelfJoinRequest.builder()
                .email("amina@example.com")
                .phone("254712000000")
                .firstName("Amina")
                .lastName("Otieno")
                .interestTags(List.of("STEM"))
                .build());

        assertThat(response.getStatus()).isEqualTo(CompanyProgramCohortJoinRequest.JoinRequestStatus.PENDING);
        assertThat(response.isDuplicateReviewRequired()).isFalse();
    }

    @Test
    void submitSelfJoin_shouldFlagDuplicateReviewWhenEmailMatchesProfile() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);
        Profile existing = new Profile();
        existing.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        existing.setEmail("amina@example.com");

        when(cohortRepository.findBySelfJoinCodeHashAndSelfJoinEnabledTrue(anyString())).thenReturn(Optional.of(cohort));
        when(profileRepository.findByEmailIgnoreCase("amina@example.com")).thenReturn(Optional.of(existing));
        when(joinRequestRepository.save(any(CompanyProgramCohortJoinRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CohortSelfJoinResponseDto response = service.submitSelfJoin("JOIN-CODE", CohortSelfJoinRequest.builder()
                .email("amina@example.com")
                .firstName("Amina")
                .lastName("Otieno")
                .interestTags(List.of("STEM"))
                .build());

        assertThat(response.getStatus()).isEqualTo(CompanyProgramCohortJoinRequest.JoinRequestStatus.DUPLICATE_REVIEW);
        assertThat(response.isDuplicateReviewRequired()).isTrue();
        assertThat(response.getMatchedProfileId()).isEqualTo(existing.getId());
    }

    @Test
    void recordPlenaryAttendance_shouldMarkConfirmedParticipantAsAttended() {
        UUID participantId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(participantId);
        participant.setCohort(intakeOpenCohort(UUID.fromString("55555555-5555-5555-5555-555555555555")));
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);

        when(cohortParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(attendanceRepository.findByCohortParticipant_Id(participantId)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(CompanyProgramCohortPlenaryAttendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyProgramCohortParticipantDto response = service.recordPlenaryAttendance(
                participantId,
                CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED,
                CompanyProgramCohortPlenaryAttendance.AttendanceSource.ADMIN_OVERRIDE,
                adminUserId
        );

        assertThat(response.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED);
        verify(attendanceRepository).save(any(CompanyProgramCohortPlenaryAttendance.class));
    }

    private CompanyProgramCohort intakeOpenCohort(UUID cohortId) {
        Company company = new Company();
        company.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        company.setName("G4G");

        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        companyProgram.setName("G4G Mentorship");
        companyProgram.setCompany(company);

        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setId(cohortId);
        cohort.setCompanyProgram(companyProgram);
        cohort.setName("G4G Nairobi - Q3 2026");
        cohort.setCode("G4G-NBO-Q3-2026");
        cohort.setStatus(CompanyProgramCohort.CohortStatus.INTAKE_OPEN);
        cohort.setSelfJoinEnabled(true);
        cohort.setSelfJoinCapacity(20);
        return cohort;
    }
}
