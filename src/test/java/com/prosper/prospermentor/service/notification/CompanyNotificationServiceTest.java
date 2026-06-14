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

class CompanyNotificationServiceTest {

    @Test
    void sendCompanyEmailConfirmation_shouldRenderThymeleafTemplateWithConfirmationLink() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        CompanyNotificationService service = new CompanyNotificationService(emailInterface, templateEngine());
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");

        service.sendCompanyEmailConfirmation(
                "admin@example.com",
                "Admin",
                "Example Co",
                "https://supabase.example.com/auth/v1/verify?token=abc&type=signup"
        );

        verify(emailInterface).sendEmail(
                eq("admin@example.com"),
                eq("Confirm your ProsperMentor company account"),
                argThat(html -> html.contains("Confirm your company account")
                        && html.contains("Example Co")
                        && html.contains("https://supabase.example.com/auth/v1/verify?token=abc&amp;type=signup")
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
