package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.MigrationIdMapping;
import com.prosper.prospermentor.repository.MigrationIdMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing MongoDB ObjectID to Supabase UUID mappings during migration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdMappingService {

    private final MigrationIdMappingRepository mappingRepository;

    /**
     * Create a new ID mapping with better error handling
     */
    public MigrationIdMapping createMapping(String mongodbId, String supabaseId, String entityType) {
        log.debug("Creating mapping: {} -> {} ({})", mongodbId, supabaseId, entityType);

        try {
            // Check if mapping already exists
            Optional<MigrationIdMapping> existing = mappingRepository
                    .findByOldMongodbIdAndEntityType(mongodbId, entityType);
            
            if (existing.isPresent()) {
                log.debug("Mapping already exists for MongoDB ID {} and entity type {}: {}", 
                        mongodbId, entityType, existing.get().getNewSupabaseId());
                return existing.get();
            }

            MigrationIdMapping mapping = new MigrationIdMapping(mongodbId, supabaseId, entityType);
            MigrationIdMapping saved = mappingRepository.save(mapping);
            
            log.debug("Successfully created mapping with ID: {}", saved.getId());
            return saved;
            
        } catch (Exception e) {
            // Handle constraint violations and other database errors gracefully
            log.warn("Failed to create mapping {} -> {} ({}): {}. Checking if it already exists...", 
                    mongodbId, supabaseId, entityType, e.getMessage());
            
            try {
                // Try to find existing mapping again in case of race condition
                Optional<MigrationIdMapping> existing = mappingRepository
                        .findByOldMongodbIdAndEntityType(mongodbId, entityType);
                
                if (existing.isPresent()) {
                    log.debug("Found existing mapping after error: {} -> {}", mongodbId, existing.get().getNewSupabaseId());
                    return existing.get();
                }
            } catch (Exception findError) {
                log.error("Could not check for existing mapping: {}", findError.getMessage());
            }
            
            // If we can't create or find the mapping, throw the original exception
            throw new RuntimeException("Could not create ID mapping for " + mongodbId + " -> " + supabaseId + " (" + entityType + ")", e);
        }
    }

    /**
     * Create mapping with auto-generated UUID
     */
    public MigrationIdMapping createMappingWithUuid(String mongodbId, String entityType) {
        String supabaseId = UUID.randomUUID().toString();
        return createMapping(mongodbId, supabaseId, entityType);
    }

    /**
     * Retrieve Supabase ID by MongoDB ObjectID
     */
    public Optional<String> getSupabaseId(String mongodbId, String entityType) {
        log.debug("Looking up Supabase ID for MongoDB ID: {} ({})", mongodbId, entityType);
        return mappingRepository.findSupabaseIdByMongoId(mongodbId, entityType);
    }

    /**
     * Retrieve MongoDB ObjectID by Supabase ID
     */
    public Optional<String> getMongodbId(String supabaseId, String entityType) {
        log.debug("Looking up MongoDB ID for Supabase ID: {} ({})", supabaseId, entityType);
        return mappingRepository.findMongoIdBySupabaseId(supabaseId, entityType);
    }

    /**
     * Get all mappings for specific entity type
     */
    public List<MigrationIdMapping> getMappingsByEntityType(String entityType) {
        log.debug("Retrieving all mappings for entity type: {}", entityType);
        return mappingRepository.findByEntityType(entityType);
    }

    /**
     * Check if mapping exists
     */
    public boolean mappingExists(String mongodbId, String entityType) {
        return mappingRepository.existsByOldMongodbIdAndEntityType(mongodbId, entityType);
    }

    /**
     * Batch create mappings
     */
    public List<MigrationIdMapping> createBatchMappings(Map<String, String> mongoToSupabaseMap, String entityType) {
        log.info("Creating batch mappings for entity type: {} (count: {})", entityType, mongoToSupabaseMap.size());

        List<MigrationIdMapping> mappings = mongoToSupabaseMap.entrySet().stream()
                .map(entry -> new MigrationIdMapping(entry.getKey(), entry.getValue(), entityType))
                .toList();

        List<MigrationIdMapping> saved = mappingRepository.saveAll(mappings);
        log.info("Successfully created {} batch mappings for entity type: {}", saved.size(), entityType);
        
        return saved;
    }

    /**
     * Batch lookup Supabase IDs
     */
    public Map<String, String> batchGetSupabaseIds(List<String> mongodbIds, String entityType) {
        log.debug("Batch lookup of Supabase IDs for {} MongoDB IDs ({})", mongodbIds.size(), entityType);

        List<Object[]> results = mappingRepository.findSupabaseIdsByMongoIds(mongodbIds, entityType);
        
        Map<String, String> mappings = new HashMap<>();
        for (Object[] result : results) {
            String mongoId = (String) result[0];
            String supabaseId = (String) result[1];
            mappings.put(mongoId, supabaseId);
        }

        log.debug("Found {} mappings from batch lookup", mappings.size());
        return mappings;
    }

    /**
     * Clear all mappings for specific entity type (for rollback)
     */
    public void clearMappingsByEntityType(String entityType) {
        log.warn("Clearing all mappings for entity type: {}", entityType);
        long count = mappingRepository.countByEntityType(entityType);
        mappingRepository.deleteByEntityType(entityType);
        log.warn("Cleared {} mappings for entity type: {}", count, entityType);
    }

    /**
     * Clear all mappings (for full rollback)
     */
    public void clearAllMappings() {
        log.warn("Clearing ALL migration mappings");
        long count = mappingRepository.count();
        mappingRepository.deleteAllMappings();
        log.warn("Cleared {} total mappings", count);
    }

    /**
     * Get migration statistics
     */
    public Map<String, Long> getMigrationStatistics() {
        List<Object[]> stats = mappingRepository.getMigrationStatistics();
        Map<String, Long> statistics = new HashMap<>();
        
        for (Object[] stat : stats) {
            String entityType = (String) stat[0];
            Long count = (Long) stat[1];
            statistics.put(entityType, count);
        }

        log.debug("Migration statistics: {}", statistics);
        return statistics;
    }

    /**
     * Get total mapping count
     */
    public long getTotalMappingCount() {
        return mappingRepository.count();
    }

    /**
     * Validate mapping integrity
     */
    public Map<String, Object> validateMappingIntegrity() {
        log.info("Validating mapping integrity");

        List<String> duplicates = mappingRepository.findDuplicateMappings();
        long totalMappings = mappingRepository.count();
        List<String> entityTypes = mappingRepository.findDistinctEntityTypes();

        Map<String, Object> validation = new HashMap<>();
        validation.put("total_mappings", totalMappings);
        validation.put("entity_types", entityTypes);
        validation.put("duplicate_count", duplicates.size());
        validation.put("has_duplicates", !duplicates.isEmpty());
        
        if (!duplicates.isEmpty()) {
            log.warn("Found {} duplicate mappings: {}", duplicates.size(), duplicates);
            validation.put("duplicate_mongodb_ids", duplicates);
        }

        log.info("Mapping integrity validation completed: {}", validation);
        return validation;
    }

    /**
     * Find mappings created after specific timestamp
     */
    public List<MigrationIdMapping> findMappingsAfter(LocalDateTime timestamp) {
        return mappingRepository.findMappingsAfterTimestamp(timestamp);
    }

    /**
     * Get mapping by MongoDB ID and entity type
     */
    public Optional<MigrationIdMapping> getMapping(String mongodbId, String entityType) {
        return mappingRepository.findByOldMongodbIdAndEntityType(mongodbId, entityType);
    }

    /**
     * Entity type constants for convenience
     */
    public static class EntityType {
        public static final String USERS = MigrationIdMapping.EntityType.USERS;
        public static final String TOPICS = MigrationIdMapping.EntityType.TOPICS;
        public static final String SESSIONS = MigrationIdMapping.EntityType.SESSIONS;
        public static final String PROFILES = MigrationIdMapping.EntityType.PROFILES;
        public static final String MENTEE_PROFILES = MigrationIdMapping.EntityType.MENTEE_PROFILES;
        public static final String MENTOR_PROFILES = MigrationIdMapping.EntityType.MENTOR_PROFILES;
        public static final String SKILLS = MigrationIdMapping.EntityType.SKILLS;
    }
}
