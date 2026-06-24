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
public class MenteeNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public MenteeNotificationService(EmailInterface emailInterface, SpringTemplateEngine templateEngine) {
        this.emailInterface = emailInterface;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendMenteeEmailConfirmation(String recipientEmail,
                                            String firstName,
                                            boolean freeTrial,
                                            String confirmationUrl) {
        try {
            log.info("Sending mentee email confirmation to: {}", recipientEmail);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("freeTrial", freeTrial);
            context.setVariable("confirmationUrl", confirmationUrl);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", frontendUrl);

            String htmlContent = templateEngine.process("email/mentee-email-confirmation", context);
            String subject = freeTrial
                    ? "Confirm your " + appName + " free trial account"
                    : "Confirm your " + appName + " account";

            emailInterface.sendEmail(recipientEmail, subject, htmlContent, List.of());

            log.info("Successfully sent mentee email confirmation to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Error sending mentee email confirmation to {}: {}", recipientEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send mentee email confirmation", e);
        }
    }
}
