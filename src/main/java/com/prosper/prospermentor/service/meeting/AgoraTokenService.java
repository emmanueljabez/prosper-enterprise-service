package com.prosper.prospermentor.service.meeting;

import io.agora.media.RtcTokenBuilder2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AgoraTokenService {

    private final boolean enabled;
    private final String appId;
    private final String appCertificate;
    private final int tokenExpirySeconds;
    private final Clock clock;

    @Autowired
    public AgoraTokenService(
            @Value("${agora.enabled:false}") boolean enabled,
            @Value("${agora.app-id:}") String appId,
            @Value("${agora.app-certificate:}") String appCertificate,
            @Value("${agora.token-expiry-seconds:3600}") int tokenExpirySeconds
    ) {
        this(enabled, appId, appCertificate, tokenExpirySeconds, Clock.systemUTC());
    }

    public AgoraTokenService(
            boolean enabled,
            String appId,
            String appCertificate,
            int tokenExpirySeconds,
            Clock clock
    ) {
        this.enabled = enabled;
        this.appId = appId;
        this.appCertificate = appCertificate;
        this.tokenExpirySeconds = tokenExpirySeconds;
        this.clock = clock;
    }

    public AgoraJoinToken createJoinToken(String channelName, UUID profileId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Agora is not configured");
        }
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("Agora channel name is required");
        }
        if (profileId == null) {
            throw new IllegalArgumentException("Agora user profile ID is required");
        }

        int expiresInSeconds = Math.max(60, tokenExpirySeconds);
        String uid = profileId.toString();
        String token = new RtcTokenBuilder2().buildTokenWithUserAccount(
                appId,
                appCertificate,
                channelName,
                uid,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                expiresInSeconds,
                expiresInSeconds
        );

        return new AgoraJoinToken(
                appId,
                channelName,
                uid,
                token,
                Instant.now(clock).plusSeconds(expiresInSeconds)
        );
    }

    private boolean isConfigured() {
        return enabled
                && appId != null
                && !appId.isBlank()
                && appCertificate != null
                && !appCertificate.isBlank();
    }

    public record AgoraJoinToken(
            String appId,
            String channelName,
            String uid,
            String token,
            Instant expiresAt
    ) {
    }
}
