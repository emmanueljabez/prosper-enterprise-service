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
public class PasswordResetNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public PasswordResetNotificationService(EmailInterface emailInterface, SpringTemplateEngine templateEngine) {
        this.emailInterface = emailInterface;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String resetUrl, int ttlMinutes) {
        try {
            log.info("Sending password reset email to: {}", recipientEmail);

            Context context = new Context();
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);
            context.setVariable("expiryText", buildExpiryText(ttlMinutes));

            String htmlContent = templateEngine.process("email/password-reset", context);

            emailInterface.sendEmail(
                    recipientEmail,
                    "Reset your " + appName + " password",
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent password reset email to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Error sending password reset email to {}: {}", recipientEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    private String buildExpiryText(int ttlMinutes) {
        if (ttlMinutes == 60) {
            return "This secure link will expire in 1 hour.";
        }
        if (ttlMinutes == 1) {
            return "This secure link will expire in 1 minute.";
        }
        return "This secure link will expire in " + ttlMinutes + " minutes.";
    }
}
