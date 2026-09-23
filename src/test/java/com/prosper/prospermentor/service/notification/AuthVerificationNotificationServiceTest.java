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

class AuthVerificationNotificationServiceTest {

    @Test
    void sendEmailConfirmation_shouldRenderRoleAwareTemplateForMentor() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        AuthVerificationNotificationService service = new AuthVerificationNotificationService(emailInterface, templateEngine());
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");

        service.sendEmailConfirmation(
                "mentor@example.com",
                "Mentor",
                "mentor",
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=abc&type=signup&audience=mentor"
        );

        verify(emailInterface).sendEmail(
                eq("mentor@example.com"),
                eq("Confirm your ProsperMentor mentor account"),
                argThat(html -> html.contains("Confirm your mentor account")
                        && html.contains("Mentor")
                        && html.contains("https://enterprise.prospermentor.com/auth/confirm-email?token_hash=abc&amp;type=signup&amp;audience=mentor")
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
