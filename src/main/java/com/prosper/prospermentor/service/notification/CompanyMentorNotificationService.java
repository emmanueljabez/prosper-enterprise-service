package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyMentorNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;
    private final NautixWhatsAppService nautixWhatsAppService;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${prosper.b2c.base-url:https://www.prospermentor.com}")
    private String b2cBaseUrl;

    @Value("${nautix.whatsapp.company-mentor-invitation-template:company_mentor_invitation}")
    private String whatsappTemplateName;

    @Value("${nautix.whatsapp.company-mentor-welcome-template:company_mentor_welcome}")
    private String whatsappWelcomeTemplateName;

    public String buildInvitationUrl(String rawToken) {
        return b2cBaseUrl + "/auth?mode=signup&flow=mentor&from=invite&token=" + rawToken;
    }

    public String buildMentorDashboardUrl() {
        return b2cBaseUrl + "/mentor-dashboard";
    }

    public DeliveryAttemptResult sendMentorInvitation(Company company,
                                                      String email,
                                                      String phone,
                                                      String rawToken,
                                                      LocalDateTime expiresAt) {
        String invitationUrl = buildInvitationUrl(rawToken);
        boolean emailSent = sendEmail(company, email, invitationUrl, expiresAt);
        boolean whatsappSent = sendWhatsapp(company, phone, rawToken, expiresAt);
        return DeliveryAttemptResult.builder()
                .emailSent(emailSent)
                .whatsappSent(whatsappSent)
                .build();
    }

    public DeliveryAttemptResult sendMentorWelcome(Company company,
                                                   Profile mentor,
                                                   CompanyMentorPoolMembership.VisibilityMode visibilityMode) {
        String dashboardUrl = buildMentorDashboardUrl();
        String mentorName = firstName(mentor);
        String visibilityLabel = visibilityLabel(visibilityMode);
        boolean emailSent = sendWelcomeEmail(company, mentor, mentorName, visibilityLabel, dashboardUrl);
        boolean whatsappSent = sendWelcomeWhatsapp(company, mentor, mentorName, visibilityLabel, dashboardUrl);
        return DeliveryAttemptResult.builder()
                .emailSent(emailSent)
                .whatsappSent(whatsappSent)
                .build();
    }

    private boolean sendEmail(Company company, String email, String invitationUrl, LocalDateTime expiresAt) {
        try {
            Context context = new Context();
            context.setVariable("company", company);
            context.setVariable("email", email);
            context.setVariable("invitationUrl", invitationUrl);
            context.setVariable("expiresAt", expiresAt);
            context.setVariable("appName", appName);

            String html = templateEngine.process("email/company-mentor-invitation", context);
            emailInterface.sendEmail(
                    email,
                    "You're invited to mentor for " + company.getName() + " on " + appName,
                    html,
                    List.of()
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send company mentor invitation email to {}: {}", email, e.getMessage(), e);
            return false;
        }
    }

    private boolean sendWelcomeEmail(Company company,
                                     Profile mentor,
                                     String mentorName,
                                     String visibilityLabel,
                                     String dashboardUrl) {
        try {
            Context context = new Context();
            context.setVariable("company", company);
            context.setVariable("mentor", mentor);
            context.setVariable("mentorName", mentorName);
            context.setVariable("dashboardUrl", dashboardUrl);
            context.setVariable("visibilityLabel", visibilityLabel);
            context.setVariable("appName", appName);

            String html = templateEngine.process("email/company-mentor-welcome", context);
            emailInterface.sendEmail(
                    mentor.getEmail(),
                    "Welcome to " + company.getName() + " mentor pool on " + appName,
                    html,
                    List.of()
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send company mentor welcome email to {}: {}",
                    mentor != null ? mentor.getEmail() : null,
                    e.getMessage(),
                    e);
            return false;
        }
    }

    private boolean sendWhatsapp(Company company, String phone, String rawToken, LocalDateTime expiresAt) {
        try {
            nautixWhatsAppService.sendTemplateMessage(
                    whatsappTemplateName,
                    phone,
                    List.of(company.getName(), expiresAt.toString(), rawToken)
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send company mentor invitation WhatsApp to {}: {}", phone, e.getMessage(), e);
            return false;
        }
    }

    private boolean sendWelcomeWhatsapp(Company company,
                                        Profile mentor,
                                        String mentorName,
                                        String visibilityLabel,
                                        String dashboardUrl) {
        try {
            nautixWhatsAppService.sendTemplateMessage(
                    whatsappWelcomeTemplateName,
                    mentor.getPhone(),
                    List.of(mentorName, company.getName(), visibilityLabel, dashboardUrl)
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send company mentor welcome WhatsApp to {}: {}",
                    mentor != null ? mentor.getPhone() : null,
                    e.getMessage(),
                    e);
            return false;
        }
    }

    private String firstName(Profile profile) {
        if (profile != null && profile.getFirstName() != null && !profile.getFirstName().isBlank()) {
            return profile.getFirstName().trim();
        }
        return "Mentor";
    }

    private String visibilityLabel(CompanyMentorPoolMembership.VisibilityMode visibilityMode) {
        if (visibilityMode == null) {
            return "Company private";
        }
        return switch (visibilityMode) {
            case COMPANY_PRIVATE -> "Company private";
            case PROGRAM_RESTRICTED -> "Program restricted";
            case PUBLIC_REQUESTED -> "Public listing requested";
            case PUBLIC_APPROVED -> "Public listing approved";
        };
    }

    @Builder
    public record DeliveryAttemptResult(boolean emailSent, boolean whatsappSent) {
    }
}
