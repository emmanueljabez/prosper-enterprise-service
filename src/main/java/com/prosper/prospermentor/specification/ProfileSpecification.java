package com.prosper.prospermentor.specification;

import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Company;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specification for Profile entity
 * Provides reusable query criteria for filtering profiles
 */
public class ProfileSpecification {

    /**
     * Filter by company ID
     */
    public static Specification<Profile> hasCompanyId(UUID companyId) {
        return (root, query, criteriaBuilder) -> {
            if (companyId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Profile, Company> companyJoin = root.join("company");
            return criteriaBuilder.equal(companyJoin.get("id"), companyId);
        };
    }

    /**
     * Search by name or email (case-insensitive, partial match)
     */
    public static Specification<Profile> searchByNameOrEmail(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String searchPattern = "%" + search.toLowerCase().trim() + "%";

            List<Predicate> predicates = new ArrayList<>();

            // Search in first name
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("firstName")),
                    searchPattern
            ));

            // Search in last name
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("lastName")),
                    searchPattern
            ));

            // Search in email
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")),
                    searchPattern
            ));

            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by verified status
     */
    public static Specification<Profile> isVerified(Boolean verified) {
        return (root, query, criteriaBuilder) -> {
            if (verified == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isVerified"), verified);
        };
    }

    /**
     * Filter by role
     */
    public static Specification<Profile> hasRole(String role) {
        return (root, query, criteriaBuilder) -> {
            if (role == null || role.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("role"), role);
        };
    }

    /**
     * Combined specification for searching company profiles
     * Searches by name/email and filters by company
     */
    public static Specification<Profile> searchCompanyProfiles(
            UUID companyId,
            String search) {
        return Specification.where(hasCompanyId(companyId))
                .and(searchByNameOrEmail(search));
    }

    /**
     * Advanced search with multiple filters
     */
    public static Specification<Profile> advancedSearch(
            UUID companyId,
            String search,
            Boolean verifiedOnly,
            String role) {

        Specification<Profile> spec = Specification.where(hasCompanyId(companyId));

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and(searchByNameOrEmail(search));
        }

        if (Boolean.TRUE.equals(verifiedOnly)) {
            spec = spec.and(isVerified(true));
        }

        if (role != null && !role.trim().isEmpty()) {
            spec = spec.and(hasRole(role));
        }

        return spec;
    }
}
