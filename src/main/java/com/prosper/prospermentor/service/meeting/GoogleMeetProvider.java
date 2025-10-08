package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Google Meet provider implementation
 * Simplified implementation - Google Meet links are generated automatically with Calendar events
 */
@Service
@Slf4j
public class GoogleMeetProvider implements MeetingProvider {
    
    @Override
    public MeetingDetails createMeeting(Session session) {
        log.info("Creating Google Meet for session: {}", session.getId());
        
        // Generate a unique meeting code for Google Meet
        String meetingCode = generateMeetingCode();
        String meetingId = "meet-" + session.getId().toString().substring(0, 8);
        String meetingUrl = "https://meet.google.com/" + meetingCode;
        
        // Add session details to the meeting
        String meetingTitle = "Mentorship Session: " + (session.getSkill() != null ? session.getSkill().getName() : "General");
        
        log.info("Generated Google Meet link: {} for session: {}", meetingUrl, session.getId());
        
        return MeetingDetails.builder()
                .meetingId(meetingId)
                .meetingUrl(meetingUrl)
                .joinUrl(meetingUrl)
                .platformSpecificData("Google Meet - " + meetingTitle + " - Code: " + meetingCode)
                .build();
    }
    
    @Override
    public MeetingDetails updateMeeting(String meetingId, Session session) {
        log.info("Updating Google Meet: {}", meetingId);
        
        // Google Meet meetings don't typically need updates
        // The same link remains valid
        return getMeetingDetails(meetingId);
    }
    
    @Override
    public void cancelMeeting(String meetingId) {
        log.info("Cancelling Google Meet: {}", meetingId);
        
        // Google Meet meetings don't need explicit cancellation
        // They become inactive when the calendar event is cancelled
    }
    
    @Override
    public MeetingDetails getMeetingDetails(String meetingId) {
        log.info("Getting Google Meet details: {}", meetingId);
        
        // In a real implementation, you would store and retrieve meeting details
        // For now, return basic details
        return MeetingDetails.builder()
                .meetingId(meetingId)
                .meetingUrl("https://meet.google.com/" + generateMeetingCode())
                .build();
    }
    
    @Override
    public boolean supports(Session.MeetingPlatform platform) {
        return platform == Session.MeetingPlatform.GOOGLE_MEET;
    }
    
    /**
     * Generate a Google Meet-style meeting code
     */
    private String generateMeetingCode() {
        // Generate a random 10-character meeting code similar to Google Meet format
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                code.append(chars.charAt((int) (Math.random() * chars.length())));
            }
            if (i < 2) code.append("-");
        }
        
        return code.toString();
    }
}
