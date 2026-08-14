package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyWalkthroughProgressMigrationTest {

    @Test
    void migration_shouldCreateAccountAwareWalkthroughProgressTable() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V79__Create_company_walkthrough_progress.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql).contains("CREATE TABLE company_user_walkthrough_progress");
        assertThat(sql).contains("company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE");
        assertThat(sql).contains("profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE");
        assertThat(sql).contains("completed_task_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[]");
        assertThat(sql).contains("completed_tour_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[]");
        assertThat(sql).contains("uniq_company_user_walkthrough_progress");
        assertThat(sql).contains("idx_company_walkthrough_company");
        assertThat(sql).contains("idx_company_walkthrough_profile");
    }
}
