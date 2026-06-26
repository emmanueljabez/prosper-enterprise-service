package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.SessionProposal;
import com.prosper.prospermentor.entity.SessionProposalSlot;
import com.prosper.prospermentor.entity.Skill;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Notification service for sending session-related emails
 * Handles mentor and mentee notifications with rich HTML templates
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionNotificationService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ProfileRepository profileRepository;
    private final EmailInterface emailInterface;
    
    @Value("${app.mail.from:noreply@prospermentor.com}")
    private String fromEmail;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.b2c-frontend-url:https://www.prospermentor.com}")
    private String b2cFrontendUrl;

    /**
     * Send session confirmation to mentee
     */
    public void sendSessionConfirmationToMentee(Session session, Profile mentee, 
                                               Profile mentor, MeetingDetails meetingDetails) {
        log.info("Sending session confirmation to mentee: {} for session: {}", 
                mentee.getId(), session.getId());
        
        try {
            Context context = new Context();
            context.setVariable("mentee", mentee);
            context.setVariable("mentor", mentor);
            context.setVariable("session", session);
            context.setVariable("meetingDetails", meetingDetails);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("sessionDate", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)));
            context.setVariable("sessionTime", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH)));
            
            String htmlContent = templateEngine.process("email/booking-confirmation-mentee", context);

            emailInterface.sendEmail(mentee.getEmail(), "Session Confirmed - " + session.getSkill().getName(),
                    htmlContent, List.of());
            
            log.info("Successfully sent session confirmation to mentee: {}", mentee.getId());
            
        } catch (Exception e) {
            log.error("Failed to send session confirmation to mentee {}: {}", 
                    mentee.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send mentee notification", e);
        }
    }
    
    /**
     * Send session notification to mentor
     */
    public void sendSessionNotificationToMentor(Session session, Profile mentor,
                                                Profile mentee, MeetingDetails meetingDetails, Skill skill) {
        log.info("Sending session notification to mentor: {} for session: {}", 
                mentor.getId(), session.getId());
        
        try {
            Context context = new Context();
            context.setVariable("mentor", mentor);
            context.setVariable("mentee", mentee);
            context.setVariable("session", session);
            context.setVariable("meetingDetails", meetingDetails);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("frontendUrl", resolveFrontendUrl(session));
            context.setVariable("sessionDate", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
            context.setVariable("sessionTime", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));

            String htmlContent = templateEngine.process("email/booking-notification-mentor", context);

            emailInterface.sendEmail(mentor.getEmail(), "New Session Request - " + skill.getName(),
                    htmlContent, List.of());
            
            log.info("Successfully sent session notification to mentor: {}", mentor.getId());
            
        } catch (Exception e) {
            log.error("Failed to send session notification to mentor {}: {}", 
                    mentor.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send mentor notification", e);
        }
    }
    
    /**
     * Send session confirmation to mentor
     */
    public void sendSessionConfirmationToMentor(Session session, Profile mentor, 
                                               Profile mentee, MeetingDetails meetingDetails) {
        log.info("Sending session confirmation to mentor: {} for session: {}", 
                mentor.getId(), session.getId());
        
        try {
            Context context = new Context();
            context.setVariable("mentor", mentor);
            context.setVariable("mentee", mentee);
            context.setVariable("session", session);
            context.setVariable("meetingDetails", meetingDetails);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("sessionDate", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)));
            context.setVariable("sessionTime", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH)));
            
            String htmlContent = templateEngine.process("email/booking-confirmation-mentor", context);

            emailInterface.sendEmail(mentor.getEmail(), "Session Confirmed - " + session.getTitle(),
                    htmlContent, List.of());
            
            log.info("Successfully sent session confirmation to mentor: {}", mentor.getId());
            
        } catch (Exception e) {
            log.error("Failed to send session confirmation to mentor {}: {}", 
                    mentor.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send mentor confirmation notification", e);
        }
    }

    public void sendAlternativeProposalToMentee(SessionProposal proposal,
                                                Session session,
                                                Profile mentor,
                                                Profile mentee) {
        log.info("Sending alternative proposal email to mentee: {} for session: {}",
                mentee.getId(), session.getId());

        try {
            String htmlContent = """
                    <p>Hi %s,</p>
                    <p>%s has proposed an alternative time for your %s session.</p>
                    <p><strong>Proposed time%s:</strong><br>%s</p>
                    <p><strong>Message from mentor:</strong><br>%s</p>
                    <p><a href="%s">Review the proposed time</a></p>
                    <p>Prosper Mentor</p>
                    """.formatted(
                    escapeHtml(firstName(mentee)),
                    escapeHtml(fullName(mentor)),
                    escapeHtml(defaultString(session.getTitle(), "mentorship")),
                    proposal.getSlots() != null && proposal.getSlots().size() > 1 ? "s" : "",
                    formatProposalSlotsHtml(proposal),
                    escapeHtml(defaultString(proposal.getMentorMessage(), "Please review the proposed alternative time.")),
                    escapeHtml(buildMenteeProposalLink(session))
            );

            emailInterface.sendEmail(
                    mentee.getEmail(),
                    "Alternative Time Proposed - " + defaultString(session.getTitle(), "Mentorship Session"),
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent alternative proposal email to mentee: {}", mentee.getId());
        } catch (Exception e) {
            log.error("Failed to send alternative proposal email to mentee {}: {}",
                    mentee.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send alternative proposal email", e);
        }
    }

    public void sendProposalResponseToMentor(SessionProposal proposal,
                                             Session session,
                                             Profile mentor,
                                             Profile mentee,
                                             String responseStatus) {
        log.info("Sending proposal response email to mentor: {} for session: {}",
                mentor.getId(), session.getId());

        try {
            String normalizedStatus = defaultString(responseStatus, "responded");
            String htmlContent = """
                    <p>Hi %s,</p>
                    <p>%s has %s your proposed alternative time for the %s session.</p>
                    <p><strong>Proposed time%s:</strong><br>%s</p>
                    <p><strong>Mentee response:</strong><br>%s</p>
                    <p><a href="%s">View the session</a></p>
                    <p>Prosper Mentor</p>
                    """.formatted(
                    escapeHtml(firstName(mentor)),
                    escapeHtml(fullName(mentee)),
                    escapeHtml(normalizedStatus),
                    escapeHtml(defaultString(session.getTitle(), "mentorship")),
                    proposal.getSlots() != null && proposal.getSlots().size() > 1 ? "s" : "",
                    formatProposalSlotsHtml(proposal),
                    escapeHtml(defaultString(proposal.getMenteeResponse(), "No note provided")),
                    escapeHtml(buildSessionReviewLink(session))
            );

            emailInterface.sendEmail(
                    mentor.getEmail(),
                    "Mentee " + normalizedStatus + " Proposed Time - " + defaultString(session.getTitle(), "Mentorship Session"),
                    htmlContent,
                    List.of()
            );

            log.info("Successfully sent proposal response email to mentor: {}", mentor.getId());
        } catch (Exception e) {
            log.error("Failed to send proposal response email to mentor {}: {}",
                    mentor.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send proposal response email", e);
        }
    }
    
    /**
     * Send session reminder to both mentor and mentee
     */
    public void sendSessionReminder(Session session, Profile mentor, 
                                   Profile mentee, MeetingDetails meetingDetails) {
        log.info("Sending session reminder for session: {}", session.getId());
        
        // Send reminder to mentee
        sendReminderToMentee(session, mentee, mentor, meetingDetails);
        
        // Send reminder to mentor
        sendReminderToMentor(session, mentor, mentee, meetingDetails);
    }
    
    /**
     * Send cancellation notification
     */
    public void sendSessionCancellationNotification(Session session, Profile mentor, 
                                           Profile mentee, String reason) {
        log.info("Sending cancellation notification for session: {}", session.getId());
        
        try {
            // Determine who cancelled and send appropriate notifications
            if (session.getCancelledBy() == Session.CancelledBy.MENTOR) {
                sendCancellationToMentee(session, mentee, mentor, reason);
                sendCancellationToMentor(session, mentor, mentee, reason);
            } else if (session.getCancelledBy() == Session.CancelledBy.MENTEE) {
                sendCancellationToMentor(session, mentor, mentee, reason);
            } else {
                // System or admin cancellation - notify both
                sendCancellationToMentee(session, mentee, mentor, reason);
                sendCancellationToMentor(session, mentor, mentee, reason);
            }
            
        } catch (Exception e) {
            log.error("Failed to send cancellation notification for session {}: {}", 
                    session.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send cancellation notification", e);
        }
    }
    
    private void sendReminderToMentee(Session session, Profile mentee, 
                                     Profile mentor, MeetingDetails meetingDetails) {
        try {
            Context context = new Context();
            context.setVariable("mentee", mentee);
            context.setVariable("mentor", mentor);
            context.setVariable("session", session);
            context.setVariable("meetingDetails", meetingDetails);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            
            String htmlContent = templateEngine.process("email/session-reminder-mentee", context);

            emailInterface.sendEmail(mentee.getEmail(), "Session Reminder - " + session.getSkill().getName() + " in 24 hours",
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send reminder to mentee {}: {}", mentee.getId(), e.getMessage(), e);
        }
    }
    
    private void sendReminderToMentor(Session session, Profile mentor, 
                                     Profile mentee, MeetingDetails meetingDetails) {
        try {
            Context context = new Context();
            context.setVariable("mentor", mentor);
            context.setVariable("mentee", mentee);
            context.setVariable("session", session);
            context.setVariable("meetingDetails", meetingDetails);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            
            String htmlContent = templateEngine.process("email/session-reminder-mentor", context);

            emailInterface.sendEmail(mentor.getEmail(), "Session Reminder - " + session.getSkill().getName() + " in 24 hours",
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send reminder to mentor {}: {}", mentor.getId(), e.getMessage(), e);
        }
    }
    
    private void sendCancellationToMentee(Session session, Profile mentee, 
                                         Profile mentor, String reason) {
        try {
            Context context = new Context();
            context.setVariable("mentee", mentee);
            context.setVariable("mentor", mentor);
            context.setVariable("session", session);
            context.setVariable("reason", reason);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("frontendUrl", resolveFrontendUrl(session));
            
            String htmlContent = templateEngine.process("email/session-cancelled-mentee", context);

            emailInterface.sendEmail(mentee.getEmail(), "Session Cancelled - " + session.getSkill().getName(),
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send cancellation to mentee {}: {}", mentee.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send cancellation to mentee", e);
        }
    }
    
    private void sendCancellationToMentor(Session session, Profile mentor, 
                                         Profile mentee, String reason) {
        try {
            Context context = new Context();
            context.setVariable("mentor", mentor);
            context.setVariable("mentee", mentee);
            context.setVariable("session", session);
            context.setVariable("reason", reason);
            context.setVariable("appName", appName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("frontendUrl", resolveFrontendUrl(session));
            
            String htmlContent = templateEngine.process("email/session-cancelled-mentor", context);

            emailInterface.sendEmail(mentor.getEmail(), "Session Cancelled - " + session.getSkill().getName(),
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send cancellation to mentor {}: {}", mentor.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send cancellation to mentor", e);
        }
    }

    private String buildSessionReviewLink(Session session) {
        return resolveFrontendUrl(session) + "/app/sessions/review/" + session.getId();
    }

    private String buildMenteeProposalLink(Session session) {
        String path = session != null && session.getBookingSource() == Session.BookingSource.B2C
                ? "/sessions/proposals/"
                : "/app/sessions/proposals/";
        return resolveFrontendUrl(session) + path + session.getId();
    }

    private String resolveFrontendUrl(Session session) {
        String configuredUrl = session != null && session.getBookingSource() == Session.BookingSource.B2C
                ? b2cFrontendUrl
                : frontendUrl;

        String normalizedFrontendUrl = configuredUrl == null || configuredUrl.isBlank()
                ? "http://localhost:3000"
                : configuredUrl.trim();
        while (normalizedFrontendUrl.endsWith("/")) {
            normalizedFrontendUrl = normalizedFrontendUrl.substring(0, normalizedFrontendUrl.length() - 1);
        }
        return normalizedFrontendUrl;
    }

    private String formatProposalSlotsHtml(SessionProposal proposal) {
        if (proposal.getSlots() == null || proposal.getSlots().isEmpty()) {
            return "No slots provided";
        }

        return proposal.getSlots().stream()
                .map(this::formatProposalSlot)
                .map(this::escapeHtml)
                .reduce((left, right) -> left + "<br>" + right)
                .orElse("No slots provided");
    }

    private String formatProposalSlot(SessionProposalSlot slot) {
        return slot.getScheduledStart().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.ENGLISH));
    }

    private String firstName(Profile profile) {
        if (profile != null && profile.getFirstName() != null && !profile.getFirstName().isBlank()) {
            return profile.getFirstName();
        }
        return fullName(profile);
    }

    private String fullName(Profile profile) {
        if (profile == null) {
            return "there";
        }
        String firstName = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? defaultString(profile.getEmail(), "there") : fullName;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String escapeHtml(String value) {
        return defaultString(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
