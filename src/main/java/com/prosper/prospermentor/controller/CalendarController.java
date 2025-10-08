package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.CalendarService;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import com.prosper.prospermentor.service.meeting.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for calendar integration
 * Provides endpoints for downloading calendar files and redirecting to calendar providers
 */
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {
    
    private final CalendarService calendarService;
    private final SessionRepository sessionRepository;
    private final ProfileRepository profileRepository;
    private final MeetingService meetingService;
    
    /**
     * Download ICS calendar file for a session
     * This works with ALL calendar applications (Apple Calendar, Outlook, Google Calendar, etc.)
     */
    @GetMapping("/download/{sessionId}")
    public ResponseEntity<String> downloadCalendarFile(@PathVariable UUID sessionId) {
        log.info("Generating calendar download for session: {}", sessionId);
        
        try {
            // Fetch session and related data
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            Profile mentor = profileRepository.findById(session.getMentorId())
                    .orElseThrow(() -> new RuntimeException("Mentor not found"));
            
            Profile mentee = profileRepository.findById(session.getMenteeId())
                    .orElseThrow(() -> new RuntimeException("Mentee not found"));
            
            // Get meeting details if available
            MeetingDetails meetingDetails = null;
            if (session.getMeetingUrl() != null) {
                meetingDetails = MeetingDetails.builder()
                    .meetingUrl(session.getMeetingUrl())
                    .meetingId(session.getMeetingId())
                    .password(session.getMeetingPassword())
                    .build();
            }
            
            // Generate ICS content
            String icsContent = calendarService.generateIcsFile(session, mentor, mentee, meetingDetails);
            
            // Set headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", 
                String.format("mentorship-session-%s.ics", session.getId().toString().substring(0, 8)));
            
            log.info("Successfully generated calendar file for session: {}", sessionId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(icsContent);
                    
        } catch (Exception e) {
            log.error("Failed to generate calendar file for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate calendar file");
        }
    }
    
    /**
     * Redirect to Google Calendar with session details
     */
    @GetMapping("/google/{sessionId}")
    public ResponseEntity<Void> redirectToGoogleCalendar(@PathVariable UUID sessionId) {
        return redirectToCalendarProvider(sessionId, "google");
    }
    
    /**
     * Redirect to Outlook Calendar with session details
     */
    @GetMapping("/outlook/{sessionId}")
    public ResponseEntity<Void> redirectToOutlookCalendar(@PathVariable UUID sessionId) {
        return redirectToCalendarProvider(sessionId, "outlook");
    }
    
    /**
     * Redirect to Yahoo Calendar with session details
     */
    @GetMapping("/yahoo/{sessionId}")
    public ResponseEntity<Void> redirectToYahooCalendar(@PathVariable UUID sessionId) {
        return redirectToCalendarProvider(sessionId, "yahoo");
    }
    
    /**
     * Get calendar links for all providers (for frontend dropdown)
     */
    @GetMapping("/links/{sessionId}")
    public ResponseEntity<CalendarService.CalendarLinks> getCalendarLinks(@PathVariable UUID sessionId) {
        log.info("Generating calendar links for session: {}", sessionId);
        
        try {
            // Fetch session and related data
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            Profile mentor = profileRepository.findById(session.getMentorId())
                    .orElseThrow(() -> new RuntimeException("Mentor not found"));
            
            Profile mentee = profileRepository.findById(session.getMenteeId())
                    .orElseThrow(() -> new RuntimeException("Mentee not found"));
            
            // Get meeting details if available
            MeetingDetails meetingDetails = null;
            if (session.getMeetingUrl() != null) {
                meetingDetails = MeetingDetails.builder()
                    .meetingUrl(session.getMeetingUrl())
                    .meetingId(session.getMeetingId())
                    .password(session.getMeetingPassword())
                    .build();
            }
            
            // Generate calendar links
            CalendarService.CalendarLinks links = calendarService.generateCalendarLinks(session, mentor, mentee, meetingDetails);
            
            log.info("Successfully generated calendar links for session: {}", sessionId);
            return ResponseEntity.ok(links);
            
        } catch (Exception e) {
            log.error("Failed to generate calendar links for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Generic method to redirect to calendar providers
     */
    private ResponseEntity<Void> redirectToCalendarProvider(UUID sessionId, String provider) {
        log.info("Redirecting to {} calendar for session: {}", provider, sessionId);
        
        try {
            // Fetch session and related data
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            Profile mentor = profileRepository.findById(session.getMentorId())
                    .orElseThrow(() -> new RuntimeException("Mentor not found"));
            
            Profile mentee = profileRepository.findById(session.getMenteeId())
                    .orElseThrow(() -> new RuntimeException("Mentee not found"));
            
            // Get meeting details if available
            MeetingDetails meetingDetails = null;
            if (session.getMeetingUrl() != null) {
                meetingDetails = MeetingDetails.builder()
                    .meetingUrl(session.getMeetingUrl())
                    .meetingId(session.getMeetingId())
                    .password(session.getMeetingPassword())
                    .build();
            }
            
            // Generate calendar links
            CalendarService.CalendarLinks links = calendarService.generateCalendarLinks(session, mentor, mentee, meetingDetails);
            
            // Get the appropriate URL
            String redirectUrl;
            switch (provider.toLowerCase()) {
                case "google":
                    redirectUrl = links.getGoogleCalendar();
                    break;
                case "outlook":
                    redirectUrl = links.getOutlookCalendar();
                    break;
                case "yahoo":
                    redirectUrl = links.getYahooCalendar();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported calendar provider: " + provider);
            }
            
            // Redirect to the calendar provider
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", redirectUrl);
            
            log.info("Successfully redirecting to {} calendar for session: {}", provider, sessionId);
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
            
        } catch (Exception e) {
            log.error("Failed to redirect to {} calendar for session {}: {}", provider, sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
