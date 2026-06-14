package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSessionAllocationRepository extends JpaRepository<EmployeeSessionAllocation, UUID> {

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    Optional<EmployeeSessionAllocation> findByCompany_IdAndProfile_Id(UUID companyId, UUID profileId);

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    List<EmployeeSessionAllocation> findByCompany_Id(UUID companyId);

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    @Query("""
            SELECT allocation
            FROM EmployeeSessionAllocation allocation
            JOIN allocation.profile profile
            WHERE allocation.company.id = :companyId
              AND (:searchTerm = ''
                   OR LOWER(COALESCE(profile.firstName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.lastName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.username, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(COALESCE(profile.email, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    Page<EmployeeSessionAllocation> findByCompanyIdWithSearch(@Param("companyId") UUID companyId,
                                                              @Param("searchTerm") String searchTerm,
                                                              Pageable pageable);

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    List<EmployeeSessionAllocation> findByCompany_IdAndProfile_IdIn(UUID companyId, Collection<UUID> profileIds);

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    Optional<EmployeeSessionAllocation> findByProfile_Id(UUID profileId);

    @EntityGraph(attributePaths = {"company", "profile", "profile.company"})
    @Query("""
            SELECT allocation
            FROM EmployeeSessionAllocation allocation
            WHERE allocation.profile.id = :profileId
              AND allocation.availableBalance > 0
            ORDER BY allocation.updatedAt DESC
            """)
    Optional<EmployeeSessionAllocation> findActiveByProfileId(@Param("profileId") UUID profileId);
}
