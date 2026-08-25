package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRegionRepository extends JpaRepository<CompanyRegion, UUID> {

    @EntityGraph(attributePaths = {"company"})
    @Query("""
            SELECT r
            FROM CompanyRegion r
            WHERE r.company.id = :companyId
              AND (:searchTerm = ''
                   OR LOWER(r.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(r.code, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyRegion> findByCompanyIdWithFilters(@Param("companyId") UUID companyId,
                                                   @Param("searchTerm") String searchTerm,
                                                   Pageable pageable);

    @EntityGraph(attributePaths = {"company"})
    Optional<CompanyRegion> findByIdAndCompany_Id(UUID id, UUID companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(UUID companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);

    boolean existsByCompany_IdAndCodeIgnoreCase(UUID companyId, String code);

    boolean existsByCompany_IdAndCodeIgnoreCaseAndIdNot(UUID companyId, String code, UUID id);

    @Query("""
            SELECT c.region.id, COUNT(c.id)
            FROM CompanyChapter c
            WHERE c.region.id IN :regionIds
            GROUP BY c.region.id
            """)
    List<Object[]> countChaptersByRegionIds(@Param("regionIds") List<UUID> regionIds);
}
