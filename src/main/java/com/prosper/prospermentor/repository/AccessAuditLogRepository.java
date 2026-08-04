package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.AccessAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessAuditLogRepository extends JpaRepository<AccessAuditLog, UUID> {

    @EntityGraph(attributePaths = {
            "company",
            "companyProgram",
            "participant",
            "participant.profile"
    })
    @Query("""
            SELECT aal
            FROM AccessAuditLog aal
            WHERE aal.company.id = :companyId
              AND (:companyProgramId IS NULL OR aal.companyProgram.id = :companyProgramId)
              AND (:resourceType IS NULL OR aal.resourceType = :resourceType)
              AND (:actionType IS NULL OR aal.action = :actionType)
            ORDER BY aal.createdAt DESC
            """)
    Page<AccessAuditLog> findCompanyAuditLogs(@Param("companyId") UUID companyId,
                                              @Param("companyProgramId") UUID companyProgramId,
                                              @Param("resourceType") AccessAuditLog.ResourceType resourceType,
                                              @Param("actionType") AccessAuditLog.ActionType actionType,
                                              Pageable pageable);

    @EntityGraph(attributePaths = {
            "company",
            "companyProgram",
            "participant",
            "participant.profile"
    })
    List<AccessAuditLog> findTop10ByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    long countByCompany_Id(UUID companyId);

    long countByCompany_IdAndCreatedAtAfter(UUID companyId, LocalDateTime threshold);

    long countByCompany_IdAndResourceTypeAndCreatedAtAfter(UUID companyId,
                                                           AccessAuditLog.ResourceType resourceType,
                                                           LocalDateTime threshold);

    long countByCompany_IdAndActionAndCreatedAtAfter(UUID companyId,
                                                     AccessAuditLog.ActionType actionType,
                                                     LocalDateTime threshold);

    @Query("""
            SELECT COUNT(aal)
            FROM AccessAuditLog aal
            WHERE aal.company.id = :companyId
              AND aal.action = :actionType
              AND aal.createdAt >= :startAt
              AND aal.createdAt < :endAt
            """)
    long countByCompanyActionInRange(@Param("companyId") UUID companyId,
                                     @Param("actionType") AccessAuditLog.ActionType actionType,
                                     @Param("startAt") LocalDateTime startAt,
                                     @Param("endAt") LocalDateTime endAt);

    @Query("""
            SELECT COUNT(DISTINCT aal.actorId)
            FROM AccessAuditLog aal
            WHERE aal.company.id = :companyId
              AND aal.createdAt >= :threshold
              AND aal.actorId IS NOT NULL
            """)
    long countDistinctActorsSince(@Param("companyId") UUID companyId,
                                  @Param("threshold") LocalDateTime threshold);
}
