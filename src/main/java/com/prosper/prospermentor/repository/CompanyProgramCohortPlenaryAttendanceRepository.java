package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramCohortPlenaryAttendanceRepository extends JpaRepository<CompanyProgramCohortPlenaryAttendance, UUID> {

    @EntityGraph(attributePaths = {"cohort", "cohortParticipant", "cohortParticipant.profile"})
    Optional<CompanyProgramCohortPlenaryAttendance> findByCohortParticipant_Id(UUID cohortParticipantId);

    @EntityGraph(attributePaths = {"cohortParticipant", "cohortParticipant.profile"})
    List<CompanyProgramCohortPlenaryAttendance> findByCohort_Id(UUID cohortId);
}
