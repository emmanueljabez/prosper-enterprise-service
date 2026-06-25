package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Skill;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionBookingNotificationTemplateTest {

    @Test
    void mentorBookingNotificationTemplate_shouldRenderRedesignedPendingRequestWithExistingActions() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Profile mentor = new Profile();
        mentor.setFirstName("Danielle");

        Profile mentee = new Profile();
        mentee.setFirstName("Alex");
        mentee.setLastName("Ogango");
        mentee.setAvatarUrl("https://enterprise.prospermentor.com/images/alex.jpg");

        Session session = new Session();
        session.setId(sessionId);
        session.setTitle("Editorial Strategy");
        session.setMeetingPlatform(Session.MeetingPlatform.GOOGLE_MEET);
        session.setScheduledStart(ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        session.setScheduledEnd(ZonedDateTime.of(2026, 10, 24, 11, 0, 0, 0, ZoneId.of("Africa/Nairobi")));

        Context context = new Context();
        context.setVariable("mentor", mentor);
        context.setVariable("mentee", mentee);
        context.setVariable("session", session);
        context.setVariable("appName", "ProsperMentor");
        context.setVariable("baseUrl", "https://api.prospermentor.com");
        context.setVariable("frontendUrl", "https://enterprise.prospermentor.com");
        context.setVariable("sessionDate", "Oct 24, 2026");
        context.setVariable("sessionTime", "10:00 AM");

        String html = templateEngine().process("email/booking-notification-mentor", context);

        assertThat(html)
                .contains("PENDING REQUEST")
                .contains("Booking<br>")
                .contains("in progress.")
                .contains("Dear Danielle,")
                .contains("Alex Ogango")
                .contains("Booking status")
                .contains("Pending")
                .contains("VERIFIED MENTEE")
                .contains("SESSION TOPIC")
                .contains("Editorial Strategy")
                .contains("DATE")
                .contains("Oct 24, 2026")
                .contains("TIME")
                .contains("10:00 AM")
                .contains("Video Call Session")
                .contains("60 Minutes")
                .contains("Accept Session")
                .contains("https://enterprise.prospermentor.com/app/sessions/review/" + sessionId + "?action=accept")
                .contains("Decline Request")
                .contains("https://enterprise.prospermentor.com/app/sessions/review/" + sessionId + "?action=decline")
                .contains("Reschedule Session")
                .contains("https://enterprise.prospermentor.com/app/sessions/review/" + sessionId)
                .contains("Support")
                .contains("Privacy Policy")
                .contains("Unsubscribe");
    }

    @Test
    void sendSessionNotificationToMentor_shouldProvideCompactDateAndTimeForBookingRequest() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TemplateEngine engine = mock(TemplateEngine.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        EmailInterface emailInterface = mock(EmailInterface.class);
        SessionNotificationService service = new SessionNotificationService(mailSender, engine, profileRepository, emailInterface);
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.prospermentor.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");

        Session session = new Session();
        session.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        session.setTitle("Editorial Strategy");
        session.setScheduledStart(ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        session.setScheduledEnd(ZonedDateTime.of(2026, 10, 24, 11, 0, 0, 0, ZoneId.of("Africa/Nairobi")));

        Profile mentor = new Profile();
        mentor.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        mentor.setEmail("mentor@example.com");

        Profile mentee = new Profile();
        mentee.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));

        Skill skill = new Skill("Editorial Strategy");

        when(engine.process(eq("email/booking-notification-mentor"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    Session renderedSession = (Session) context.getVariable("session");
                    return context.getVariable("sessionDate")
                            + "|"
                            + context.getVariable("sessionTime")
                            + "|"
                            + renderedSession.getDurationMinutes();
                });

        service.sendSessionNotificationToMentor(session, mentor, mentee, null, skill);

        verify(emailInterface).sendEmail(
                eq("mentor@example.com"),
                eq("New Session Request - Editorial Strategy"),
                eq("Oct 24, 2026|10:00 AM|60"),
                eq(List.of())
        );
    }

    @Test
    void sendSessionNotificationToMentor_shouldUseB2cReviewLinksForB2cBookings() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TemplateEngine engine = mock(TemplateEngine.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        EmailInterface emailInterface = mock(EmailInterface.class);
        SessionNotificationService service = new SessionNotificationService(mailSender, engine, profileRepository, emailInterface);
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.prospermentor.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(service, "b2cFrontendUrl", "https://www.prospermentor.com");

        UUID sessionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Session session = new Session();
        session.setId(sessionId);
        session.setTitle("Career Strategy");
        session.setBookingSource(Session.BookingSource.B2C);
        session.setScheduledStart(ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        session.setScheduledEnd(ZonedDateTime.of(2026, 10, 24, 11, 0, 0, 0, ZoneId.of("Africa/Nairobi")));

        Profile mentor = new Profile();
        mentor.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        mentor.setEmail("mentor@example.com");

        Profile mentee = new Profile();
        mentee.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));

        Skill skill = new Skill("Career Strategy");

        when(engine.process(eq("email/booking-notification-mentor"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    return context.getVariable("frontendUrl")
                            + "/app/sessions/review/"
                            + ((Session) context.getVariable("session")).getId();
                });

        service.sendSessionNotificationToMentor(session, mentor, mentee, null, skill);

        verify(emailInterface).sendEmail(
                eq("mentor@example.com"),
                eq("New Session Request - Career Strategy"),
                eq("https://www.prospermentor.com/app/sessions/review/" + sessionId),
                eq(List.of())
        );
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
