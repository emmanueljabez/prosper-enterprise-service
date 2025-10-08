package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Google Calendar integration service
 * Manages calendar events for mentorship sessions
 */
@Service
@Slf4j
public class CalendarService {
    
    @Value("${google.calendar.enabled:false}")
    private boolean calendarEnabled;
    
    @Value("${google.calendar.api.key:}")
    private String googleApiKey;
    
    @Value("${google.calendar.service-account-key:}")
    private String serviceAccountKeyPath;
    
    /**
     * Create a calendar event for the session
     */
    public String createCalendarEvent(Session session, MeetingDetails meetingDetails) {
        if (!calendarEnabled) {
            log.info("Google Calendar integration is disabled");
            return null;
        }
        
        log.info("Creating calendar event for session: {}", session.getId());
        
        try {
            // TODO: Implement Google Calendar API integration
            // This is a placeholder implementation
            
            // In a real implementation, you would:
            // 1. Initialize Google Calendar service with credentials
            // 2. Create event with meeting details
            // 3. Add both mentor and mentee as attendees
            // 4. Set meeting link in the event description
            // 5. Return the event ID
            
            String eventId = "cal_event_" + session.getId().toString().substring(0, 8);
            
            log.info("Successfully created calendar event: {} for session: {}", eventId, session.getId());
            return eventId;
            
        } catch (Exception e) {
            log.error("Failed to create calendar event for session {}: {}", 
                    session.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create calendar event", e);
        }
    }
    
    /**
     * Update an existing calendar event
     */
    public void updateCalendarEvent(String eventId, Session session, MeetingDetails meetingDetails) {
        if (!calendarEnabled || eventId == null) {
            return;
        }
        
        log.info("Updating calendar event: {} for session: {}", eventId, session.getId());
        
        try {
            // TODO: Implement calendar event update
            log.info("Successfully updated calendar event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to update calendar event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to update calendar event", e);
        }
    }
    
    /**
     * Cancel a calendar event
     */
    public void cancelCalendarEvent(String eventId) {
        if (!calendarEnabled || eventId == null) {
            return;
        }
        
        log.info("Cancelling calendar event: {}", eventId);
        
        try {
            // TODO: Implement calendar event cancellation
            log.info("Successfully cancelled calendar event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to cancel calendar event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to cancel calendar event", e);
        }
    }
    
    /**
     * Check if calendar integration is enabled
     */
    public boolean isCalendarEnabled() {
        return calendarEnabled;
    }
    
    /**
     * Generate ICS calendar file content for universal calendar support
     */
    public String generateIcsFile(Session session, Profile mentor, Profile mentee, MeetingDetails meetingDetails) {
        log.info("Generating ICS file for session: {}", session.getId());
        
        StringBuilder ics = new StringBuilder();
        
        // ICS Header
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//ProsperMentor//Mentorship Session//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        
        // Event details
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append("session-").append(session.getId()).append("@prospermentor.com\r\n");
        
        // Date/Time formatting for ICS (UTC format: YYYYMMDDTHHMMSSZ)
        String startTime = session.getScheduledStart().toInstant().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String endTime = session.getScheduledEnd().toInstant().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        
        ics.append("DTSTART:").append(startTime).append("\r\n");
        ics.append("DTEND:").append(endTime).append("\r\n");
        ics.append("DTSTAMP:").append(java.time.Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))).append("\r\n");
        
        // Event details
        ics.append("SUMMARY:").append(escapeIcsText(session.getTitle() + " - Mentorship Session")).append("\r\n");
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("Mentorship session between ")
                   .append(mentor.getFirstName()).append(" ").append(mentor.getLastName())
                   .append(" (Mentor) and ")
                   .append(mentee.getFirstName()).append(" ").append(mentee.getLastName())
                   .append(" (Mentee)");
        
        if (session.getMenteeMessage() != null && !session.getMenteeMessage().trim().isEmpty()) {
            description.append("\\n\\nMentee Message: ").append(session.getMenteeMessage());
        }
        
        if (meetingDetails != null && meetingDetails.getMeetingUrl() != null) {
            description.append("\\n\\nMeeting Link: ").append(meetingDetails.getMeetingUrl());
            if (meetingDetails.getMeetingId() != null) {
                description.append("\\nMeeting ID: ").append(meetingDetails.getMeetingId());
            }
            if (meetingDetails.getPassword() != null) {
                description.append("\\nPassword: ").append(meetingDetails.getPassword());
            }
        }
        
        ics.append("DESCRIPTION:").append(escapeIcsText(description.toString())).append("\r\n");
        
        // Location (meeting URL if available)
        if (meetingDetails != null && meetingDetails.getMeetingUrl() != null) {
            ics.append("LOCATION:").append(escapeIcsText(meetingDetails.getMeetingUrl())).append("\r\n");
        } else {
            ics.append("LOCATION:Online Meeting\r\n");
        }
        
        // Attendees
        ics.append("ATTENDEE;CN=").append(escapeIcsText(mentor.getFirstName() + " " + mentor.getLastName()))
           .append(";ROLE=REQ-PARTICIPANT:mailto:").append(mentor.getEmail()).append("\r\n");
        ics.append("ATTENDEE;CN=").append(escapeIcsText(mentee.getFirstName() + " " + mentee.getLastName()))
           .append(";ROLE=REQ-PARTICIPANT:mailto:").append(mentee.getEmail()).append("\r\n");
        
        // Organizer (could be the platform or mentor)
        ics.append("ORGANIZER;CN=ProsperMentor:mailto:noreply@prospermentor.com\r\n");
        
        // Reminders
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT24H\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Mentorship session reminder - 24 hours\r\n");
        ics.append("END:VALARM\r\n");
        
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT15M\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Mentorship session starting in 15 minutes\r\n");
        ics.append("END:VALARM\r\n");
        
        // End event
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");
        
        log.info("Successfully generated ICS file for session: {}", session.getId());
        return ics.toString();
    }
    
    /**
     * Generate multiple calendar provider URLs for user choice
     */
    public CalendarLinks generateCalendarLinks(Session session, Profile mentor, Profile mentee, MeetingDetails meetingDetails) {
        log.info("Generating calendar links for session: {}", session.getId());
        
        String title = URLEncoder.encode(session.getTitle() + " - Mentorship Session", StandardCharsets.UTF_8);
        String startTime = session.getScheduledStart().toInstant().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String endTime = session.getScheduledEnd().toInstant().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        
        StringBuilder details = new StringBuilder();
        details.append("Mentorship session with ")
               .append(mentor.getFirstName()).append(" ").append(mentor.getLastName())
               .append(" and ")
               .append(mentee.getFirstName()).append(" ").append(mentee.getLastName());
        
        if (meetingDetails != null && meetingDetails.getMeetingUrl() != null) {
            details.append("%0A%0AMeeting Link: ").append(meetingDetails.getMeetingUrl());
        }
        
        String encodedDetails = URLEncoder.encode(details.toString(), StandardCharsets.UTF_8);
        String location = meetingDetails != null && meetingDetails.getMeetingUrl() != null ? 
                         URLEncoder.encode(meetingDetails.getMeetingUrl(), StandardCharsets.UTF_8) : 
                         URLEncoder.encode("Online Meeting", StandardCharsets.UTF_8);
        
        // Google Calendar
        String googleUrl = String.format(
            "https://calendar.google.com/calendar/render?action=TEMPLATE&text=%s&dates=%s/%s&details=%s&location=%s",
            title, startTime, endTime, encodedDetails, location
        );
        
        // Outlook Calendar
        String outlookUrl = String.format(
            "https://outlook.live.com/calendar/0/deeplink/compose?subject=%s&startdt=%s&enddt=%s&body=%s&location=%s",
            title, startTime, endTime, encodedDetails, location
        );
        
        // Yahoo Calendar
        String yahooUrl = String.format(
            "https://calendar.yahoo.com/?v=60&view=d&type=20&title=%s&st=%s&et=%s&desc=%s&in_loc=%s",
            title, startTime, endTime, encodedDetails, location
        );
        
        return new CalendarLinks(googleUrl, outlookUrl, yahooUrl);
    }
    
    /**
     * Escape special characters for ICS format
     */
    private String escapeIcsText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace(",", "\\,")
                  .replace(";", "\\;")
                  .replace("\n", "\\n")
                  .replace("\r", "");
    }
    
    /**
     * Data class to hold calendar links for different providers
     */
    public static class CalendarLinks {
        public final String googleCalendar;
        public final String outlookCalendar;
        public final String yahooCalendar;
        
        public CalendarLinks(String googleCalendar, String outlookCalendar, String yahooCalendar) {
            this.googleCalendar = googleCalendar;
            this.outlookCalendar = outlookCalendar;
            this.yahooCalendar = yahooCalendar;
        }
        
        public String getGoogleCalendar() { return googleCalendar; }
        public String getOutlookCalendar() { return outlookCalendar; }
        public String getYahooCalendar() { return yahooCalendar; }
    }

    // Private helper methods for Google Calendar API integration
    
    private String formatEventDescription(Session session, MeetingDetails meetingDetails) {
        StringBuilder description = new StringBuilder();
        description.append("Mentorship Session: ").append(session.getSkill().getName()).append("\n\n");
        
        if (session.getMenteeMessage() != null) {
            description.append("Message from mentee:\n").append(session.getMenteeMessage()).append("\n\n");
        }
        
        if (meetingDetails != null) {
            description.append("Meeting Details:\n");
            description.append("Platform: ").append(session.getMeetingPlatform().getDisplayName()).append("\n");
            description.append("Meeting URL: ").append(meetingDetails.getMeetingUrl()).append("\n");
            
            if (meetingDetails.getMeetingId() != null) {
                description.append("Meeting ID: ").append(meetingDetails.getMeetingId()).append("\n");
            }
            
            if (meetingDetails.getPassword() != null) {
                description.append("Password: ").append(meetingDetails.getPassword()).append("\n");
            }
        }
        
        return description.toString();
    }
}
