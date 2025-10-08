package com.prosper.prospermentor.service.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value object representing meeting details
 * Immutable data structure following DDD principles
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDetails {
    
    private String meetingUrl;
    private String meetingId;
    private String password;
    private String dialInNumber;
    private String hostKey;
    private String joinUrl;
    private String startUrl;
    
    /**
     * Additional platform-specific metadata
     */
    private String platformSpecificData;
    
    /**
     * Check if meeting details are complete
     */
    public boolean isComplete() {
        return meetingUrl != null && !meetingUrl.trim().isEmpty() &&
               meetingId != null && !meetingId.trim().isEmpty();
    }
    
    /**
     * Get display-friendly meeting info
     */
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Meeting URL: ").append(meetingUrl);
        if (meetingId != null) {
            info.append("\nMeeting ID: ").append(meetingId);
        }
        if (password != null) {
            info.append("\nPassword: ").append(password);
        }
        if (dialInNumber != null) {
            info.append("\nDial-in: ").append(dialInNumber);
        }
        return info.toString();
    }
}


