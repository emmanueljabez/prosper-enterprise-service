package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyMentorEnrollmentMigrationTest {

    @Test
    void migration_shouldCreateMentorEnrollmentTablesAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V77__Create_company_mentor_enrollment_tables.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration);

        assertThat(sql).contains("CREATE TABLE company_mentor_invitations");
        assertThat(sql).contains("CREATE TABLE company_mentor_pool_memberships");
        assertThat(sql).contains("CREATE TABLE company_mentor_program_scopes");
        assertThat(sql).contains("invitation_token_hash");
        assertThat(sql).contains("email_delivery_status");
        assertThat(sql).contains("whatsapp_delivery_status");
        assertThat(sql).contains("public_listing_preexisting");
        assertThat(sql).contains("uniq_company_mentor_open_invitation_email");
        assertThat(sql).contains("uniq_company_mentor_active_membership");
        assertThat(sql).contains("idx_company_mentor_memberships_visibility");
    }
}
