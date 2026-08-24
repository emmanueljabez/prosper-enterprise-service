package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyProgramCohortSchemaTest {

    @Test
    void migration_shouldCreateCohortCircleTablesAndConstraints() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V80__Create_company_program_cohorts_and_circles.sql"
        ));

        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_program_cohorts");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_program_cohort_participants");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_program_cohort_join_requests");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS company_program_cohort_plenary_attendance");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS common_interest_circles");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS common_interest_circle_memberships");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS common_interest_circle_notes");
        assertThat(migration).contains("UNIQUE (company_program_cohort_id, profile_id)");
        assertThat(migration).contains("UNIQUE (circle_id, cohort_participant_id)");
        assertThat(migration).contains("circle_max_size >= circle_min_size");
        assertThat(migration).contains("self_join_code_hash");
    }

    @Test
    void domain_shouldDefineCohortCircleEntitiesAndRepositoryContracts() throws Exception {
        String cohortEntity = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/entity/CompanyProgramCohort.java"
        ));
        String participantEntity = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/entity/CompanyProgramCohortParticipant.java"
        ));
        String circleEntity = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/entity/CommonInterestCircle.java"
        ));
        String cohortRepository = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/repository/CompanyProgramCohortRepository.java"
        ));
        String participantRepository = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/repository/CompanyProgramCohortParticipantRepository.java"
        ));
        String circleRepository = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/repository/CommonInterestCircleRepository.java"
        ));

        assertThat(cohortEntity).contains("enum CohortStatus", "enum PlenaryEventType");
        assertThat(participantEntity).contains(
                "enum ParticipantSource",
                "enum CohortParticipantStatus",
                "enum DuplicateStatus"
        );
        assertThat(circleEntity).contains("enum CircleStatus");
        assertThat(cohortRepository).contains(
                "findByCompanyProgram_IdOrderByStartsAtDescCreatedAtDesc",
                "findBySelfJoinCodeHashAndSelfJoinEnabledTrue"
        );
        assertThat(participantRepository).contains(
                "findByCohort_Id",
                "findByCohort_IdAndProfile_Id",
                "findByCompanyProgramParticipant_Id"
        );
        assertThat(circleRepository).contains("findByCohort_IdOrderByCreatedAtAsc");
    }

    @Test
    void controller_shouldExposeCohortIntakeEndpoints() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/prosper/prospermentor/controller/CompanyProgramCohortController.java"
        ));

        assertThat(controller).contains(
                "/company-program-cohorts/join/{joinCode}",
                "@PostMapping(\"/company-program-cohorts/{cohortId}/participants\")",
                "addRosterParticipants",
                "/company-program-cohorts/{cohortId}/participants",
                "/company-program-cohort-join-requests/{joinRequestId}/confirm",
                "/company-program-cohort-join-requests/{joinRequestId}/reject",
                "/company-program-cohort-participants/{participantId}/confirm",
                "/company-program-cohort-participants/{participantId}/reject",
                "/company-program-cohort-participants/{participantId}/resolve-duplicate",
                "/company-program-cohorts/{cohortId}/plenary",
                "/company-program-cohorts/{cohortId}/plenary/link-event",
                "/company-program-cohorts/{cohortId}/plenary/attendance/import",
                "/company-program-cohort-participants/{participantId}/plenary-attendance",
                "/company-program-cohorts/{cohortId}/circles",
                "/company-program-cohorts/{cohortId}/circle-suggestions",
                "/common-interest-circles/{circleId}",
                "/common-interest-circles/{circleId}/members",
                "/common-interest-circle-memberships/{membershipId}",
                "/common-interest-circle-memberships/{membershipId}/move",
                "/company-program-cohorts/{cohortId}/circles/finalize",
                "/company-program-cohorts/{cohortId}/dashboard",
                "/me/company-program-cohorts",
                "/me/company-program-cohorts/{cohortId}",
                "/me/company-program-cohorts/{cohortId}/circles",
                "/me/company-program-cohorts/{cohortId}/circle-requests"
        );
    }
}
