package com.prosper.prospermentor.badge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeSchemaMigrationTest {

    @Test
    void migration_shouldCreateBadgeSystemTablesAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V90__Create_badge_system_tables.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS badge_types")
                .contains("CREATE TABLE IF NOT EXISTS profile_badge_awards")
                .contains("CREATE TABLE IF NOT EXISTS badge_affiliation_rules")
                .contains("CREATE TABLE IF NOT EXISTS badge_audit_events")
                .contains("uk_badge_types_slug")
                .contains("uk_profile_badge_awards_active_profile_type")
                .contains("uk_profile_badge_awards_primary")
                .contains("uk_badge_affiliation_rules_company_role_active")
                .contains("ck_badge_types_category")
                .contains("ck_profile_badge_awards_visibility")
                .contains("BADGE_PRIMARY_CHANGED");
    }
}
