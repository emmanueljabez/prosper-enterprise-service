package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

@Service
@Slf4j
public class AuthVerificationNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public AuthVerificationNotificationService(EmailInterface emailInterface, SpringTemplateEngine templateEngine) {
        this.emailInterface = emailInterface;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendEmailConfirmation(String recipientEmail,
                                      String firstName,
                                      String role,
                                      String confirmationUrl) {
        String roleLabel = roleLabel(role);
        try {
            log.info("Sending {} email confirmation to: {}", roleLabel, recipientEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("roleLabel", roleLabel);
            context.setVariable("confirmationUrl", confirmationUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);

            String htmlContent = templateEngine.process("email/auth-email-confirmation", context);
            emailInterface.sendEmail(
                    recipientEmail,
                    "Confirm your " + appName + " " + roleLabel + " account",
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent {} email confirmation to: {}", roleLabel, recipientEmail);
        } catch (Exception e) {
            log.error("Error sending {} email confirmation to {}: {}", roleLabel, recipientEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send " + roleLabel + " email confirmation", e);
        }
    }

    private String roleLabel(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        return switch (normalized) {
            case "corporate_admin", "company", "company_admin" -> "company admin";
            case "employee" -> "employee";
            case "mentor", "advisor" -> "mentor";
            default -> "account";
        };
    }
}
