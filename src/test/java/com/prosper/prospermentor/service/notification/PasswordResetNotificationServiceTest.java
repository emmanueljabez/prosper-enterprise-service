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

class PasswordResetNotificationServiceTest {

    @Test
    void sendPasswordResetEmail_shouldRenderSecurityUpdateTemplateWithResetLink() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        PasswordResetNotificationService service = new PasswordResetNotificationService(emailInterface, templateEngine());
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");

        service.sendPasswordResetEmail(
                "user@example.com",
                "https://enterprise.prospermentor.com/reset-password?token=abc123",
                60
        );

        verify(emailInterface).sendEmail(
                eq("user@example.com"),
                eq("Reset your ProsperMentor password"),
                argThat(html -> html.contains("SECURITY UPDATE")
                        && html.contains("Reset Your")
                        && html.contains("Password")
                        && html.contains("Reset Password")
                        && html.contains("https://enterprise.prospermentor.com/reset-password?token=abc123")
                        && html.contains("This secure link will expire in 1 hour.")
                        && html.contains("DIDN'T REQUEST THIS?")
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
