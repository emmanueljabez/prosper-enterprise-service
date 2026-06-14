package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ReviewAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface ReviewAlertRepository extends JpaRepository<ReviewAlert, UUID> {

    Optional<ReviewAlert> findByAlertKey(String alertKey);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "participant",
            "participant.profile",
            "mentorAssignment",
            "mentorAssignment.mentor",
            "reviewCycle",
            "reviewRequest"
    })
    @Query("""
            SELECT ra
            FROM ReviewAlert ra
            JOIN ra.companyProgram cp
            WHERE cp.company.id = :companyId
              AND (:companyProgramId IS NULL OR cp.id = :companyProgramId)
              AND (:status IS NULL OR ra.status = :status)
              AND (:severity IS NULL OR ra.severity = :severity)
              AND (:alertType IS NULL OR ra.alertType = :alertType)
            """)
    Page<ReviewAlert> findCompanyAlerts(@Param("companyId") UUID companyId,
                                        @Param("companyProgramId") UUID companyProgramId,
                                        @Param("status") ReviewAlert.ReviewAlertStatus status,
                                        @Param("severity") ReviewAlert.Severity severity,
                                        @Param("alertType") ReviewAlert.ReviewAlertType alertType,
                                        Pageable pageable);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "participant",
            "participant.profile",
            "mentorAssignment",
            "mentorAssignment.mentor",
            "reviewCycle",
            "reviewRequest"
    })
    @Query("""
            SELECT ra
            FROM ReviewAlert ra
            JOIN ra.companyProgram cp
            WHERE cp.company.id = :companyId
              AND (:companyProgramId IS NULL OR cp.id = :companyProgramId)
              AND ra.createdAt >= :startAt
              AND ra.createdAt < :endAt
            ORDER BY ra.createdAt DESC
            """)
    List<ReviewAlert> findCompanyAlertsForSummary(@Param("companyId") UUID companyId,
                                                  @Param("companyProgramId") UUID companyProgramId,
                                                  @Param("startAt") LocalDateTime startAt,
                                                  @Param("endAt") LocalDateTime endAt);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "participant",
            "participant.profile",
            "mentorAssignment",
            "mentorAssignment.mentor",
            "reviewCycle",
            "reviewRequest"
    })
    Optional<ReviewAlert> findByIdAndCompanyProgram_Company_Id(UUID id, UUID companyId);

    List<ReviewAlert> findByParticipant_IdAndStatusIn(UUID participantId,
                                                      Collection<ReviewAlert.ReviewAlertStatus> statuses);
}
