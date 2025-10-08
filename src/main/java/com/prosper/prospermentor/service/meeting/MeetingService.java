package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Meeting service that delegates to appropriate meeting providers
 * Follows Strategy pattern and Dependency Inversion principle
 */
@Service
@Slf4j
public class MeetingService {
    
    private final List<MeetingProvider> meetingProviders;
    
    public MeetingService(List<MeetingProvider> meetingProviders) {
        this.meetingProviders = meetingProviders;
    }
    
    /**
     * Create a meeting for the given session
     */
    public MeetingDetails createMeeting(Session session) {
        log.info("Creating meeting for session: {} with platform: {}", 
                session.getId(), session.getMeetingPlatform());
        
        MeetingProvider provider = getProviderForPlatform(session.getMeetingPlatform())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No provider found for platform: " + session.getMeetingPlatform()));
        
        return provider.createMeeting(session);
    }
    
    /**
     * Update an existing meeting
     */
    public MeetingDetails updateMeeting(String meetingId, Session session) {
        log.info("Updating meeting: {} for session: {}", meetingId, session.getId());
        
        MeetingProvider provider = getProviderForPlatform(session.getMeetingPlatform())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No provider found for platform: " + session.getMeetingPlatform()));
        
        return provider.updateMeeting(meetingId, session);
    }
    
    /**
     * Cancel a meeting
     */
    public void cancelMeeting(String meetingId, Session.MeetingPlatform platform) {
        log.info("Cancelling meeting: {} for platform: {}", meetingId, platform);
        
        MeetingProvider provider = getProviderForPlatform(platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No provider found for platform: " + platform));
        
        provider.cancelMeeting(meetingId);
    }
    
    /**
     * Get meeting details
     */
    public MeetingDetails getMeetingDetails(String meetingId, Session.MeetingPlatform platform) {
        log.info("Getting meeting details: {} for platform: {}", meetingId, platform);
        
        MeetingProvider provider = getProviderForPlatform(platform)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No provider found for platform: " + platform));
        
        return provider.getMeetingDetails(meetingId);
    }
    
    /**
     * Get available meeting platforms
     */
    public List<Session.MeetingPlatform> getAvailablePlatforms() {
        return meetingProviders.stream()
                .flatMap(provider -> 
                    java.util.Arrays.stream(Session.MeetingPlatform.values())
                        .filter(provider::supports))
                .distinct()
                .toList();
    }
    
    /**
     * Find the appropriate provider for the given platform
     */
    private Optional<MeetingProvider> getProviderForPlatform(Session.MeetingPlatform platform) {
        return meetingProviders.stream()
                .filter(provider -> provider.supports(platform))
                .findFirst();
    }
}
