package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Zoom meeting provider implementation
 * Integrates with Zoom API to create and manage meetings
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "zoom.enabled", havingValue = "true", matchIfMissing = false)
public class ZoomMeetingProvider implements MeetingProvider {
    
    private final RestTemplate restTemplate;
    
    @Value("${zoom.api.base-url:https://api.zoom.us/v2}")
    private String zoomApiBaseUrl;
    
    @Value("${zoom.api.jwt-token:}")
    private String zoomJwtToken;
    
    @Value("${zoom.api.account-id:}")
    private String zoomAccountId;
    
    @Value("${zoom.api.client-id:}")
    private String zoomClientId;
    
    @Value("${zoom.api.client-secret:}")
    private String zoomClientSecret;
    
    public ZoomMeetingProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public MeetingDetails createMeeting(Session session) {
        log.info("Creating Zoom meeting for session: {}", session.getId());
        
        try {
            // Prepare meeting request
            Map<String, Object> meetingRequest = prepareMeetingRequest(session);
            
            // Create HTTP headers with authorization
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + getAccessToken());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(meetingRequest, headers);
            
            // Make API call to create meeting
            String createMeetingUrl = zoomApiBaseUrl + "/users/me/meetings";
            ResponseEntity<Map> response = restTemplate.postForEntity(createMeetingUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                return parseMeetingResponse(response.getBody());
            } else {
                log.error("Failed to create Zoom meeting. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to create Zoom meeting");
            }
            
        } catch (Exception e) {
            log.error("Error creating Zoom meeting for session {}: {}", session.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create Zoom meeting: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MeetingDetails updateMeeting(String meetingId, Session session) {
        log.info("Updating Zoom meeting: {}", meetingId);
        
        try {
            Map<String, Object> meetingRequest = prepareMeetingRequest(session);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + getAccessToken());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(meetingRequest, headers);
            
            String updateMeetingUrl = zoomApiBaseUrl + "/meetings/" + meetingId;
            restTemplate.exchange(updateMeetingUrl, HttpMethod.PATCH, request, Void.class);
            
            // Return updated meeting details
            return getMeetingDetails(meetingId);
            
        } catch (Exception e) {
            log.error("Error updating Zoom meeting {}: {}", meetingId, e.getMessage(), e);
            throw new RuntimeException("Failed to update Zoom meeting: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void cancelMeeting(String meetingId) {
        log.info("Cancelling Zoom meeting: {}", meetingId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAccessToken());
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String deleteMeetingUrl = zoomApiBaseUrl + "/meetings/" + meetingId;
            restTemplate.exchange(deleteMeetingUrl, HttpMethod.DELETE, request, Void.class);
            
            log.info("Successfully cancelled Zoom meeting: {}", meetingId);
            
        } catch (Exception e) {
            log.error("Error cancelling Zoom meeting {}: {}", meetingId, e.getMessage(), e);
            throw new RuntimeException("Failed to cancel Zoom meeting: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MeetingDetails getMeetingDetails(String meetingId) {
        log.info("Getting Zoom meeting details: {}", meetingId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + getAccessToken());
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String getMeetingUrl = zoomApiBaseUrl + "/meetings/" + meetingId;
            ResponseEntity<Map> response = restTemplate.exchange(getMeetingUrl, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseMeetingResponse(response.getBody());
            } else {
                log.error("Failed to get Zoom meeting details. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get Zoom meeting details");
            }
            
        } catch (Exception e) {
            log.error("Error getting Zoom meeting details {}: {}", meetingId, e.getMessage(), e);
            throw new RuntimeException("Failed to get Zoom meeting details: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean supports(Session.MeetingPlatform platform) {
        return platform == Session.MeetingPlatform.ZOOM;
    }
    
    /**
     * Prepare meeting request payload for Zoom API
     */
    private Map<String, Object> prepareMeetingRequest(Session session) {
        Map<String, Object> meetingRequest = new HashMap<>();
        
        // Basic meeting info
        meetingRequest.put("topic", "Mentorship Session: " + session.getSkill().getName());
        meetingRequest.put("type", 2); // Scheduled meeting
        meetingRequest.put("start_time", session.getScheduledStart().format(DateTimeFormatter.ISO_INSTANT));
        meetingRequest.put("duration", calculateDurationInMinutes(session));
        meetingRequest.put("timezone", "UTC");
        
        // Meeting settings
        Map<String, Object> settings = new HashMap<>();
        settings.put("host_video", true);
        settings.put("participant_video", true);
        settings.put("join_before_host", false);
        settings.put("mute_upon_entry", true);
        settings.put("waiting_room", true);
        settings.put("audio", "both");
        settings.put("auto_recording", "none");
        
        meetingRequest.put("settings", settings);
        
        return meetingRequest;
    }
    
    /**
     * Parse Zoom API response to MeetingDetails
     */
    private MeetingDetails parseMeetingResponse(Map<String, Object> response) {
        return MeetingDetails.builder()
                .meetingUrl((String) response.get("join_url"))
                .meetingId(String.valueOf(response.get("id")))
                .password((String) response.get("password"))
                .startUrl((String) response.get("start_url"))
                .joinUrl((String) response.get("join_url"))
                .platformSpecificData(response.toString())
                .build();
    }
    
    /**
     * Calculate meeting duration in minutes
     */
    private int calculateDurationInMinutes(Session session) {
        return (int) java.time.Duration.between(
                session.getScheduledStart(),
                session.getScheduledEnd()
        ).toMinutes();
    }
    
    /**
     * Get Zoom access token (implement OAuth 2.0 flow or use JWT)
     * For production, implement proper OAuth 2.0 flow
     */
    private String getAccessToken() {
        // TODO: Implement proper OAuth 2.0 flow for production
        // For now, return JWT token if configured
        if (zoomJwtToken != null && !zoomJwtToken.isEmpty()) {
            return zoomJwtToken;
        }
        
        // Implement OAuth 2.0 token exchange here
        throw new RuntimeException("Zoom authentication not configured");
    }
}
