package com.prosper.prospermentor.badge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeSecurityConfigTest {

    @Test
    void publicProfileBadgeEndpointIsPermitAll() throws Exception {
        String securityConfig = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/config/SecurityConfig.java"
        ));

        assertThat(securityConfig)
                .contains(".requestMatchers(HttpMethod.GET, \"/api/v1/profiles/*/badges\").permitAll()");
    }
}
