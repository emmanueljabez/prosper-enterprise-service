package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommonInterestCircleMembershipRepository extends JpaRepository<CommonInterestCircleMembership, UUID> {

    @EntityGraph(attributePaths = {"circle", "cohortParticipant", "cohortParticipant.profile"})
    List<CommonInterestCircleMembership> findByCircle_Cohort_Id(UUID cohortId);

    @EntityGraph(attributePaths = {"circle", "cohortParticipant", "cohortParticipant.profile"})
    Optional<CommonInterestCircleMembership> findByCohortParticipant_IdAndStatus(UUID cohortParticipantId,
                                                                                 CommonInterestCircleMembership.MembershipStatus status);

    boolean existsByCohortParticipant_IdAndStatus(UUID cohortParticipantId,
                                                  CommonInterestCircleMembership.MembershipStatus status);

    long countByCircle_IdAndStatus(UUID circleId, CommonInterestCircleMembership.MembershipStatus status);
}
