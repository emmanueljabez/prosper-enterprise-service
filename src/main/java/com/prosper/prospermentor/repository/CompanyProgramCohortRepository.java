package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramCohort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramCohortRepository extends JpaRepository<CompanyProgramCohort, UUID> {

    @EntityGraph(attributePaths = {"companyProgram", "companyProgram.company", "companyProgram.program"})
    List<CompanyProgramCohort> findByCompanyProgram_IdOrderByStartsAtDescCreatedAtDesc(UUID companyProgramId);

    @EntityGraph(attributePaths = {"companyProgram", "companyProgram.company", "companyProgram.program"})
    Optional<CompanyProgramCohort> findByIdAndCompanyProgram_Company_Id(UUID cohortId, UUID companyId);

    @EntityGraph(attributePaths = {"companyProgram", "companyProgram.company", "companyProgram.program"})
    Optional<CompanyProgramCohort> findBySelfJoinCodeHashAndSelfJoinEnabledTrue(String selfJoinCodeHash);
}
