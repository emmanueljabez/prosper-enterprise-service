package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Company entity
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * Find company by email address
     */
    Optional<Company> findByEmailAddress(String emailAddress);

    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.recommendedPrograms WHERE c.id = :id")
    Optional<Company> findByIdWithRecommendedPrograms(@Param("id") UUID id);

    /**
     * Find company by name
     */
    Optional<Company> findByName(String name);

    /**
     * Find all active companies
     */
    List<Company> findByIsActive(Boolean isActive);

    /**
     * Find active companies with pagination
     */
    Page<Company> findByIsActive(Boolean isActive, Pageable pageable);

    /**
     * Find companies by name containing (case insensitive)
     */
    @Query("SELECT c FROM Company c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Company> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find companies by name containing (case insensitive) with pagination
     */
    @Query("SELECT c FROM Company c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Company> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    /**
     * Find active companies by name containing (case insensitive) with pagination
     */
    @Query("SELECT c FROM Company c WHERE c.isActive = :isActive AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Company> findByIsActiveAndNameContainingIgnoreCase(@Param("isActive") Boolean isActive,
                                                            @Param("name") String name,
                                                            Pageable pageable);

    /**
     * Check if email address exists
     */
    boolean existsByEmailAddress(String emailAddress);
}
