package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.entity.Session;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgoraMeetingProviderTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void createMeeting_shouldCreateEnterpriseRoomUrlAndStableChannelName() {
        AgoraMeetingProvider provider = new AgoraMeetingProvider("https://enterprise.test/");
        Session session = session(Session.BookingSource.ENTERPRISE);

        MeetingDetails details = provider.createMeeting(session);

        assertThat(details.getMeetingId()).isEqualTo("pm-session-" + SESSION_ID);
        assertThat(details.getMeetingUrl()).isEqualTo("https://enterprise.test/app/sessions/" + SESSION_ID + "/room");
        assertThat(details.getJoinUrl()).isEqualTo(details.getMeetingUrl());
        assertThat(details.getPassword()).isNull();
        assertThat(details.getPlatformSpecificData()).contains("Agora RTC");
    }

    @Test
    void createMeeting_shouldUseConfiguredRoomFrontendUrlForB2cBookings() {
        AgoraMeetingProvider provider = new AgoraMeetingProvider("https://enterprise.test");
        Session session = session(Session.BookingSource.B2C);

        MeetingDetails details = provider.createMeeting(session);

        assertThat(details.getMeetingUrl()).isEqualTo("https://enterprise.test/app/sessions/" + SESSION_ID + "/room");
    }

    @Test
    void supports_shouldOnlySupportAgora() {
        AgoraMeetingProvider provider = new AgoraMeetingProvider("https://enterprise.test");

        assertThat(provider.supports(Session.MeetingPlatform.AGORA)).isTrue();
        assertThat(provider.supports(Session.MeetingPlatform.GOOGLE_MEET)).isFalse();
        assertThat(provider.supports(Session.MeetingPlatform.ZOOM)).isFalse();
    }

    private Session session(Session.BookingSource bookingSource) {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setMentorId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        session.setMenteeId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        session.setSkillId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        session.setTitle("Leadership coaching");
        session.setBookingSource(bookingSource);
        session.setMeetingPlatform(Session.MeetingPlatform.AGORA);
        session.setScheduledStart(ZonedDateTime.of(2026, 9, 6, 15, 0, 0, 0, ZoneId.of("Africa/Nairobi")));
        session.setScheduledEnd(session.getScheduledStart().plusHours(1));
        return session;
    }
}
