package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyChapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyChapterRepository extends JpaRepository<CompanyChapter, UUID> {

    @EntityGraph(attributePaths = {"company", "region"})
    @Query("""
            SELECT c
            FROM CompanyChapter c
            WHERE c.company.id = :companyId
              AND (:regionId IS NULL OR c.region.id = :regionId)
              AND (:searchTerm = ''
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(c.code, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(c.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyChapter> findByCompanyIdWithFilters(@Param("companyId") UUID companyId,
                                                    @Param("regionId") UUID regionId,
                                                    @Param("searchTerm") String searchTerm,
                                                    Pageable pageable);

    @EntityGraph(attributePaths = {"company", "region"})
    Optional<CompanyChapter> findByIdAndCompany_Id(UUID id, UUID companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(UUID companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);

    boolean existsByCompany_IdAndCodeIgnoreCase(UUID companyId, String code);

    boolean existsByCompany_IdAndCodeIgnoreCaseAndIdNot(UUID companyId, String code, UUID id);
}
