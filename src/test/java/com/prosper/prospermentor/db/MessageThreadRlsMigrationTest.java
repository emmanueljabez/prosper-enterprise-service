package com.prosper.prospermentor.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MessageThreadRlsMigrationTest {

    @Test
    void migration_shouldAllowAuthenticatedParticipantsToCreateMessageThreads() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V84__Repair_message_thread_rls_policies.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql)
                .contains("ALTER TABLE public.message_threads ENABLE ROW LEVEL SECURITY")
                .contains("DROP POLICY IF EXISTS \"Users can create message threads\"")
                .contains("CREATE POLICY \"Users can create message threads\"")
                .contains("FOR INSERT")
                .contains("WITH CHECK")
                .contains("auth.uid() = user1_id")
                .contains("auth.uid() = user2_id")
                .contains("CREATE POLICY \"Users can view their own message threads\"")
                .contains("CREATE POLICY \"Users can update their own message threads\"");
    }
}
