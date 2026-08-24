package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CommonInterestCircle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommonInterestCircleRepository extends JpaRepository<CommonInterestCircle, UUID> {

    @EntityGraph(attributePaths = {"cohort", "facilitatorProfile"})
    List<CommonInterestCircle> findByCohort_IdOrderByCreatedAtAsc(UUID cohortId);

    @EntityGraph(attributePaths = {"cohort", "facilitatorProfile"})
    Optional<CommonInterestCircle> findByIdAndCohort_Id(UUID circleId, UUID cohortId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT circle FROM CommonInterestCircle circle WHERE circle.id = :circleId")
    Optional<CommonInterestCircle> findByIdForUpdate(@Param("circleId") UUID circleId);
}
