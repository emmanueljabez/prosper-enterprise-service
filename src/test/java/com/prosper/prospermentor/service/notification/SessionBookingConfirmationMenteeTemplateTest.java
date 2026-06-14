package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Skill;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
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

class SessionBookingConfirmationMenteeTemplateTest {

    @Test
    void menteeBookingConfirmationTemplate_shouldRenderGrowthConfirmationWithJoinLink() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Profile mentee = new Profile();
        mentee.setFirstName("Jay");

        Profile mentor = new Profile();
        mentor.setFirstName("Elena");
        mentor.setLastName("Rodriguez");
        mentor.setAvatarUrl("https://enterprise.prospermentor.com/images/elena.jpg");

        Session session = new Session();
        session.setId(sessionId);
        session.setTitle("Editorial Strategy");
        session.setMentorResponse("I'm excited to help you refine your strategy and tackle your current roadblocks. See you soon!");

        MeetingDetails meetingDetails = MeetingDetails.builder()
                .meetingUrl("https://meet.google.com/growth-room")
                .meetingId("growth-room")
                .build();

        Context context = new Context();
        context.setVariable("mentee", mentee);
        context.setVariable("mentor", mentor);
        context.setVariable("session", session);
        context.setVariable("meetingDetails", meetingDetails);
        context.setVariable("appName", "ProsperMentor");
        context.setVariable("baseUrl", "https://api.prospermentor.com");
        context.setVariable("sessionDate", "Oct 24");
        context.setVariable("sessionTime", "10:30 AM EST");

        String html = templateEngine().process("email/booking-confirmation-mentee", context);

        assertThat(html)
                .contains("CONFIRMED")
                .contains("You&#39;re all set for<br>growth.")
                .contains("Your session with")
                .contains("has been successfully scheduled.")
                .contains("DATE AND TIME")
                .contains("Oct 24, 10:30 AM EST")
                .contains("BOOKING STATUS")
                .contains("Confirmed")
                .contains("Join Session")
                .contains("https://meet.google.com/growth-room")
                .contains("MEET YOUR MENTOR")
                .contains("Elena")
                .contains("Rodriguez")
                .contains("Strategic Product Lead")
                .contains("I&#39;m excited to help you refine your strategy")
                .contains("Preparation Tips")
                .contains("Prepare 2-3 specific questions")
                .contains("Ensure your microphone and camera are tested")
                .contains("Support")
                .contains("Privacy Policy")
                .contains("Unsubscribe");
    }

    @Test
    void sendSessionConfirmationToMentee_shouldProvideCompactDateAndTimezoneTime() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        TemplateEngine engine = mock(TemplateEngine.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        EmailInterface emailInterface = mock(EmailInterface.class);
        SessionNotificationService service = new SessionNotificationService(mailSender, engine, profileRepository, emailInterface);
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.prospermentor.com");

        Skill skill = new Skill("Editorial Strategy");

        Session session = new Session();
        session.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        session.setTitle("Editorial Strategy");
        session.setSkill(skill);
        session.setScheduledStart(ZonedDateTime.of(2026, 10, 24, 10, 30, 0, 0, ZoneId.of("America/Jamaica")));
        session.setScheduledEnd(ZonedDateTime.of(2026, 10, 24, 11, 15, 0, 0, ZoneId.of("America/Jamaica")));

        Profile mentee = new Profile();
        mentee.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        mentee.setEmail("mentee@example.com");

        Profile mentor = new Profile();
        mentor.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));

        MeetingDetails meetingDetails = MeetingDetails.builder()
                .meetingUrl("https://meet.google.com/growth-room")
                .build();

        when(engine.process(eq("email/booking-confirmation-mentee"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    return context.getVariable("sessionDate") + "|" + context.getVariable("sessionTime");
                });

        service.sendSessionConfirmationToMentee(session, mentee, mentor, meetingDetails);

        verify(emailInterface).sendEmail(
                eq("mentee@example.com"),
                eq("Session Confirmed - Editorial Strategy"),
                eq("Oct 24|10:30 AM EST"),
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
