package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ReviewCycle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

    @EntityGraph(attributePaths = {
            "session",
            "mentorAssignment",
            "participant",
            "companyProgram",
            "mentorProfile",
            "menteeProfile"
    })
    Optional<ReviewCycle> findBySession_IdAndType(UUID sessionId, ReviewCycle.ReviewType type);

    @EntityGraph(attributePaths = {
            "session",
            "mentorAssignment",
            "participant",
            "companyProgram",
            "mentorProfile",
            "menteeProfile"
    })
    Optional<ReviewCycle> findByMentorAssignment_IdAndType(UUID mentorAssignmentId, ReviewCycle.ReviewType type);

    @EntityGraph(attributePaths = {
            "session",
            "mentorAssignment",
            "participant",
            "companyProgram",
            "mentorProfile",
            "menteeProfile"
    })
    List<ReviewCycle> findBySession_IdInAndTypeOrderByCreatedAtDesc(Collection<UUID> sessionIds, ReviewCycle.ReviewType type);

    @EntityGraph(attributePaths = {
            "session",
            "mentorAssignment",
            "participant",
            "companyProgram",
            "mentorProfile",
            "menteeProfile"
    })
    List<ReviewCycle> findByStatusInAndExpiresAtLessThanEqual(Collection<ReviewCycle.ReviewCycleStatus> statuses,
                                                              LocalDateTime expiresAt);

    @EntityGraph(attributePaths = {
            "session",
            "mentorAssignment",
            "participant",
            "companyProgram",
            "mentorProfile",
            "menteeProfile"
    })
    @Query("""
            SELECT rc
            FROM ReviewCycle rc
            JOIN rc.companyProgram cp
            WHERE cp.company.id = :companyId
              AND (:companyProgramId IS NULL OR cp.id = :companyProgramId)
              AND rc.createdAt >= :startAt
              AND rc.createdAt < :endAt
            ORDER BY rc.createdAt DESC
            """)
    List<ReviewCycle> findCompanyCyclesForSummary(@Param("companyId") UUID companyId,
                                                  @Param("companyProgramId") UUID companyProgramId,
                                                  @Param("startAt") LocalDateTime startAt,
                                                  @Param("endAt") LocalDateTime endAt);
}
