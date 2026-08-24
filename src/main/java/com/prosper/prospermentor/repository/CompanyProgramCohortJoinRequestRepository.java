package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramCohortJoinRequestRepository extends JpaRepository<CompanyProgramCohortJoinRequest, UUID> {

    @EntityGraph(attributePaths = {"cohort", "cohort.companyProgram", "matchedProfile"})
    Optional<CompanyProgramCohortJoinRequest> findById(UUID id);

    @EntityGraph(attributePaths = {"cohort", "matchedProfile"})
    List<CompanyProgramCohortJoinRequest> findByCohort_IdOrderByCreatedAtDesc(UUID cohortId);
}
