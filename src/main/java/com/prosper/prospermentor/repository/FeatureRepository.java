package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Feature entity
 */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {

    /**
     * Find feature by code
     */
    Optional<Feature> findByCode(String code);

    /**
     * Check if feature exists by code
     */
    boolean existsByCode(String code);

    /**
     * Find all active features
     */
    List<Feature> findByIsActiveTrue();

    /**
     * Find features by type
     */
    List<Feature> findByType(Feature.FeatureType type);

    /**
     * Find active features by type
     */
    List<Feature> findByTypeAndIsActiveTrue(Feature.FeatureType type);
}
