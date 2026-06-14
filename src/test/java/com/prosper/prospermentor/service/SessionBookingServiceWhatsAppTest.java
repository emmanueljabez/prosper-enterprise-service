package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CreateSessionRequestDto;
import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Skill;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SkillRepository;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import com.prosper.prospermentor.service.meeting.MeetingService;
import com.prosper.prospermentor.service.notification.SessionNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionBookingServiceWhatsAppTest {

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
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock
    private CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    @Mock
    private SessionOutcomeRepository sessionOutcomeRepository;
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

    @InjectMocks
    private SessionBookingService sessionBookingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionBookingService, "frontendUrl", "https://enterprise.prospermentor.com");
        lenient().when(employeeSessionAllocationService.findActiveAllocationForProfile(any(UUID.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    void createSessionRequest_shouldSendMentorAndMenteeTemplateMessages() {
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.of(2026, 3, 15, 14, 30, 0, 0, ZoneId.of("Africa/Nairobi"));

        CreateSessionRequestDto request = CreateSessionRequestDto.builder()
                .mentorId(mentorId.toString())
                .menteeId(menteeId.toString())
                .skillId(skillId.toString())
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .scheduledStart(scheduledStart)
                .menteeMessage("Looking for career guidance")
                .currency("USD")
                .build();

        Profile mentor = profile(mentorId, "mentor", "0712345678", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "+254700000001", "Ada", "Lovelace", "ada");
        mentee.setLinkedinUrl("https://linkedin.com/in/ada");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Career Growth");

        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(mentorId);
        mentorProfile.setHourlyRate(new BigDecimal("120.00"));

        when(subscriptionService.checkSessionBookingEligibility(menteeId))
                .thenReturn(SessionBookingEligibility.eligible("Eligible", 2, 0));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(currencyService.convertToUserCurrency(any(BigDecimal.class), eq("USD")))
                .thenReturn(new BigDecimal("120.00"));
        when(subscriptionService.consumeSessionForBooking(menteeId))
                .thenReturn(SubscriptionService.SessionConsumptionResult.individualSubscription(subscriptionId));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(sessionId);
            }
            return saved;
        });

        Session created = sessionBookingService.createSessionRequest(request);

        String expectedDate = scheduledStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));
        assertThat(created.getId()).isEqualTo(sessionId);
        assertThat(created.getEntitlementSource()).isEqualTo(Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION);
        assertThat(created.getConsumedSubscriptionId()).isEqualTo(subscriptionId);

        verify(notificationService).sendSessionNotificationToMentor(any(Session.class), eq(mentor), eq(mentee), isNull(), eq(skill));
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_session_request"),
                eq("+254712345678"),
                eq(List.of(
                        "Grace",
                        "Ada Lovelace",
                        "https://linkedin.com/in/ada",
                        "Career Growth",
                        expectedDate,
                        "Looking for career guidance",
                        "https://enterprise.prospermentor.com/app/sessions/review/" + sessionId
                ))
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentee_session_request_notification"),
                eq("+254700000001"),
                eq(List.of(
                        "Ada",
                        "Grace Hopper",
                        "Career Growth",
                        expectedDate
                ))
        );
    }

    @Test
    void createSessionRequest_withInvalidMentorPhone_shouldSkipMentorWhatsApp() {
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.of(2026, 3, 15, 14, 30, 0, 0, ZoneId.of("Africa/Nairobi"));

        CreateSessionRequestDto request = CreateSessionRequestDto.builder()
                .mentorId(mentorId.toString())
                .menteeId(menteeId.toString())
                .skillId(skillId.toString())
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .scheduledStart(scheduledStart)
                .menteeMessage("Looking for career guidance")
                .currency("USD")
                .build();

        Profile mentor = profile(mentorId, "mentor", "invalid-phone", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "+254700000001", "Ada", "Lovelace", "ada");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Career Growth");

        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(mentorId);
        mentorProfile.setHourlyRate(new BigDecimal("120.00"));

        when(subscriptionService.checkSessionBookingEligibility(menteeId))
                .thenReturn(SessionBookingEligibility.eligible("Eligible", 2, 0));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(currencyService.convertToUserCurrency(any(BigDecimal.class), eq("USD")))
                .thenReturn(new BigDecimal("120.00"));
        when(subscriptionService.consumeSessionForBooking(menteeId))
                .thenReturn(SubscriptionService.SessionConsumptionResult.individualSubscription(subscriptionId));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(sessionId);
            }
            return saved;
        });

        sessionBookingService.createSessionRequest(request);

        verify(nautixWhatsAppService, never()).sendTemplateMessage(
                eq("prosper_mentor_session_request"),
                any(),
                any()
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentee_session_request_notification"),
                eq("+254700000001"),
                any()
        );
    }

    @Test
    void createSessionRequest_withPersonalCreditEligibility_shouldConsumeCreditInsteadOfSubscriptionSession() {
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.of(2026, 3, 18, 9, 0, 0, 0, ZoneId.of("Africa/Nairobi"));

        CreateSessionRequestDto request = CreateSessionRequestDto.builder()
                .mentorId(mentorId.toString())
                .menteeId(menteeId.toString())
                .skillId(skillId.toString())
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .scheduledStart(scheduledStart)
                .menteeMessage("Rebooking from previous credit")
                .currency("USD")
                .build();

        Profile mentor = profile(mentorId, "mentor", "0712345678", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "+254700000001", "Ada", "Lovelace", "ada");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Career Growth");

        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(mentorId);
        mentorProfile.setHourlyRate(new BigDecimal("120.00"));

        SessionBookingEligibility eligibility = SessionBookingEligibility.eligible("You have a credited session available.", 1, 0);
        eligibility.setSubscriptionSource(SessionBookingEligibility.SubscriptionSource.PERSONAL_CREDIT);

        when(subscriptionService.checkSessionBookingEligibility(menteeId)).thenReturn(eligibility);
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(currencyService.convertToUserCurrency(any(BigDecimal.class), eq("USD")))
                .thenReturn(new BigDecimal("120.00"));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(sessionId);
            }
            return saved;
        });

        Session created = sessionBookingService.createSessionRequest(request);

        verify(personalSessionCreditService).consumeNextCredit(menteeId, sessionId);
        verify(subscriptionService, never()).consumeSession(menteeId);
        verify(subscriptionService, never()).consumeSessionForBooking(menteeId);
        assertThat(created.getPaymentStatus()).isEqualTo(Session.PaymentStatus.PAID);
        assertThat(created.getPaid()).isTrue();
        assertThat(created.getEntitlementSource()).isEqualTo(Session.EntitlementSource.PERSONAL_CREDIT);
    }

    @Test
    void confirmSession_shouldSendMentorAndMenteeConfirmationTemplateMessages() {
        UUID sessionId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(10)
                .withHour(16)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        Session pendingSession = new Session();
        pendingSession.setId(sessionId);
        pendingSession.setMentorId(mentorId);
        pendingSession.setMenteeId(menteeId);
        pendingSession.setTitle("Leadership Coaching");
        pendingSession.setMenteeMessage("I need support on executive communication");
        pendingSession.setScheduledStart(scheduledStart);
        pendingSession.setScheduledEnd(scheduledStart.plusHours(1));
        pendingSession.setStatus(Session.SessionStatus.PENDING);

        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");
        MentorProfile mentorProfile = availableMentorProfile(mentorId);

        MeetingDetails meetingDetails = MeetingDetails.builder()
                .meetingUrl("https://meet.example.com/123")
                .meetingId("123")
                .password("pass")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(meetingService.createMeeting(pendingSession)).thenReturn(meetingDetails);
        when(calendarService.createCalendarEvent(eq(pendingSession), eq(meetingDetails))).thenReturn("calendar-event-1");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(sessionRepository.findBlockingSessions(mentorId, scheduledStart, scheduledStart.plusHours(1), sessionId))
                .thenReturn(List.of());

        Session confirmed = sessionBookingService.confirmSession(sessionId, "See you soon");

        String expectedDate = scheduledStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));
        String expectedTime = scheduledStart.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH));

        assertThat(confirmed.getStatus()).isEqualTo(Session.SessionStatus.CONFIRMED);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_session_confirmed"),
                eq("+254700000002"),
                eq(List.of(
                        "Grace",
                        "Ada Lovelace",
                        "Leadership Coaching",
                        expectedDate,
                        expectedTime,
                        "I need support on executive communication",
                        "https://enterprise.prospermentor.com/app/sessions/" + sessionId
                ))
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentee_session_confirmed"),
                eq("+254700000003"),
                eq(List.of(
                        "Ada",
                        "Grace Hopper",
                        "Leadership Coaching",
                        expectedDate,
                        expectedTime,
                        "See you soon"
                ))
        );
    }

    @Test
    void confirmSession_shouldUseMentorSelectedScheduleWhenProvided() {
        UUID sessionId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime requestedStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(11)
                .withHour(16)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        ZonedDateTime confirmedStart = requestedStart.withHour(18).withMinute(30);
        ZonedDateTime confirmedEnd = confirmedStart.plusHours(1);

        Session pendingSession = new Session();
        pendingSession.setId(sessionId);
        pendingSession.setMentorId(mentorId);
        pendingSession.setMenteeId(menteeId);
        pendingSession.setTitle("Leadership Coaching");
        pendingSession.setMenteeMessage("I need support on executive communication");
        pendingSession.setScheduledStart(requestedStart);
        pendingSession.setScheduledEnd(requestedStart.plusHours(1));
        pendingSession.setStatus(Session.SessionStatus.PENDING);

        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");
        MentorProfile mentorProfile = availableMentorProfile(mentorId);

        MeetingDetails meetingDetails = MeetingDetails.builder()
                .meetingUrl("https://meet.example.com/123")
                .meetingId("123")
                .password("pass")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(sessionRepository.findBlockingSessions(mentorId, confirmedStart, confirmedEnd, sessionId))
                .thenReturn(List.of());
        when(meetingService.createMeeting(any(Session.class))).thenReturn(meetingDetails);
        when(calendarService.createCalendarEvent(any(Session.class), eq(meetingDetails))).thenReturn("calendar-event-1");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session confirmed = sessionBookingService.confirmSession(sessionId, "See you soon", confirmedStart);

        String expectedDate = confirmedStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));
        String expectedTime = confirmedStart.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH));

        assertThat(confirmed.getScheduledStart()).isEqualTo(confirmedStart);
        assertThat(confirmed.getScheduledEnd()).isEqualTo(confirmedEnd);
        verify(sessionRepository).findBlockingSessions(mentorId, confirmedStart, confirmedEnd, sessionId);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_session_confirmed"),
                eq("+254700000002"),
                eq(List.of(
                        "Grace",
                        "Ada Lovelace",
                        "Leadership Coaching",
                        expectedDate,
                        expectedTime,
                        "I need support on executive communication",
                        "https://enterprise.prospermentor.com/app/sessions/" + sessionId
                ))
        );
    }

    @Test
    void markSessionComplete_shouldOpenReviewWorkflow() {
        UUID sessionId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi")).minusHours(2);

        Session confirmedSession = new Session();
        confirmedSession.setId(sessionId);
        confirmedSession.setMentorId(mentorId);
        confirmedSession.setMenteeId(menteeId);
        confirmedSession.setTitle("Leadership Coaching");
        confirmedSession.setScheduledStart(scheduledStart);
        confirmedSession.setScheduledEnd(scheduledStart.plusHours(1));
        confirmedSession.setStatus(Session.SessionStatus.CONFIRMED);

        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(confirmedSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session completed = sessionBookingService.markSessionComplete(sessionId);

        assertThat(completed.getStatus()).isEqualTo(Session.SessionStatus.COMPLETED);
        verify(reviewWorkflowService).openSessionReviewCycle(completed, mentor, mentee);
        verify(reviewWorkflowService).maybeOpenFitReviewCycle(completed, mentor, mentee);
    }

    private static Profile profile(UUID id, String role, String phone, String firstName, String lastName, String username) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole(role);
        profile.setPhone(phone);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setUsername(username);
        return profile;
    }

    private static MentorProfile availableMentorProfile(UUID mentorId) {
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(mentorId);
        mentorProfile.setIsAvailable(true);
        return mentorProfile;
    }
}
