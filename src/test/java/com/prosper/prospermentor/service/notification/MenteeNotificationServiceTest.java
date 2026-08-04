package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MenteeNotificationServiceTest {

    @Test
    void sendMenteeEmailConfirmation_shouldRenderFreeTrialTemplateWithConfirmationLink() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        MenteeNotificationService service = new MenteeNotificationService(emailInterface, templateEngine());
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");

        service.sendMenteeEmailConfirmation(
                "mentee@example.com",
                "Mentee",
                true,
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=abc&type=signup&audience=mentee&trial=1&product=FREE_TRIAL"
        );

        verify(emailInterface).sendEmail(
                eq("mentee@example.com"),
                eq("Confirm your ProsperMentor free trial account"),
                argThat(html -> html.contains("Your growth journey begins here.")
                        && html.contains("Confirm Email")
                        && html.contains("30-minute free trial")
                        && html.contains("https://enterprise.prospermentor.com/auth/confirm-email?token_hash=abc&amp;type=signup&amp;audience=mentee&amp;trial=1&amp;product=FREE_TRIAL")
                        && html.contains("https://enterprise.prospermentor.com/images/prosper_mentor_logo.png")),
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
