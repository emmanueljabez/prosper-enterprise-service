package com.prosper.prospermentor.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgoraMeetingMigrationTest {

    @Test
    void migration_shouldAllowAgoraMeetingPlatform() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V86__Allow_agora_session_meeting_platform.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql)
                .contains("DROP CONSTRAINT IF EXISTS chk_sessions_meeting_platform")
                .contains("ADD CONSTRAINT chk_sessions_meeting_platform")
                .contains("'GOOGLE_MEET'")
                .contains("'ZOOM'")
                .contains("'AGORA'");
    }
}
