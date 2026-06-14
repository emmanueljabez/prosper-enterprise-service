package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, UUID> {

    @EntityGraph(attributePaths = {
            "reviewCycle",
            "reviewCycle.session",
            "reviewCycle.participant",
            "reviewCycle.companyProgram",
            "reviewCycle.mentorAssignment",
            "reviewerProfile",
            "targetProfile"
    })
    List<ReviewRequest> findByReviewCycle_IdOrderByCreatedAtAsc(UUID reviewCycleId);

    @EntityGraph(attributePaths = {
            "reviewCycle",
            "reviewCycle.session",
            "reviewCycle.participant",
            "reviewCycle.companyProgram",
            "reviewCycle.mentorAssignment",
            "reviewerProfile",
            "targetProfile"
    })
    List<ReviewRequest> findByReviewCycle_IdInOrderByCreatedAtAsc(Collection<UUID> reviewCycleIds);

    @EntityGraph(attributePaths = {
            "reviewCycle",
            "reviewCycle.session",
            "reviewCycle.participant",
            "reviewCycle.companyProgram",
            "reviewerProfile",
            "targetProfile"
    })
    Optional<ReviewRequest> findById(UUID id);

    @EntityGraph(attributePaths = {
            "reviewCycle",
            "reviewCycle.session",
            "reviewCycle.participant",
            "reviewCycle.companyProgram",
            "reviewerProfile",
            "targetProfile"
    })
    List<ReviewRequest> findByReviewerProfile_IdOrderByCreatedAtDesc(UUID reviewerProfileId);

    @EntityGraph(attributePaths = {
            "reviewCycle",
            "reviewCycle.session",
            "reviewCycle.participant",
            "reviewCycle.companyProgram",
            "reviewCycle.mentorAssignment",
            "reviewerProfile",
            "targetProfile"
    })
    List<ReviewRequest> findByStatusInAndReviewCycle_StatusInAndReviewCycle_ExpiresAtAfter(
            Collection<ReviewRequest.ReviewRequestStatus> statuses,
            Collection<ReviewCycle.ReviewCycleStatus> cycleStatuses,
            LocalDateTime expiresAt
    );

    Optional<ReviewRequest> findByFlowToken(String flowToken);
}
