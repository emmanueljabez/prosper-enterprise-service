package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.MigrationIdMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MigrationIdMapping entity
 * Used to track MongoDB ObjectIDs to Supabase UUIDs during migration
 */
@Repository
public interface MigrationIdMappingRepository extends JpaRepository<MigrationIdMapping, Long> {

    /**
     * Find mapping by MongoDB ObjectID and entity type
     */
    Optional<MigrationIdMapping> findByOldMongodbIdAndEntityType(String oldMongodbId, String entityType);

    /**
     * Find mapping by Supabase ID and entity type
     */
    Optional<MigrationIdMapping> findByNewSupabaseIdAndEntityType(String newSupabaseId, String entityType);

    /**
     * Find all mappings by entity type
     */
    List<MigrationIdMapping> findByEntityType(String entityType);

    /**
     * Find all mappings by entity type ordered by creation time
     */
    @Query("SELECT m FROM MigrationIdMapping m WHERE m.entityType = :entityType ORDER BY m.migrationTimestamp ASC")
    List<MigrationIdMapping> findByEntityTypeOrderedByTimestamp(@Param("entityType") String entityType);

    /**
     * Check if mapping exists for MongoDB ObjectID and entity type
     */
    boolean existsByOldMongodbIdAndEntityType(String oldMongodbId, String entityType);

    /**
     * Check if mapping exists for Supabase ID and entity type
     */
    boolean existsByNewSupabaseIdAndEntityType(String newSupabaseId, String entityType);

    /**
     * Get Supabase ID by MongoDB ObjectID and entity type
     */
    @Query("SELECT m.newSupabaseId FROM MigrationIdMapping m WHERE m.oldMongodbId = :oldMongodbId AND m.entityType = :entityType")
    Optional<String> findSupabaseIdByMongoId(@Param("oldMongodbId") String oldMongodbId, @Param("entityType") String entityType);

    /**
     * Get MongoDB ObjectID by Supabase ID and entity type
     */
    @Query("SELECT m.oldMongodbId FROM MigrationIdMapping m WHERE m.newSupabaseId = :newSupabaseId AND m.entityType = :entityType")
    Optional<String> findMongoIdBySupabaseId(@Param("newSupabaseId") String newSupabaseId, @Param("entityType") String entityType);

    /**
     * Delete all mappings for specific entity type
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MigrationIdMapping m WHERE m.entityType = :entityType")
    void deleteByEntityType(@Param("entityType") String entityType);

    /**
     * Delete all mappings (for rollback)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MigrationIdMapping")
    void deleteAllMappings();

    /**
     * Count mappings by entity type
     */
    long countByEntityType(String entityType);

    /**
     * Get all entity types that have mappings
     */
    @Query("SELECT DISTINCT m.entityType FROM MigrationIdMapping m")
    List<String> findDistinctEntityTypes();

    /**
     * Find mappings created after specific timestamp
     */
    @Query("SELECT m FROM MigrationIdMapping m WHERE m.migrationTimestamp > :timestamp")
    List<MigrationIdMapping> findMappingsAfterTimestamp(@Param("timestamp") java.time.LocalDateTime timestamp);

    /**
     * Get migration statistics by entity type
     */
    @Query("SELECT m.entityType, COUNT(m) as count FROM MigrationIdMapping m GROUP BY m.entityType")
    List<Object[]> getMigrationStatistics();

    /**
     * Find duplicate mappings (same MongoDB ID mapped to different Supabase IDs)
     */
    @Query("SELECT m.oldMongodbId FROM MigrationIdMapping m GROUP BY m.oldMongodbId, m.entityType HAVING COUNT(m) > 1")
    List<String> findDuplicateMappings();

    /**
     * Batch find Supabase IDs by MongoDB ObjectIDs
     */
    @Query("SELECT m.oldMongodbId, m.newSupabaseId FROM MigrationIdMapping m WHERE m.oldMongodbId IN :mongoIds AND m.entityType = :entityType")
    List<Object[]> findSupabaseIdsByMongoIds(@Param("mongoIds") List<String> mongoIds, @Param("entityType") String entityType);
}


