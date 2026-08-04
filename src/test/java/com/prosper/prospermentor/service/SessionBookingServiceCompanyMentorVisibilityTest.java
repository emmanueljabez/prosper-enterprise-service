package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CreateSessionRequestDto;
import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionProposalRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SessionSupportRequestRepository;
import com.prosper.prospermentor.repository.SkillRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.meeting.MeetingService;
import com.prosper.prospermentor.service.notification.SessionNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionBookingServiceCompanyMentorVisibilityTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID PROGRAM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MENTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MENTEE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID SKILL_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private MenteeProfileRepository menteeProfileRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private MeetingService meetingService;
    @Mock
    private SessionNotificationService notificationService;
    @Mock
    private CalendarService calendarService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private MpesaService mpesaService;
    @Mock
    private CurrencyService currencyService;
    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock
    private CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    @Mock
    private SessionOutcomeRepository sessionOutcomeRepository;
    @Mock
    private NautixWhatsAppService nautixWhatsAppService;
    @Mock
    private ReviewWorkflowService reviewWorkflowService;
    @Mock
    private ParticipantConsentService participantConsentService;
    @Mock
    private JourneyInstanceService journeyInstanceService;
    @Mock
    private EmployeeSessionAllocationService employeeSessionAllocationService;
    @Mock
    private PersonalSessionCreditService personalSessionCreditService;
    @Mock
    private SessionProposalRepository sessionProposalRepository;
    @Mock
    private SessionSupportRequestRepository sessionSupportRequestRepository;
    @Mock
    private CompanyMentorEnrollmentService companyMentorEnrollmentService;

    @InjectMocks
    private SessionBookingService service;

    @Test
    void createSessionRequest_shouldRejectPublicBookingForCompanyPrivateMentor() {
        stubEligibleBooking();
        stubMentorAndMentee(null);
        when(companyMentorEnrollmentService.isMentorPubliclyDiscoverable(MENTOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createSessionRequest(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected mentor is not available for public booking");
        verify(skillRepository, never()).findById(SKILL_ID);
    }

    @Test
    void createSessionRequest_shouldRejectCompanyProgramBookingWhenMentorMembershipWasRemoved() {
        stubEligibleBooking();
        stubMentorAndMentee(company(COMPANY_ID));
        when(companyMentorEnrollmentService.canCompanyBookMentor(COMPANY_ID, PROGRAM_ID, MENTOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createSessionRequest(request(PROGRAM_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected mentor is not available for this company program");
        verify(skillRepository, never()).findById(SKILL_ID);
    }

    @Test
    void createSessionRequest_shouldRejectCompanyPrivateMentorForOtherCompany() {
        stubEligibleBooking();
        stubMentorAndMentee(company(OTHER_COMPANY_ID));
        when(companyMentorEnrollmentService.canCompanyBookMentor(OTHER_COMPANY_ID, PROGRAM_ID, MENTOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createSessionRequest(request(PROGRAM_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected mentor is not available for this company program");
        verify(skillRepository, never()).findById(SKILL_ID);
    }

    @Test
    void createSessionRequest_shouldContinueForCompanyBookableProgramMentor() {
        stubEligibleBooking();
        stubMentorAndMentee(company(COMPANY_ID));
        when(companyMentorEnrollmentService.canCompanyBookMentor(COMPANY_ID, PROGRAM_ID, MENTOR_ID)).thenReturn(true);
        when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSessionRequest(request(PROGRAM_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Skill not found");
        verify(skillRepository).findById(SKILL_ID);
    }

    private void stubEligibleBooking() {
        when(subscriptionService.checkSessionBookingEligibility(MENTEE_ID))
                .thenReturn(SessionBookingEligibility.builder()
                        .canBook(true)
                        .message("Eligible")
                        .reason(SessionBookingEligibility.EligibilityReason.ELIGIBLE)
                        .build());
    }

    private void stubMentorAndMentee(Company menteeCompany) {
        when(profileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(profile(MENTOR_ID, "mentor", null)));
        when(profileRepository.findById(MENTEE_ID)).thenReturn(Optional.of(profile(MENTEE_ID, "mentee", menteeCompany)));
    }

    private CreateSessionRequestDto request(UUID companyProgramId) {
        return CreateSessionRequestDto.builder()
                .mentorId(MENTOR_ID.toString())
                .menteeId(MENTEE_ID.toString())
                .skillId(SKILL_ID.toString())
                .scheduledStart(ZonedDateTime.now().plusDays(1))
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .companyProgramId(companyProgramId != null ? companyProgramId.toString() : null)
                .build();
    }

    private Profile profile(UUID id, String role, Company company) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole(role);
        profile.setCompany(company);
        return profile;
    }

    private Company company(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
