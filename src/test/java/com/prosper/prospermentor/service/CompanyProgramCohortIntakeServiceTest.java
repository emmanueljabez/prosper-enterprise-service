package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortSelfJoinRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinResponseDto;
import com.prosper.prospermentor.dto.AddCohortRosterParticipantsRequest;
import com.prosper.prospermentor.dto.CompanyProgramCohortJoinRequestDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.dto.CohortRosterParticipantRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramCohortJoinRequestRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.CompanyProgramCohortNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    @Mock
    private CompanyProgramCohortNotificationService notificationService;

    @InjectMocks
    private CompanyProgramCohortIntakeService service;

    @Test
    void addRosterParticipants_shouldCreatePendingRosterParticipantsFromUploadedRecords() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);
        Profile existing = companyProfile(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                cohort.getCompanyProgram().getCompany(),
                "Amina",
                "Otieno",
                "amina@example.com",
                "+254 712 000 000"
        );

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(profileRepository.findByEmailIgnoreCase("amina@example.com")).thenReturn(Optional.of(existing));
        when(cohortParticipantRepository.findByCohort_IdAndProfile_Id(cohortId, existing.getId())).thenReturn(Optional.empty());
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class))).thenAnswer(invocation -> {
            CompanyProgramCohortParticipant participant = invocation.getArgument(0);
            participant.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
            return participant;
        });

        List<CompanyProgramCohortParticipantDto> participants = service.addRosterParticipants(
                cohortId,
                AddCohortRosterParticipantsRequest.builder()
                        .participants(List.of(CohortRosterParticipantRequest.builder()
                                .firstName("Amina")
                                .lastName("Otieno")
                                .email(" AMINA@example.com ")
                                .phone("+254 712 000 000")
                                .chapter("Nairobi")
                                .region("Kenya")
                                .interestTags(List.of(" STEM ", "STEM", "Career readiness"))
                                .build()))
                        .build(),
                adminUserId
        );

        assertThat(participants).hasSize(1);
        CompanyProgramCohortParticipantDto participant = participants.get(0);
        assertThat(participant.getProfileId()).isEqualTo(existing.getId());
        assertThat(participant.getProfileEmail()).isEqualTo("amina@example.com");
        assertThat(participant.getSource()).isEqualTo(CompanyProgramCohortParticipant.ParticipantSource.ROSTER_UPLOAD);
        assertThat(participant.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING);
        assertThat(participant.getDuplicateStatus()).isEqualTo(CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        assertThat(participant.getInterestTags()).containsExactly("STEM", "Career readiness");
        verify(programParticipantRepository, never()).save(any(CompanyProgramParticipant.class));
        verify(notificationService).sendCohortParticipantAdded(any(CompanyProgramCohortParticipant.class));
    }

    @Test
    void addRosterParticipants_shouldCreateCompanyLinkedMenteeProfileWhenContactIsNew() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(profileRepository.findByEmailIgnoreCase("new.mentee@example.com")).thenReturn(Optional.empty());
        when(profileRepository.findByPhoneNormalized("254700000000")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.findByCohort_IdAndProfile_Id(eq(cohortId), any(UUID.class))).thenReturn(Optional.empty());
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class))).thenAnswer(invocation -> {
            CompanyProgramCohortParticipant participant = invocation.getArgument(0);
            participant.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
            return participant;
        });

        List<CompanyProgramCohortParticipantDto> participants = service.addRosterParticipants(
                cohortId,
                AddCohortRosterParticipantsRequest.builder()
                        .participants(List.of(CohortRosterParticipantRequest.builder()
                                .firstName("New")
                                .lastName("Mentee")
                                .email("new.mentee@example.com")
                                .phone("+254 700 000 000")
                                .chapter("Nairobi")
                                .region("Kenya")
                                .interestTags(List.of("Public speaking"))
                                .build()))
                        .build(),
                adminUserId
        );

        assertThat(participants).hasSize(1);
        verify(profileRepository).save(any(Profile.class));
        CompanyProgramCohortParticipantDto participant = participants.get(0);
        assertThat(participant.getProfileEmail()).isEqualTo("new.mentee@example.com");
        assertThat(participant.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING);
        assertThat(participant.getSource()).isEqualTo(CompanyProgramCohortParticipant.ParticipantSource.ROSTER_UPLOAD);
    }

    @Test
    void confirmParticipant_shouldNotifyParticipantAfterConfirmation() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID participantId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);
        Profile profile = companyProfile(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                cohort.getCompanyProgram().getCompany(),
                "Amina",
                "Otieno",
                "amina@example.com",
                "+254 712 000 000"
        );
        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(participantId);
        participant.setCohort(cohort);
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING);

        when(cohortParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(programParticipantRepository.findByCompanyProgram_IdAndProfile_IdIn(cohort.getCompanyProgram().getId(), List.of(profile.getId())))
                .thenReturn(List.of());
        when(programParticipantRepository.save(any(CompanyProgramParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyProgramCohortParticipantDto response = service.confirmParticipant(participantId, adminUserId);

        assertThat(response.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        verify(notificationService).sendCohortParticipantConfirmed(participant);
    }

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

        assertThat(response.getCompanyId()).isEqualTo(cohort.getCompanyProgram().getCompany().getId());
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

    @Test
    void getJoinRequests_shouldExposePendingSelfJoinRequestsForAdminReview() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = intakeOpenCohort(cohortId);
        CompanyProgramCohortJoinRequest joinRequest = new CompanyProgramCohortJoinRequest();
        joinRequest.setId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        joinRequest.setCohort(cohort);
        joinRequest.setSubmittedEmail("amina@example.com");
        joinRequest.setSubmittedFirstName("Amina");
        joinRequest.setSubmittedLastName("Otieno");
        joinRequest.setSubmittedInterestTags(List.of("STEM"));
        joinRequest.setStatus(CompanyProgramCohortJoinRequest.JoinRequestStatus.PENDING);

        when(joinRequestRepository.findByCohort_IdOrderByCreatedAtDesc(cohortId)).thenReturn(List.of(joinRequest));

        List<CompanyProgramCohortJoinRequestDto> requests = service.getJoinRequests(cohortId);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getId()).isEqualTo(joinRequest.getId());
        assertThat(requests.get(0).getSubmittedEmail()).isEqualTo("amina@example.com");
        assertThat(requests.get(0).getStatus()).isEqualTo(CompanyProgramCohortJoinRequest.JoinRequestStatus.PENDING);
        assertThat(requests.get(0).getInterestTags()).containsExactly("STEM");
    }

    @Test
    void confirmJoinRequest_shouldNotifyParticipantAfterConfirmation() {
        UUID joinRequestId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID profileId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID adminUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohort cohort = intakeOpenCohort(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        Profile profile = companyProfile(
                profileId,
                cohort.getCompanyProgram().getCompany(),
                "Amina",
                "Otieno",
                "amina@example.com",
                "+254 712 000 000"
        );
        CompanyProgramCohortJoinRequest joinRequest = new CompanyProgramCohortJoinRequest();
        joinRequest.setId(joinRequestId);
        joinRequest.setCohort(cohort);
        joinRequest.setSubmittedEmail("amina@example.com");
        joinRequest.setSubmittedFirstName("Amina");
        joinRequest.setSubmittedLastName("Otieno");
        joinRequest.setSubmittedInterestTags(List.of("STEM"));
        joinRequest.setStatus(CompanyProgramCohortJoinRequest.JoinRequestStatus.PENDING);

        when(joinRequestRepository.findById(joinRequestId)).thenReturn(Optional.of(joinRequest));
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(cohortParticipantRepository.findByCohort_IdAndProfile_Id(cohort.getId(), profileId)).thenReturn(Optional.empty());
        when(programParticipantRepository.findByCompanyProgram_IdAndProfile_IdIn(cohort.getCompanyProgram().getId(), List.of(profileId)))
                .thenReturn(List.of());
        when(programParticipantRepository.save(any(CompanyProgramParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(joinRequestRepository.save(any(CompanyProgramCohortJoinRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class))).thenAnswer(invocation -> {
            CompanyProgramCohortParticipant participant = invocation.getArgument(0);
            participant.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
            return participant;
        });

        CompanyProgramCohortParticipantDto response = service.confirmJoinRequest(joinRequestId, profileId, adminUserId);

        assertThat(response.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        verify(notificationService).sendCohortParticipantConfirmed(any(CompanyProgramCohortParticipant.class));
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

    private Profile companyProfile(UUID profileId,
                                   Company company,
                                   String firstName,
                                   String lastName,
                                   String email,
                                   String phone) {
        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setCompany(company);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setEmail(email);
        profile.setPhone(phone);
        profile.setUsername(email);
        profile.setRole("mentee");
        return profile;
    }
}
