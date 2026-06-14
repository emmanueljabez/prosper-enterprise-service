package com.prosper.prospermentor.specification;

import com.prosper.prospermentor.entity.CompanyEmployeeWhitelist;
import com.prosper.prospermentor.entity.Company;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification for CompanyEmployeeWhitelist entity
 * Provides reusable query criteria for filtering whitelist entries
 */
public class CompanyEmployeeWhitelistSpecification {

    /**
     * Filter by company ID
     */
    public static Specification<CompanyEmployeeWhitelist> hasCompanyId(UUID companyId) {
        return (root, query, criteriaBuilder) -> {
            if (companyId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<CompanyEmployeeWhitelist, Company> companyJoin = root.join("company");
            return criteriaBuilder.equal(companyJoin.get("id"), companyId);
        };
    }

    /**
     * Search by email (case-insensitive, partial match)
     */
    public static Specification<CompanyEmployeeWhitelist> searchByEmail(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String searchPattern = "%" + search.toLowerCase().trim() + "%";
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")),
                    searchPattern
            );
        };
    }

    /**
     * Filter by deleted status
     */
    public static Specification<CompanyEmployeeWhitelist> isNotDeleted() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("deleted"), false);
    }

    /**
     * Filter by active status (not deleted)
     */
    public static Specification<CompanyEmployeeWhitelist> isActive() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("deleted"), false);
    }

    /**
     * Filter by used status
     */
    public static Specification<CompanyEmployeeWhitelist> isUsed(Boolean used) {
        return (root, query, criteriaBuilder) -> {
            if (used == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isUsed"), used);
        };
    }

    /**
     * Filter by invitation accepted status
     */
    public static Specification<CompanyEmployeeWhitelist> invitationAccepted(Boolean accepted) {
        return (root, query, criteriaBuilder) -> {
            if (accepted == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("invitationAccepted"), accepted);
        };
    }

    /**
     * Combined specification for searching whitelisted employees
     * Searches by email and filters by company
     */
    public static Specification<CompanyEmployeeWhitelist> searchWhitelistedEmployees(
            UUID companyId,
            String search) {
        return Specification.where(hasCompanyId(companyId))
                .and(searchByEmail(search));
    }

    /**
     * Advanced search with multiple filters
     */
    public static Specification<CompanyEmployeeWhitelist> advancedSearch(
            UUID companyId,
            String search,
            Boolean activeOnly,
            Boolean unusedOnly) {

        Specification<CompanyEmployeeWhitelist> spec = Specification.where(hasCompanyId(companyId));

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and(searchByEmail(search));
        }

        if (Boolean.TRUE.equals(activeOnly)) {
            spec = spec.and(isActive());
        }

        if (Boolean.TRUE.equals(unusedOnly)) {
            spec = spec.and(isUsed(false));
        }

        return spec;
    }
}
