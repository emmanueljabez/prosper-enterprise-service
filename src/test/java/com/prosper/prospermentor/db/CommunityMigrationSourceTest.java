package com.prosper.prospermentor.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityMigrationSourceTest {
    @Test
    void communityFoundationMigrationCreatesRequiredTablesAndSeedsCategories() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V78__Create_community_foundation_tables.sql"));

        assertThat(sql)
                .contains("CREATE TABLE community_categories")
                .contains("CREATE TABLE community_posts")
                .contains("CREATE TABLE community_post_reactions")
                .contains("CREATE TABLE community_post_comments")
                .contains("CREATE TABLE community_saved_posts")
                .contains("CREATE TABLE community_blocks")
                .contains("CREATE TABLE community_reports")
                .contains("CREATE TABLE community_mentions")
                .contains("CREATE TABLE community_notification_preferences")
                .contains("CREATE TABLE community_events_outbox")
                .contains("uniq_community_blocks_pair")
                .contains("uniq_community_open_report")
                .contains("idx_community_events_outbox_pending")
                .contains("'career-growth'")
                .contains("'mentorship'")
                .contains("'questions'");
    }
}
