package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyJoinLinkMigrationTest {

    @Test
    void migration_shouldCreateCompanyJoinLinksTableAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V89__Create_company_join_links.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql).contains("CREATE TABLE company_join_links");
        assertThat(sql).contains("company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE");
        assertThat(sql).contains("token_nonce varchar(96) NOT NULL");
        assertThat(sql).contains("token_hash varchar(128) NOT NULL");
        assertThat(sql).contains("status varchar(40) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("last_used_at timestamp");
        assertThat(sql).contains("chk_company_join_links_status");
        assertThat(sql).contains("uniq_company_join_links_active_company");
        assertThat(sql).contains("idx_company_join_links_token_hash");
    }
}
