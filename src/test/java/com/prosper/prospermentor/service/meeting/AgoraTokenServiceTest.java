package com.prosper.prospermentor.service.meeting;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgoraTokenServiceTest {

    private static final String DUMMY_APP_ID = "00000000000000000000000000000000";
    private static final String DUMMY_APP_CERTIFICATE = "11111111111111111111111111111111";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createJoinToken_shouldReturnAccessToken2PayloadForUserAccount() {
        UUID profileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        AgoraTokenService service = new AgoraTokenService(true, DUMMY_APP_ID, DUMMY_APP_CERTIFICATE, 3600, CLOCK);

        AgoraTokenService.AgoraJoinToken token = service.createJoinToken("pm-session-test", profileId);

        assertThat(token.appId()).isEqualTo(DUMMY_APP_ID);
        assertThat(token.channelName()).isEqualTo("pm-session-test");
        assertThat(token.uid()).isEqualTo(profileId.toString());
        assertThat(token.token()).startsWith("007");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-09-06T13:00:00Z"));
    }

    @Test
    void createJoinToken_shouldRejectMissingConfiguration() {
        AgoraTokenService service = new AgoraTokenService(false, "", "", 3600, CLOCK);

        assertThatThrownBy(() -> service.createJoinToken("pm-session-test", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agora is not configured");
    }
}
