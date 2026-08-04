package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgram;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramRepository extends JpaRepository<CompanyProgram, UUID> {

    @EntityGraph(attributePaths = {"company", "program"})
    @Query("""
            SELECT cp
            FROM CompanyProgram cp
            WHERE cp.company.id = :companyId
              AND (:status IS NULL OR cp.status = :status)
              AND cp.createdAt >= :startAt
              AND cp.createdAt < :endAt
              AND (:searchTerm = ''
                   OR LOWER(cp.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(cp.objective, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyProgram> findByCompanyIdWithFilters(@Param("companyId") UUID companyId,
                                                    @Param("status") CompanyProgram.CompanyProgramStatus status,
                                                    @Param("searchTerm") String searchTerm,
                                                    @Param("startAt") LocalDateTime startAt,
                                                    @Param("endAt") LocalDateTime endAt,
                                                    Pageable pageable);

    @EntityGraph(attributePaths = {"company", "program"})
    Optional<CompanyProgram> findById(UUID id);

    @EntityGraph(attributePaths = {"company", "program"})
    Optional<CompanyProgram> findByIdAndCompany_Id(UUID id, UUID companyId);
}
