package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.UUID;

@Service
public class AgoraSessionAccessService {

    private static final EnumSet<Session.SessionStatus> JOINABLE_STATUSES = EnumSet.of(
            Session.SessionStatus.CONFIRMED,
            Session.SessionStatus.SCHEDULED,
            Session.SessionStatus.IN_PROGRESS
    );
    private static final long JOIN_WINDOW_MINUTES_BEFORE_START = 15;
    private static final long JOIN_WINDOW_MINUTES_AFTER_END = 30;

    private final SessionRepository sessionRepository;
    private final AgoraTokenService agoraTokenService;
    private final Clock clock;

    @Autowired
    public AgoraSessionAccessService(SessionRepository sessionRepository, AgoraTokenService agoraTokenService) {
        this(sessionRepository, agoraTokenService, Clock.systemUTC());
    }

    public AgoraSessionAccessService(
            SessionRepository sessionRepository,
            AgoraTokenService agoraTokenService,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.agoraTokenService = agoraTokenService;
        this.clock = clock;
    }

    public AgoraTokenService.AgoraJoinToken createJoinToken(UUID sessionId, UUID requesterProfileId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        validateAgoraSession(session);
        validateParticipant(session, requesterProfileId);
        validateJoinWindow(session);

        String channelName = session.getMeetingId();
        if (channelName == null || channelName.isBlank()) {
            channelName = AgoraMeetingProvider.channelNameFor(session.getId());
        }

        return agoraTokenService.createJoinToken(channelName, requesterProfileId);
    }

    private void validateAgoraSession(Session session) {
        if (session.getMeetingPlatform() != Session.MeetingPlatform.AGORA) {
            throw new IllegalStateException("Session is not configured for Agora");
        }

        if (!JOINABLE_STATUSES.contains(session.getStatus())) {
            throw new IllegalStateException("Agora tokens are only available for confirmed session meetings");
        }
    }

    private void validateParticipant(Session session, UUID requesterProfileId) {
        if (requesterProfileId == null
                || (!requesterProfileId.equals(session.getMentorId()) && !requesterProfileId.equals(session.getMenteeId()))) {
            throw new SecurityException("Authenticated user is not a participant in this session");
        }
    }

    private void validateJoinWindow(Session session) {
        if (session.getScheduledStart() == null || session.getScheduledEnd() == null) {
            throw new IllegalStateException("Session schedule is required before joining Agora");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime opensAt = session.getScheduledStart().minusMinutes(JOIN_WINDOW_MINUTES_BEFORE_START);
        ZonedDateTime closesAt = session.getScheduledEnd().plusMinutes(JOIN_WINDOW_MINUTES_AFTER_END);

        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw new IllegalStateException("Agora token is outside the session join window");
        }
    }
}
