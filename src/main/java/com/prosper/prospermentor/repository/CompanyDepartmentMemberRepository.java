package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyDepartmentMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyDepartmentMemberRepository extends JpaRepository<CompanyDepartmentMember, UUID> {

    @EntityGraph(attributePaths = {"department", "profile"})
    @Query("""
            SELECT m
            FROM CompanyDepartmentMember m
            JOIN m.profile p
            WHERE m.department.id = :departmentId
              AND (:searchTerm = ''
                   OR LOWER(CONCAT(COALESCE(p.firstName, ''), ' ', COALESCE(p.lastName, '')))
                        LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(p.email, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(p.username, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<CompanyDepartmentMember> findByDepartmentIdWithSearch(@Param("departmentId") UUID departmentId,
                                                               @Param("searchTerm") String searchTerm,
                                                               Pageable pageable);

    @EntityGraph(attributePaths = {"department", "profile"})
    Optional<CompanyDepartmentMember> findByDepartment_IdAndProfile_Id(UUID departmentId, UUID profileId);

    @EntityGraph(attributePaths = {"department", "profile"})
    Optional<CompanyDepartmentMember> findByProfile_Id(UUID profileId);

    long countByDepartment_Id(UUID departmentId);

    @Modifying
    int deleteByDepartment_IdAndProfile_Id(UUID departmentId, UUID profileId);
}
