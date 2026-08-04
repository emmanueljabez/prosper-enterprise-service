package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CreateSessionRequestDto;
import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionProposal;
import com.prosper.prospermentor.entity.SessionProposalSlot;
import com.prosper.prospermentor.entity.SessionSupportRequest;
import com.prosper.prospermentor.entity.Skill;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionProposalRepository;
import com.prosper.prospermentor.repository.SessionSupportRequestRepository;
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
import java.util.Map;
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
    private SessionProposalRepository sessionProposalRepository;
    @Mock
    private SessionSupportRequestRepository sessionSupportRequestRepository;
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
    @Mock
    private CompanyMentorEnrollmentService companyMentorEnrollmentService;

    @InjectMocks
    private SessionBookingService sessionBookingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionBookingService, "frontendUrl", "https://enterprise.prospermentor.com");
        lenient().when(employeeSessionAllocationService.findActiveAllocationForProfile(any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(companyMentorEnrollmentService.isMentorPubliclyDiscoverable(any(UUID.class)))
                .thenReturn(true);
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
                        "review/" + sessionId
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
    void createSessionRequest_shouldUseEligibilitySessionDuration() {
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.of(2026, 3, 18, 9, 0, 0, 0, ZoneId.of("Africa/Nairobi"));

        CreateSessionRequestDto request = CreateSessionRequestDto.builder()
                .mentorId(mentorId.toString())
                .menteeId(menteeId.toString())
                .skillId(skillId.toString())
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .scheduledStart(scheduledStart)
                .menteeMessage("Free trial intro call")
                .currency("USD")
                .build();

        Profile mentor = profile(mentorId, "mentor", "0712345678", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "+254700000001", "Ada", "Lovelace", "ada");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Career Growth");

        SessionBookingEligibility eligibility = SessionBookingEligibility.eligible("Eligible for a free trial.", 1, 0);
        eligibility.setSubscriptionSource(SessionBookingEligibility.SubscriptionSource.INDIVIDUAL);
        eligibility.setSessionDurationMinutes(30);

        when(subscriptionService.checkSessionBookingEligibility(menteeId)).thenReturn(eligibility);
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.empty());
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

        assertThat(created.getScheduledEnd()).isEqualTo(scheduledStart.plusMinutes(30));
        assertThat(created.getDurationMinutes()).isEqualTo(30);
    }

    @Test
    void createSessionRequest_shouldPersistStructuredQuestionnaireResponses() {
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ZonedDateTime scheduledStart = ZonedDateTime.of(2026, 3, 18, 9, 0, 0, 0, ZoneId.of("Africa/Nairobi"));

        CreateSessionRequestDto request = CreateSessionRequestDto.builder()
                .mentorId(mentorId.toString())
                .menteeId(menteeId.toString())
                .skillId(skillId.toString())
                .meetingPlatform(Session.MeetingPlatform.GOOGLE_MEET)
                .scheduledStart(scheduledStart)
                .questionnaireResponses(Map.of(
                        "primaryGoal", "Build an executive presence plan",
                        "alreadyTried", "Practiced weekly with my manager",
                        "successLooksLike", "A clear 30-day communication plan",
                        "contextDocument", Map.of(
                                "name", "career-plan.pdf",
                                "url", "/api/v1/sessions/context-documents/career-plan.pdf"
                        )
                ))
                .currency("USD")
                .build();

        Profile mentor = profile(mentorId, "mentor", "0712345678", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "+254700000001", "Ada", "Lovelace", "ada");

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Career Growth");

        when(subscriptionService.checkSessionBookingEligibility(menteeId))
                .thenReturn(SessionBookingEligibility.eligible("Eligible", 2, 0));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.empty());
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

        assertThat(created.getBookingPrimaryGoal()).isEqualTo("Build an executive presence plan");
        assertThat(created.getBookingAlreadyTried()).isEqualTo("Practiced weekly with my manager");
        assertThat(created.getBookingSuccessLooksLike()).isEqualTo("A clear 30-day communication plan");
        assertThat(created.getBookingContextDocument()).contains("career-plan.pdf");
    }

    @Test
    void proposeAlternative_shouldPersistSlotsAndNotifyMentee() {
        UUID sessionId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime requestedStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(7)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        ZonedDateTime alternativeStart = requestedStart.plusDays(1).withHour(14);
        ZonedDateTime secondAlternativeStart = requestedStart.plusDays(2).withHour(15);

        Session pendingSession = pendingSession(sessionId, mentorId, menteeId, requestedStart);
        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(sessionProposalRepository.findFirstBySessionIdAndStatusOrderByProposedAtDesc(
                sessionId,
                SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE
        )).thenReturn(Optional.empty());
        when(sessionProposalRepository.save(any(SessionProposal.class))).thenAnswer(invocation -> {
            SessionProposal saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.getSlots().forEach(slot -> slot.setId(UUID.randomUUID()));
            return saved;
        });

        SessionProposal proposal = sessionBookingService.proposeAlternative(
                sessionId,
                "I can make either of these slots work.",
                List.of(
                        slotRequest(alternativeStart, alternativeStart.plusMinutes(60)),
                        slotRequest(secondAlternativeStart, secondAlternativeStart.plusMinutes(60))
                )
        );

        assertThat(proposal.getStatus()).isEqualTo(SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE);
        assertThat(proposal.getProposalType()).isEqualTo(SessionProposal.ProposalType.MULTIPLE_SLOTS);
        assertThat(proposal.getSlots()).hasSize(2);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentee_session_alternative_proposed"),
                eq("+254700000003"),
                eq(List.of(
                        "Ada",
                        "Grace Hopper",
                        "Leadership Coaching",
                        alternativeStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
                                + " at "
                                + alternativeStart.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH))
                                + ", "
                                + secondAlternativeStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
                                + " at "
                                + secondAlternativeStart.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH)),
                        "I can make either of these slots work.",
                        "proposals/" + sessionId
                ))
        );
        verify(notificationService).sendAlternativeProposalToMentee(
                eq(proposal),
                eq(pendingSession),
                eq(mentor),
                eq(mentee)
        );
    }

    @Test
    void acceptProposal_shouldConfirmSessionAtSelectedSlotAndNotifyMentor() {
        UUID sessionId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime requestedStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(7)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        ZonedDateTime acceptedStart = requestedStart.plusDays(1).withHour(14);
        ZonedDateTime acceptedEnd = acceptedStart.plusMinutes(60);

        Session pendingSession = pendingSession(sessionId, mentorId, menteeId, requestedStart);
        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");
        MentorProfile mentorProfile = availableMentorProfile(mentorId);
        SessionProposal proposal = pendingProposal(proposalId, pendingSession, slotId, acceptedStart, acceptedEnd);
        MeetingDetails meetingDetails = MeetingDetails.builder()
                .meetingUrl("https://meet.example.com/123")
                .meetingId("123")
                .password("pass")
                .build();

        when(sessionProposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findById(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(sessionRepository.findBlockingSessions(mentorId, acceptedStart, acceptedEnd, sessionId)).thenReturn(List.of());
        when(meetingService.createMeeting(any(Session.class))).thenReturn(meetingDetails);
        when(calendarService.createCalendarEvent(any(Session.class), eq(meetingDetails))).thenReturn("calendar-event-1");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionProposalRepository.save(any(SessionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session confirmed = sessionBookingService.acceptProposal(sessionId, proposalId, slotId, "Works for me");

        assertThat(confirmed.getStatus()).isEqualTo(Session.SessionStatus.CONFIRMED);
        assertThat(confirmed.getScheduledStart()).isEqualTo(acceptedStart);
        assertThat(proposal.getStatus()).isEqualTo(SessionProposal.ProposalStatus.ACCEPTED);
        assertThat(proposal.getAcceptedSlotId()).isEqualTo(slotId);
        String expectedAcceptedTime = acceptedStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
                + " at "
                + acceptedStart.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH));
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_alternative_accepted"),
                eq("+254700000002"),
                eq(List.of(
                        "Grace",
                        "Ada Lovelace",
                        "Leadership Coaching",
                        expectedAcceptedTime,
                        sessionId.toString()
                ))
        );
        verify(nautixWhatsAppService, never()).sendTemplateMessage(
                eq("prosper_mentor_session_proposal_response"),
                any(),
                any()
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentee_session_confirmed"),
                eq("+254700000003"),
                any()
        );
        verify(notificationService).sendProposalResponseToMentor(
                eq(proposal),
                eq(confirmed),
                eq(mentor),
                eq(mentee),
                eq("accepted")
        );
    }

    @Test
    void declineProposal_shouldNotifyMentorUsingDeclinedTemplate() {
        UUID sessionId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime requestedStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(7)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        ZonedDateTime alternativeStart = requestedStart.plusDays(1).withHour(14);

        Session pendingSession = pendingSession(sessionId, mentorId, menteeId, requestedStart);
        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");
        SessionProposal proposal = pendingProposal(proposalId, pendingSession, slotId, alternativeStart, alternativeStart.plusMinutes(60));

        when(sessionProposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(sessionProposalRepository.save(any(SessionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionProposal declined = sessionBookingService.declineProposal(sessionId, proposalId, "That time does not work for me.");

        assertThat(declined.getStatus()).isEqualTo(SessionProposal.ProposalStatus.DECLINED);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_alternative_declined"),
                eq("+254700000002"),
                eq(List.of(
                        "Grace",
                        "Ada Lovelace",
                        "Leadership Coaching",
                        "That time does not work for me.",
                        sessionId.toString()
                ))
        );
        verify(nautixWhatsAppService, never()).sendTemplateMessage(
                eq("prosper_mentor_session_proposal_response"),
                any(),
                any()
        );
        verify(notificationService).sendProposalResponseToMentor(
                eq(proposal),
                eq(pendingSession),
                eq(mentor),
                eq(mentee),
                eq("declined")
        );
    }

    @Test
    void contactSupport_shouldPersistRequestAndNotifyRepresentativeAndRequester() {
        UUID sessionId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        ZonedDateTime requestedStart = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
                .plusDays(7)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        Session pendingSession = pendingSession(sessionId, mentorId, menteeId, requestedStart);
        Profile mentor = profile(mentorId, "mentor", "0700000002", "Grace", "Hopper", "grace");
        Profile mentee = profile(menteeId, "mentee", "0700000003", "Ada", "Lovelace", "ada");
        ReflectionTestUtils.setField(sessionBookingService, "mentorExperienceWhatsApp", "+254711111111");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(pendingSession));
        when(profileRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(sessionSupportRequestRepository.save(any(SessionSupportRequest.class))).thenAnswer(invocation -> {
            SessionSupportRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        SessionSupportRequest supportRequest = sessionBookingService.contactSupport(
                sessionId,
                SessionSupportRequest.RequesterType.MENTOR,
                "I need help coordinating a better time."
        );

        assertThat(supportRequest.getRequesterType()).isEqualTo(SessionSupportRequest.RequesterType.MENTOR);
        assertThat(supportRequest.getRequesterId()).isEqualTo(mentorId);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_mentor_support_contact_requested"),
                eq("+254700000002"),
                eq(List.of(
                        "Grace",
                        "Leadership Coaching",
                        "Ada Lovelace",
                        sessionId.toString()
                ))
        );
        verify(nautixWhatsAppService, never()).sendTemplateMessage(
                eq("prosper_session_support_request"),
                any(),
                any()
        );
        verify(nautixWhatsAppService, never()).sendTemplateMessage(
                eq("prosper_session_support_acknowledgement"),
                any(),
                any()
        );
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
                        sessionId.toString()
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
                        sessionId.toString()
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

    private static Session pendingSession(UUID sessionId, UUID mentorId, UUID menteeId, ZonedDateTime requestedStart) {
        Session session = new Session();
        session.setId(sessionId);
        session.setMentorId(mentorId);
        session.setMenteeId(menteeId);
        session.setTitle("Leadership Coaching");
        session.setMenteeMessage("I need support on executive communication");
        session.setScheduledStart(requestedStart);
        session.setScheduledEnd(requestedStart.plusHours(1));
        session.setStatus(Session.SessionStatus.PENDING);
        return session;
    }

    private static SessionProposalSlotRequest slotRequest(ZonedDateTime start, ZonedDateTime end) {
        return SessionProposalSlotRequest.builder()
                .scheduledStart(start)
                .scheduledEnd(end)
                .build();
    }

    private static SessionProposal pendingProposal(UUID proposalId,
                                                   Session session,
                                                   UUID slotId,
                                                   ZonedDateTime slotStart,
                                                   ZonedDateTime slotEnd) {
        SessionProposal proposal = new SessionProposal();
        proposal.setId(proposalId);
        proposal.setSession(session);
        proposal.setSessionId(session.getId());
        proposal.setStatus(SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE);
        proposal.setProposalType(SessionProposal.ProposalType.SINGLE_SLOT);

        SessionProposalSlot slot = new SessionProposalSlot();
        slot.setId(slotId);
        slot.setProposal(proposal);
        slot.setScheduledStart(slotStart);
        slot.setScheduledEnd(slotEnd);
        slot.setSortOrder(0);
        proposal.getSlots().add(slot);

        return proposal;
    }
}
