package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Company;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

/**
 * Service for sending company-related email notifications
 */
@Service
@Slf4j
public class CompanyNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public CompanyNotificationService(EmailInterface emailInterface, SpringTemplateEngine templateEngine) {
        this.emailInterface = emailInterface;
        this.templateEngine = templateEngine;
    }

    /**
     * Send welcome email to newly created company with registration link
     */
    @Async
    public void sendCompanyWelcomeEmail(Company company, String registrationToken) {
        try {
            log.info("Sending welcome email to company: {} ({})", company.getName(), company.getEmailAddress());

            // Build registration URL
            String registrationUrl = frontendUrl + "/auth/complete-signup?token=" + registrationToken;

            // Create Thymeleaf context
            Context context = new Context();
            context.setVariable("company", company);
            context.setVariable("registrationUrl", registrationUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);

            // Render template
            String htmlContent = templateEngine.process("email/company-welcome", context);

            // Send email
            emailInterface.sendEmail(
                    company.getEmailAddress(),
                    "Welcome to " + appName + " - Complete Your Registration",
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent welcome email to company: {}", company.getEmailAddress());

        } catch (Exception e) {
            log.error("Error sending welcome email to company {}: {}", company.getEmailAddress(), e.getMessage(), e);
        }
    }

    /**
     * Send company admin email confirmation using ProsperMentor branding.
     */
    @Async
    public void sendCompanyEmailConfirmation(String recipientEmail,
                                             String firstName,
                                             String companyName,
                                             String confirmationUrl) {
        try {
            log.info("Sending company email confirmation to: {}", recipientEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("companyName", companyName);
            context.setVariable("confirmationUrl", confirmationUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);

            String htmlContent = templateEngine.process("email/company-email-confirmation", context);

            emailInterface.sendEmail(
                    recipientEmail,
                    "Confirm your " + appName + " company account",
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent company email confirmation to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Error sending company email confirmation to {}: {}", recipientEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send company email confirmation", e);
        }
    }

    /**
     * Send registration completion confirmation email
     */
    @Async
    public void sendRegistrationCompletedEmail(Company company) {
        try {
            log.info("Sending registration completion email to company: {}", company.getEmailAddress());

            // Create simple confirmation email
            String htmlContent = buildRegistrationCompletedHtml(company);

            // Send email
            emailInterface.sendEmail(
                    company.getEmailAddress(),
                    "Registration Completed - " + appName,
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent registration completion email to company: {}", company.getEmailAddress());

        } catch (Exception e) {
            log.error("Error sending registration completion email to company {}: {}",
                    company.getEmailAddress(), e.getMessage(), e);
        }
    }

    /**
     * Send invitation email to whitelisted employee
     */
    @Async
    public void sendEmployeeInvitationEmail(Company company, String employeeEmail, String invitationToken) {
        try {
            log.info("Sending invitation email to employee: {} from company: {}", employeeEmail, company.getName());

            // Build invitation URL - this will be the frontend signup page with token
            String invitationUrl = frontendUrl + "/auth/complete-signup?token=" + invitationToken;

            // Create Thymeleaf context
            Context context = new Context();
            context.setVariable("company", company);
            context.setVariable("email", employeeEmail);
            context.setVariable("invitationUrl", invitationUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);

            // Render template
            String htmlContent = templateEngine.process("email/employee-invitation", context);

            // Send email
            emailInterface.sendEmail(
                    employeeEmail,
                    "You're Invited to Join " + company.getName() + " on " + appName,
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent invitation email to: {}", employeeEmail);

        } catch (Exception e) {
            log.error("Error sending invitation email to {}: {}", employeeEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }

    /**
     * Build simple HTML for registration completed email
     */
    private String buildRegistrationCompletedHtml(Company company) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #9b3699 0%%, #7d2a7b 100%%); color: white; padding: 30px; text-align: center; border-radius: 8px 8px 0 0; }
                        .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                        .success-icon { font-size: 48px; margin-bottom: 20px; }
                        h1 { margin: 0; font-size: 24px; }
                        .button { display: inline-block; background: #9b3699; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="success-icon">✓</div>
                            <h1>Registration Completed!</h1>
                        </div>
                        <div class="content">
                            <p>Dear %s,</p>
                            <p>Your company registration has been successfully completed! Your team can now access the %s platform.</p>
                            <p>You can now:</p>
                            <ul>
                                <li>Whitelist employee email addresses</li>
                                <li>Browse mentorship programs</li>
                                <li>Manage your company profile</li>
                                <li>Invite team members</li>
                            </ul>
                            <a href="%s" class="button">Access Your Portal</a>
                            <p style="margin-top: 30px;">Thank you for choosing %s!</p>
                            <p><strong>The %s Team</strong></p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                company.getName(),
                appName,
                frontendUrl + "/app/admin",
                appName,
                appName
        );
    }
}
