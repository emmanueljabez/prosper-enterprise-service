package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;

/**
 * Interface for meeting platform providers (Zoom, Google Meet, etc.)
 * Follows Open/Closed Principle - easy to add new meeting providers
 */
public interface MeetingProvider {
    
    /**
     * Create a meeting for the given session
     * @param session The session details
     * @return Meeting details with URL, ID, and password
     */
    MeetingDetails createMeeting(Session session);
    
    /**
     * Update an existing meeting
     * @param meetingId The meeting ID to update
     * @param session Updated session details
     * @return Updated meeting details
     */
    MeetingDetails updateMeeting(String meetingId, Session session);
    
    /**
     * Cancel/delete a meeting
     * @param meetingId The meeting ID to cancel
     */
    void cancelMeeting(String meetingId);
    
    /**
     * Get meeting details by ID
     * @param meetingId The meeting ID
     * @return Meeting details
     */
    MeetingDetails getMeetingDetails(String meetingId);
    
    /**
     * Check if this provider supports the given platform
     * @param platform The meeting platform
     * @return true if supported
     */
    boolean supports(Session.MeetingPlatform platform);
}
