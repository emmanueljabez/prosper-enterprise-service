package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyLocationCatalogSchemaTest {

    @Test
    void migration_shouldCreateCompanyRegionAndChapterCatalogs() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V81__Create_company_region_chapter_catalogs.sql"
        ));

        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_regions");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_chapters");
        assertThat(migration).contains("region_id UUID REFERENCES company_regions(id) ON DELETE SET NULL");
        assertThat(migration).contains("uq_company_regions_company_name");
        assertThat(migration).contains("uq_company_chapters_company_name");
        assertThat(migration).contains("idx_company_chapters_region");
    }

    @Test
    void controller_shouldExposeCompanyRegionAndChapterCrudEndpoints() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/controller/CompanyLocationCatalogController.java"
        ));

        assertThat(controller).contains(
                "/companies/{companyId}/regions",
                "/companies/{companyId}/regions/{regionId}",
                "/companies/{companyId}/chapters",
                "/companies/{companyId}/chapters/{chapterId}"
        );
    }
}
