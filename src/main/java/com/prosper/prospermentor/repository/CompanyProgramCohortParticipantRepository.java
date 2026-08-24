package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramCohortParticipantRepository extends JpaRepository<CompanyProgramCohortParticipant, UUID> {

    @EntityGraph(attributePaths = {
            "cohort",
            "cohort.companyProgram",
            "cohort.companyProgram.company",
            "profile",
            "companyProgramParticipant"
    })
    List<CompanyProgramCohortParticipant> findByCohort_Id(UUID cohortId);

    @EntityGraph(attributePaths = {"cohort", "profile", "companyProgramParticipant"})
    Optional<CompanyProgramCohortParticipant> findByCohort_IdAndProfile_Id(UUID cohortId, UUID profileId);

    @EntityGraph(attributePaths = {"cohort", "profile", "companyProgramParticipant"})
    List<CompanyProgramCohortParticipant> findByCompanyProgramParticipant_Id(UUID companyProgramParticipantId);

    @EntityGraph(attributePaths = {"cohort", "profile", "companyProgramParticipant"})
    List<CompanyProgramCohortParticipant> findByProfile_Id(UUID profileId);

    long countByCohort_IdAndStatusNot(UUID cohortId, CompanyProgramCohortParticipant.CohortParticipantStatus status);
}
