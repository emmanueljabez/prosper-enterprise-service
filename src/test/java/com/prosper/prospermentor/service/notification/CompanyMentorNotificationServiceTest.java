package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyMentorNotificationServiceTest {

    @Test
    void companyMentorInvitationTemplate_shouldRenderBrandedInviteWithSignupCta() {
        Company company = new Company();
        company.setName("Kenya Airways");

        Context context = new Context();
        context.setVariable("company", company);
        context.setVariable("email", "mentor@example.com");
        context.setVariable("invitationUrl", "https://www.prospermentor.com/auth?mode=signup&flow=mentor&from=invite&token=abc");
        context.setVariable("expiresAt", LocalDateTime.of(2026, 8, 20, 9, 30));
        context.setVariable("appName", "ProsperMentor");

        String html = templateEngine().process("email/company-mentor-invitation", context);

        assertThat(html)
                .contains("prosper-logo.png")
                .contains("MENTOR INVITE")
                .contains("You&#39;re invited to<br>mentor.")
                .contains("Kenya Airways invited you to join their ProsperMentor mentor pool.")
                .contains("COMPANY")
                .contains("Kenya Airways")
                .contains("INVITE EXPIRES")
                .contains("2026-08-20T09:30")
                .contains("Continue mentor signup")
                .contains("https://www.prospermentor.com/auth?mode=signup&amp;flow=mentor&amp;from=invite&amp;token=abc")
                .contains("Support")
                .contains("Privacy Policy")
                .contains("Unsubscribe");
    }

    @Test
    void companyMentorWelcomeTemplate_shouldRenderBrandedWelcomeWithDashboardCta() {
        Company company = new Company();
        company.setName("Kenya Airways");

        Profile mentor = new Profile();
        mentor.setFirstName("David");
        mentor.setEmail("mentor@example.com");

        Context context = new Context();
        context.setVariable("company", company);
        context.setVariable("mentor", mentor);
        context.setVariable("mentorName", "David");
        context.setVariable("dashboardUrl", "https://www.prospermentor.com/mentor-dashboard");
        context.setVariable("visibilityLabel", "Company private");
        context.setVariable("appName", "ProsperMentor");

        String html = templateEngine().process("email/company-mentor-welcome", context);

        assertThat(html)
                .contains("prosper-logo.png")
                .contains("WELCOME MENTOR")
                .contains("Welcome to<br>Kenya Airways.")
                .contains("David, your ProsperMentor mentor profile is now connected to Kenya Airways.")
                .contains("VISIBILITY")
                .contains("Company private")
                .contains("Open mentor dashboard")
                .contains("https://www.prospermentor.com/mentor-dashboard")
                .contains("Support")
                .contains("Privacy Policy");
    }

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

    @Test
    void sendMentorWelcome_shouldSendEmailAndWhatsAppWithDashboardLink() {
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
        ReflectionTestUtils.setField(service, "whatsappWelcomeTemplateName", "company_mentor_welcome");
        when(templateEngine.process(eq("email/company-mentor-welcome"), any(Context.class)))
                .thenReturn("<html>Welcome</html>");

        Company company = new Company();
        company.setName("Kenya Airways");

        Profile mentor = new Profile();
        mentor.setFirstName("David");
        mentor.setEmail("mentor@example.com");
        mentor.setPhone("+254720482575");

        CompanyMentorNotificationService.DeliveryAttemptResult result = service.sendMentorWelcome(
                company,
                mentor,
                CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE
        );

        assertThat(result.emailSent()).isTrue();
        assertThat(result.whatsappSent()).isTrue();
        verify(emailInterface).sendEmail(
                eq("mentor@example.com"),
                eq("Welcome to Kenya Airways mentor pool on ProsperMentor"),
                eq("<html>Welcome</html>"),
                eq(List.of())
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("company_mentor_welcome"),
                eq("+254720482575"),
                eq(List.of(
                        "David",
                        "Kenya Airways",
                        "Company private",
                        "https://www.prospermentor.com/mentor-dashboard"
                ))
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
