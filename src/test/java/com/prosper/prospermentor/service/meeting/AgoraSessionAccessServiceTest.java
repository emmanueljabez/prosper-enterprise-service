package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgoraSessionAccessServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MENTOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MENTEE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T12:55:00Z"), ZoneId.of("UTC"));

    private SessionRepository sessionRepository;
    private AgoraTokenService agoraTokenService;
    private AgoraSessionAccessService accessService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        agoraTokenService = mock(AgoraTokenService.class);
        accessService = new AgoraSessionAccessService(sessionRepository, agoraTokenService, CLOCK);
    }

    @Test
    void createJoinToken_shouldAllowMentorAndUseStoredMeetingChannel() {
        Session session = agoraSession(Session.SessionStatus.CONFIRMED);
        session.setMeetingId("pm-session-custom");
        AgoraTokenService.AgoraJoinToken expected = new AgoraTokenService.AgoraJoinToken(
                "app-id",
                "pm-session-custom",
                MENTOR_ID.toString(),
                "token",
                Instant.parse("2026-09-06T13:55:00Z")
        );

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(agoraTokenService.createJoinToken("pm-session-custom", MENTOR_ID)).thenReturn(expected);

        AgoraTokenService.AgoraJoinToken token = accessService.createJoinToken(SESSION_ID, MENTOR_ID);

        assertThat(token).isEqualTo(expected);
    }

    @Test
    void createJoinToken_shouldRejectNonParticipants() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(agoraSession(Session.SessionStatus.CONFIRMED)));

        assertThatThrownBy(() -> accessService.createJoinToken(SESSION_ID, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void createJoinToken_shouldRejectPendingSessions() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(agoraSession(Session.SessionStatus.PENDING)));

        assertThatThrownBy(() -> accessService.createJoinToken(SESSION_ID, MENTEE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed session");
    }

    @Test
    void createJoinToken_shouldRejectSessionsOutsideJoinWindow() {
        Session session = agoraSession(Session.SessionStatus.CONFIRMED);
        session.setScheduledStart(ZonedDateTime.parse("2026-09-07T10:00:00Z"));
        session.setScheduledEnd(ZonedDateTime.parse("2026-09-07T11:00:00Z"));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> accessService.createJoinToken(SESSION_ID, MENTEE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("join window");
    }

    private Session agoraSession(Session.SessionStatus status) {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setMentorId(MENTOR_ID);
        session.setMenteeId(MENTEE_ID);
        session.setSkillId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        session.setTitle("Leadership coaching");
        session.setStatus(status);
        session.setMeetingPlatform(Session.MeetingPlatform.AGORA);
        session.setScheduledStart(ZonedDateTime.parse("2026-09-06T13:00:00Z"));
        session.setScheduledEnd(ZonedDateTime.parse("2026-09-06T14:00:00Z"));
        return session;
    }
}
