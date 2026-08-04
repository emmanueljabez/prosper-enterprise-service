package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramParticipant;
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
public interface CompanyProgramParticipantRepository extends JpaRepository<CompanyProgramParticipant, UUID> {

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program",
            "profile",
            "profile.company"
    })
    @Query("""
            SELECT cpp
            FROM CompanyProgramParticipant cpp
            JOIN cpp.profile profile
            WHERE cpp.companyProgram.id = :companyProgramId
              AND (:status IS NULL OR cpp.status = :status)
              AND COALESCE(cpp.enrolledAt, cpp.createdAt) >= :startAt
              AND COALESCE(cpp.enrolledAt, cpp.createdAt) < :endAt
              AND (:searchTerm = ''
                   OR LOWER(COALESCE(profile.firstName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.lastName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.username, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.email, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyProgramParticipant> findByCompanyProgramIdWithFilters(@Param("companyProgramId") UUID companyProgramId,
                                                                      @Param("status") CompanyProgramParticipant.ParticipantStatus status,
                                                                      @Param("searchTerm") String searchTerm,
                                                                      @Param("startAt") LocalDateTime startAt,
                                                                      @Param("endAt") LocalDateTime endAt,
                                                                      Pageable pageable);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program",
            "profile",
            "profile.company"
    })
    Optional<CompanyProgramParticipant> findById(UUID id);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program",
            "profile",
            "profile.company"
    })
    List<CompanyProgramParticipant> findByCompanyProgram_Company_Id(UUID companyId);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program",
            "profile",
            "profile.company"
    })
    List<CompanyProgramParticipant> findByCompanyProgram_IdAndProfile_IdIn(UUID companyProgramId, Collection<UUID> profileIds);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program",
            "companyProgram.journeyTemplate",
            "profile",
            "profile.company"
    })
    List<CompanyProgramParticipant> findByCompanyProgram_IdAndStatusIn(UUID companyProgramId,
                                                                       Collection<CompanyProgramParticipant.ParticipantStatus> statuses);

    long countByCompanyProgram_Id(UUID companyProgramId);

    @EntityGraph(attributePaths = {
            "companyProgram",
            "companyProgram.company",
            "companyProgram.program"
    })
    @Query("""
            SELECT cpp
            FROM CompanyProgramParticipant cpp
            WHERE cpp.profile.id = :profileId
              AND cpp.status IN :statuses
            ORDER BY cpp.enrolledAt DESC
            """)
    List<CompanyProgramParticipant> findByProfileIdAndStatusIn(@Param("profileId") UUID profileId,
                                                               @Param("statuses") Collection<CompanyProgramParticipant.ParticipantStatus> statuses);
}
