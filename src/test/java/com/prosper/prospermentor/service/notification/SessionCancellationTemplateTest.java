package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Skill;
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

class SessionCancellationTemplateTest {

    @Test
    void menteeCancellationTemplate_shouldRenderMentorDeclineAndCreditMessage() {
        Profile mentee = profile("Jay", "Oteno", "mentee@example.com");
        Profile mentor = profile("Maria", "Tantuo", "mentor@example.com");
        Session session = session();

        Context context = cancellationContext(session, mentor, mentee, "Mentor declined the session request");

        String html = templateEngine().process("email/session-cancelled-mentee", context);

        assertThat(html)
                .contains("SESSION DECLINED")
                .contains("Hi Jay,")
                .contains("Maria Tantuo")
                .contains("Leadership")
                .contains("Mentor declined the session request")
                .contains("If this was a paid personal booking, your session credit has been returned")
                .contains("Book Another Session");
    }

    @Test
    void mentorCancellationTemplate_shouldRenderDeclineRecordedMessage() {
        Profile mentee = profile("Jay", "Oteno", "mentee@example.com");
        Profile mentor = profile("Maria", "Tantuo", "mentor@example.com");
        Session session = session();

        Context context = cancellationContext(session, mentor, mentee, "Mentor declined the session request");

        String html = templateEngine().process("email/session-cancelled-mentor", context);

        assertThat(html)
                .contains("DECLINE RECORDED")
                .contains("Hi Maria,")
                .contains("Jay Oteno")
                .contains("Leadership")
                .contains("Mentor declined the session request")
                .contains("The mentee has been notified");
    }

    @Test
    void mentorDecline_shouldEmailMenteeAndMentorConfirmationCopy() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TemplateEngine engine = mock(TemplateEngine.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        EmailInterface emailInterface = mock(EmailInterface.class);
        SessionNotificationService service = new SessionNotificationService(mailSender, engine, profileRepository, emailInterface);
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.prospermentor.com");

        Profile mentee = profile("Jay", "Oteno", "mentee@example.com");
        mentee.setId(UUID.randomUUID());
        Profile mentor = profile("Maria", "Tantuo", "mentor@example.com");
        mentor.setId(UUID.randomUUID());
        Session session = session();
        session.setCancelledBy(Session.CancelledBy.MENTOR);

        when(engine.process(eq("email/session-cancelled-mentee"), any(Context.class)))
                .thenReturn("mentee cancellation html");
        when(engine.process(eq("email/session-cancelled-mentor"), any(Context.class)))
                .thenReturn("mentor cancellation html");

        service.sendSessionCancellationNotification(session, mentor, mentee, "Mentor declined the session request");

        verify(emailInterface).sendEmail(
                eq("mentee@example.com"),
                eq("Session Cancelled - Leadership"),
                eq("mentee cancellation html"),
                eq(List.of())
        );
        verify(emailInterface).sendEmail(
                eq("mentor@example.com"),
                eq("Session Cancelled - Leadership"),
                eq("mentor cancellation html"),
                eq(List.of())
        );
    }

    private Context cancellationContext(Session session, Profile mentor, Profile mentee, String reason) {
        Context context = new Context();
        context.setVariable("mentee", mentee);
        context.setVariable("mentor", mentor);
        context.setVariable("session", session);
        context.setVariable("reason", reason);
        context.setVariable("appName", "ProsperMentor");
        context.setVariable("baseUrl", "https://api.prospermentor.com");
        return context;
    }

    private Profile profile(String firstName, String lastName, String email) {
        Profile profile = new Profile();
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setEmail(email);
        return profile;
    }

    private Session session() {
        Session session = new Session();
        session.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        session.setTitle("Leadership");
        session.setSkill(new Skill("Leadership"));
        session.setScheduledStart(ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        session.setScheduledEnd(ZonedDateTime.of(2026, 10, 24, 11, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        return session;
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
