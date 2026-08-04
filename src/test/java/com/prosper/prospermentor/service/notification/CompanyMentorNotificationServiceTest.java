package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyMentorNotificationServiceTest {

    @Test
    void sendMentorInvitation_shouldSendWhatsAppButtonTokenAfterBodyParameters() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
        NautixWhatsAppService nautixWhatsAppService = mock(NautixWhatsAppService.class);
        CompanyMentorNotificationService service = new CompanyMentorNotificationService(
                emailInterface,
                templateEngine,
                nautixWhatsAppService
        );
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "b2cBaseUrl", "https://www.prospermentor.com");
        ReflectionTestUtils.setField(service, "whatsappTemplateName", "company_mentor_invitation");
        when(templateEngine.process(eq("email/company-mentor-invitation"), any(Context.class)))
                .thenReturn("<html>Invite</html>");

        Company company = new Company();
        company.setName("Kenya Airways");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 11, 15, 59, 47);
        String rawToken = "11111111-2222-3333-4444-555555555555-66666666-7777-8888-9999-000000000000";

        service.sendMentorInvitation(
                company,
                "mentor@example.com",
                "+254720482575",
                rawToken,
                expiresAt
        );

        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("company_mentor_invitation"),
                eq("+254720482575"),
                eq(List.of(
                        "Kenya Airways",
                        "2026-08-11T15:59:47",
                        rawToken
                ))
        );
    }
}
