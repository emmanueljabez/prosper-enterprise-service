package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyDepartment;
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
public interface CompanyDepartmentRepository extends JpaRepository<CompanyDepartment, UUID> {

    @EntityGraph(attributePaths = {"company"})
    @Query("""
            SELECT d
            FROM CompanyDepartment d
            WHERE d.company.id = :companyId
              AND (:searchTerm = ''
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(d.description, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyDepartment> findByCompanyIdWithFilters(@Param("companyId") UUID companyId,
                                                       @Param("searchTerm") String searchTerm,
                                                       Pageable pageable);

    @EntityGraph(attributePaths = {"company"})
    Optional<CompanyDepartment> findByIdAndCompany_Id(UUID id, UUID companyId);

    boolean existsByCompany_IdAndNameIgnoreCase(UUID companyId, String name);

    boolean existsByCompany_IdAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);

    boolean existsByCompany_IdAndCodeIgnoreCase(UUID companyId, String code);

    boolean existsByCompany_IdAndCodeIgnoreCaseAndIdNot(UUID companyId, String code, UUID id);

    @Query("""
            SELECT m.department.id, COUNT(m.id)
            FROM CompanyDepartmentMember m
            WHERE m.department.id IN :departmentIds
            GROUP BY m.department.id
            """)
    List<Object[]> countMembersByDepartmentIds(@Param("departmentIds") List<UUID> departmentIds);
}
