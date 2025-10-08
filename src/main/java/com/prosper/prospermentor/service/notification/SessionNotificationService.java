package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Profile;
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
                    .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)));
            context.setVariable("sessionTime", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH)));
            
            String htmlContent = templateEngine.process("email/booking-confirmation-mentee", context);

            emailInterface.sendEmail("jayb2oteno@gmail.com", "Session Confirmed - " + session.getSkill().getName(),
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
            context.setVariable("sessionDate", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)));
            context.setVariable("sessionTime", session.getScheduledStart()
                    .format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH)));
            
            String htmlContent = templateEngine.process("email/booking-notification-mentor", context);

            emailInterface.sendEmail(/*mentor.getEmail()*/ "jayb2oteno@gmail.com", "New Session Request - " + skill.getName(),
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
            
            String htmlContent = templateEngine.process("email/session-cancelled-mentee", context);

            emailInterface.sendEmail(mentee.getEmail(), "Session Cancelled - " + session.getSkill().getName(),
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send cancellation to mentee {}: {}", mentee.getId(), e.getMessage(), e);
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
            
            String htmlContent = templateEngine.process("email/session-cancelled-mentor", context);

            emailInterface.sendEmail(mentor.getEmail(), "Session Cancelled - " + session.getSkill().getName(),
                    htmlContent, List.of());
            
        } catch (Exception e) {
            log.error("Failed to send cancellation to mentor {}: {}", mentor.getId(), e.getMessage(), e);
        }
    }
}
