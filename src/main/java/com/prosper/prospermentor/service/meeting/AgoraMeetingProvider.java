package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class AgoraMeetingProvider implements MeetingProvider {

    private final String roomFrontendUrl;

    public AgoraMeetingProvider(
            @Value("${agora.room-frontend-url:${app.frontend-url:http://localhost:3000}}") String roomFrontendUrl
    ) {
        this.roomFrontendUrl = trimTrailingSlash(roomFrontendUrl);
    }

    @Override
    public MeetingDetails createMeeting(Session session) {
        UUID sessionId = session.getId();
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required to create an Agora meeting");
        }

        String channelName = channelNameFor(sessionId);
        String meetingUrl = roomFrontendUrl + "/app/sessions/" + sessionId + "/room";

        log.info("Created Agora channel {} for session {}", channelName, sessionId);

        return MeetingDetails.builder()
                .meetingId(channelName)
                .meetingUrl(meetingUrl)
                .joinUrl(meetingUrl)
                .platformSpecificData("Agora RTC channel: " + channelName)
                .build();
    }

    @Override
    public MeetingDetails updateMeeting(String meetingId, Session session) {
        return createMeeting(session);
    }

    @Override
    public void cancelMeeting(String meetingId) {
        log.info("Agora channel {} requires no explicit cancellation", meetingId);
    }

    @Override
    public MeetingDetails getMeetingDetails(String meetingId) {
        return MeetingDetails.builder()
                .meetingId(meetingId)
                .platformSpecificData("Agora RTC channel: " + meetingId)
                .build();
    }

    @Override
    public boolean supports(Session.MeetingPlatform platform) {
        return platform == Session.MeetingPlatform.AGORA;
    }

    public static String channelNameFor(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required to build an Agora channel name");
        }
        return "pm-session-" + sessionId;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
