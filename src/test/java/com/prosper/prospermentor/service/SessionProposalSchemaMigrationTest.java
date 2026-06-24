package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionProposalSchemaMigrationTest {

    @Test
    void migration_shouldRelaxLegacyProposalSlotColumns() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V73__Backfill_session_proposal_slot_legacy_columns.sql"
        );

        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql).contains("ALTER COLUMN proposed_start DROP NOT NULL");
        assertThat(sql).contains("ALTER COLUMN proposed_end DROP NOT NULL");
        assertThat(sql).contains("UPDATE session_proposal_slots");
        assertThat(sql).contains("scheduled_start = proposed_start");
        assertThat(sql).contains("scheduled_end = proposed_end");
    }
}
