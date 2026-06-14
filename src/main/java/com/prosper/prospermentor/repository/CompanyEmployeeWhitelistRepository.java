package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyEmployeeWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CompanyEmployeeWhitelist entity
 */
@Repository
public interface CompanyEmployeeWhitelistRepository extends JpaRepository<CompanyEmployeeWhitelist, UUID>, JpaSpecificationExecutor<CompanyEmployeeWhitelist> {

    /**
     * Find all whitelist entries for a company
     */
    List<CompanyEmployeeWhitelist> findByCompanyId(UUID companyId);

    /**
     * Find whitelist entry by company and email
     */
    Optional<CompanyEmployeeWhitelist> findByCompanyIdAndEmail(UUID companyId, String email);

    /**
     * Check if email exists in company whitelist
     */
    boolean existsByCompanyIdAndEmail(UUID companyId, String email);

    /**
     * Count whitelist entries for a company
     */
    long countByCompanyId(UUID companyId);

    /**
     * Delete all whitelist entries for a company
     */
    void deleteByCompanyId(UUID companyId);
}
