package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PlanFeature entity
 */
@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    /**
     * Find all features for a specific plan
     */
    List<PlanFeature> findByPlanId(UUID planId);

    /**
     * Find enabled features for a specific plan
     */
    List<PlanFeature> findByPlanIdAndEnabledTrue(UUID planId);

    /**
     * Find a specific plan feature by plan ID and feature code
     */
    @Query("SELECT pf FROM PlanFeature pf JOIN pf.feature f WHERE pf.plan.id = :planId AND f.code = :featureCode")
    Optional<PlanFeature> findByPlanIdAndFeatureCode(@Param("planId") UUID planId, @Param("featureCode") String featureCode);

    /**
     * Find all plans that have a specific feature
     */
    @Query("SELECT pf FROM PlanFeature pf JOIN pf.feature f WHERE f.code = :featureCode AND pf.enabled = true")
    List<PlanFeature> findByFeatureCode(@Param("featureCode") String featureCode);

    /**
     * Check if a plan has a specific feature enabled
     */
    @Query("SELECT CASE WHEN COUNT(pf) > 0 THEN true ELSE false END FROM PlanFeature pf JOIN pf.feature f " +
           "WHERE pf.plan.id = :planId AND f.code = :featureCode AND pf.enabled = true")
    boolean existsByPlanIdAndFeatureCodeAndEnabledTrue(@Param("planId") UUID planId, @Param("featureCode") String featureCode);
}
