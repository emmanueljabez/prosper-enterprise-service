package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SkillRepository;
import com.prosper.prospermentor.service.meeting.MeetingService;
import com.prosper.prospermentor.service.notification.SessionNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionBookingServiceCorporateAllocationTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MenteeProfileRepository menteeProfileRepository;
    @Mock private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock private CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    @Mock private SessionOutcomeRepository sessionOutcomeRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private MeetingService meetingService;
    @Mock private SessionNotificationService notificationService;
    @Mock private CalendarService calendarService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private MpesaService mpesaService;
    @Mock private CurrencyService currencyService;
    @Mock private NautixWhatsAppService nautixWhatsAppService;
    @Mock private ReviewWorkflowService reviewWorkflowService;
    @Mock private ParticipantConsentService participantConsentService;
    @Mock private JourneyInstanceService journeyInstanceService;
    @Mock private EmployeeSessionAllocationService employeeSessionAllocationService;
    @Mock private PersonalSessionCreditService personalSessionCreditService;

    @InjectMocks
    private SessionBookingService sessionBookingService;

    @Test
    void cancelSession_shouldReturnCorporateAllocationOnce() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setCorporateAllocationId(UUID.randomUUID());
        session.setCorporateAllocationConsumedAt(LocalDateTime.now().minusMinutes(10));
        session.setStatus(Session.SessionStatus.CONFIRMED);

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTEE, "Need to reschedule");

        verify(employeeSessionAllocationService).returnConsumedBooking(session.getCorporateAllocationId(), session.getId(), session.getMenteeId());
        assertThat(session.getCorporateAllocationReturnedAt()).isNotNull();
    }

    @Test
    void cancelSession_whenPaidPersonalBookingIsDeclinedByMentor_shouldIssueMenteeCredit() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setStatus(Session.SessionStatus.PENDING);
        session.setPaid(true);
        session.setPaymentStatus(Session.PaymentStatus.PAID);

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTOR, "Calendar conflict");

        verify(personalSessionCreditService).issueMentorDeclineCredit(session);
        verify(subscriptionService, never()).returnConsumedSessionForDeclinedBooking(any(UUID.class), any(), any());
        assertThat(session.getStatus()).isEqualTo(Session.SessionStatus.CANCELLED);
        assertThat(session.getCancelledBy()).isEqualTo(Session.CancelledBy.MENTOR);
        assertThat(session.getPaymentStatus()).isEqualTo(Session.PaymentStatus.PAID);
        assertThat(session.getPaid()).isTrue();
    }

    @Test
    void cancelSession_whenMentorDeclinesIndividualSubscriptionBooking_shouldReturnConsumedSubscriptionSession() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setStatus(Session.SessionStatus.PENDING);
        session.setEntitlementSource(Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION);
        session.setConsumedSubscriptionId(UUID.randomUUID());

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTOR, "Not available");

        verify(subscriptionService).returnConsumedSessionForDeclinedBooking(
                session.getMenteeId(),
                session.getConsumedSubscriptionId(),
                null
        );
        assertThat(session.getEntitlementReturnedAt()).isNotNull();
    }

    @Test
    void cancelSession_whenMenteeCancelsFutureIndividualSubscriptionBooking_shouldReturnConsumedSubscriptionSession() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setStatus(Session.SessionStatus.CONFIRMED);
        session.setScheduledStart(java.time.ZonedDateTime.now().plusDays(2));
        session.setScheduledEnd(java.time.ZonedDateTime.now().plusDays(2).plusHours(1));
        session.setEntitlementSource(Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION);
        session.setConsumedSubscriptionId(UUID.randomUUID());

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTEE, "Need to reschedule");

        verify(subscriptionService).returnConsumedSessionForDeclinedBooking(
                session.getMenteeId(),
                session.getConsumedSubscriptionId(),
                null
        );
        assertThat(session.getEntitlementReturnedAt()).isNotNull();
    }

    @Test
    void cancelSession_whenMenteeCancelsAfterScheduledStart_shouldNotReturnIndividualEntitlement() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setStatus(Session.SessionStatus.CONFIRMED);
        session.setScheduledStart(java.time.ZonedDateTime.now().minusHours(2));
        session.setScheduledEnd(java.time.ZonedDateTime.now().minusHours(1));
        session.setEntitlementSource(Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION);
        session.setConsumedSubscriptionId(UUID.randomUUID());

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTEE, "Missed it");

        verify(subscriptionService, never()).returnConsumedSessionForDeclinedBooking(
                any(UUID.class),
                any(),
                any()
        );
        assertThat(session.getEntitlementReturnedAt()).isNull();
    }

    @Test
    void cancelSession_whenMentorDeclinesAddonBackedBooking_shouldReturnConsumedAddonSession() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMentorId(UUID.randomUUID());
        session.setMenteeId(UUID.randomUUID());
        session.setStatus(Session.SessionStatus.PENDING);
        session.setEntitlementSource(Session.EntitlementSource.SUBSCRIPTION_ADDON);
        session.setConsumedSubscriptionId(UUID.randomUUID());
        session.setConsumedSubscriptionAddonId(UUID.randomUUID());

        Profile mentor = new Profile();
        mentor.setId(session.getMentorId());
        Profile mentee = new Profile();
        mentee.setId(session.getMenteeId());

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(session.getMentorId())).thenReturn(Optional.of(mentor));
        when(profileRepository.findById(session.getMenteeId())).thenReturn(Optional.of(mentee));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTOR, "Not available");

        verify(subscriptionService).returnConsumedSessionForDeclinedBooking(
                session.getMenteeId(),
                session.getConsumedSubscriptionId(),
                session.getConsumedSubscriptionAddonId()
        );
        assertThat(session.getEntitlementReturnedAt()).isNotNull();
    }
}
