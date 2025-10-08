package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.service.IdMappingService;
import com.prosper.prospermentor.service.MigrationService;
import com.prosper.prospermentor.service.MongoDataReaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for managing MongoDB to Supabase migration
 */
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
@Slf4j
public class MigrationController {

    private final MigrationService migrationService;
    private final MongoDataReaderService mongoDataReader;
    private final IdMappingService idMappingService;

    /**
     * Start the migration process
     */
    @PostMapping("/start")
    public ResponseEntity<Object> startMigration() {
        log.info("Migration start requested");
        
        try {
            // Check if migration is already in progress
            MigrationService.MigrationStatusInfo currentStatus = migrationService.getStatus();
            if (currentStatus.status() == MigrationService.MigrationStatus.IN_PROGRESS) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Migration is already in progress",
                        "status", currentStatus
                ));
            }

            // Start migration asynchronously
            new Thread(() -> {
                try {
                    migrationService.executeMigration();
                } catch (Exception e) {
                    log.error("Migration failed with unexpected error", e);
                }
            }).start();

            return ResponseEntity.ok(Map.of(
                    "message", "Migration started successfully",
                    "status", "IN_PROGRESS"
            ));

        } catch (Exception e) {
            log.error("Failed to start migration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to start migration: " + e.getMessage()
            ));
        }
    }

    /**
     * Get current migration status and progress
     */
    @GetMapping("/status")
    public ResponseEntity<MigrationService.MigrationStatusInfo> getMigrationStatus() {
        try {
            MigrationService.MigrationStatusInfo status = migrationService.getStatus();
            log.debug("Migration status requested: {}", status.status());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get migration status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get migration logs
     */
    @GetMapping("/logs")
    public ResponseEntity<Object> getMigrationLogs() {
        try {
            MigrationService.MigrationStatusInfo status = migrationService.getStatus();
            return ResponseEntity.ok(Map.of(
                    "status", status.status(),
                    "logs", status.log(),
                    "progress", status.progressPercentage()
            ));
        } catch (Exception e) {
            log.error("Failed to get migration logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to retrieve migration logs: " + e.getMessage()
            ));
        }
    }

    /**
     * Validate MongoDB collection files
     */
    @GetMapping("/validate")
    public ResponseEntity<Object> validateCollections() {
        try {
            log.info("Collection validation requested");
            
            boolean filesValid = mongoDataReader.validateCollectionFiles();
            Map<String, Object> dataStats = mongoDataReader.getDataStatistics();
            
            return ResponseEntity.ok(Map.of(
                    "files_valid", filesValid,
                    "statistics", dataStats,
                    "message", filesValid ? "All collection files are valid" : "Some collection files are missing"
            ));
            
        } catch (Exception e) {
            log.error("Failed to validate collections: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to validate collections: " + e.getMessage()
            ));
        }
    }

    /**
     * Get data statistics from MongoDB collections
     */
    @GetMapping("/data-stats")
    public ResponseEntity<Object> getDataStatistics() {
        try {
            Map<String, Object> stats = mongoDataReader.getDataStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get data statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get data statistics: " + e.getMessage()
            ));
        }
    }

    /**
     * Get ID mapping statistics
     */
    @GetMapping("/mapping-stats")
    public ResponseEntity<Object> getMappingStatistics() {
        try {
            Map<String, Long> mappingStats = idMappingService.getMigrationStatistics();
            long totalMappings = idMappingService.getTotalMappingCount();
            Map<String, Object> integrity = idMappingService.validateMappingIntegrity();
            
            return ResponseEntity.ok(Map.of(
                    "mapping_counts", mappingStats,
                    "total_mappings", totalMappings,
                    "integrity_check", integrity
            ));
            
        } catch (Exception e) {
            log.error("Failed to get mapping statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get mapping statistics: " + e.getMessage()
            ));
        }
    }

    /**
     * Clear ID mappings (for testing/rollback)
     */
    @DeleteMapping("/mappings")
    public ResponseEntity<Object> clearMappings(@RequestParam(required = false) String entityType) {
        try {
            log.warn("Mapping clear requested for entity type: {}", entityType);
            
            if (entityType != null && !entityType.trim().isEmpty()) {
                long count = idMappingService.getMigrationStatistics().getOrDefault(entityType, 0L);
                idMappingService.clearMappingsByEntityType(entityType);
                
                return ResponseEntity.ok(Map.of(
                        "message", "Cleared mappings for entity type: " + entityType,
                        "cleared_count", count
                ));
            } else {
                long totalCount = idMappingService.getTotalMappingCount();
                idMappingService.clearAllMappings();
                
                return ResponseEntity.ok(Map.of(
                        "message", "Cleared all migration mappings",
                        "cleared_count", totalCount
                ));
            }
            
        } catch (Exception e) {
            log.error("Failed to clear mappings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear mappings: " + e.getMessage()
            ));
        }
    }

    /**
     * Get specific ID mapping
     */
    @GetMapping("/mapping")
    public ResponseEntity<Object> getMapping(
            @RequestParam String mongodbId,
            @RequestParam String entityType) {
        try {
            var mapping = idMappingService.getMapping(mongodbId, entityType);
            
            if (mapping.isPresent()) {
                return ResponseEntity.ok(Map.of(
                        "mapping_found", true,
                        "mongodb_id", mapping.get().getOldMongodbId(),
                        "supabase_id", mapping.get().getNewSupabaseId(),
                        "entity_type", mapping.get().getEntityType(),
                        "migration_timestamp", mapping.get().getMigrationTimestamp()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "mapping_found", false,
                        "message", "No mapping found for MongoDB ID: " + mongodbId
                ));
            }
            
        } catch (Exception e) {
            log.error("Failed to get mapping: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get mapping: " + e.getMessage()
            ));
        }
    }

    /**
     * Health check for migration API
     */
    @GetMapping("/health")
    public ResponseEntity<Object> healthCheck() {
        try {
            boolean collectionsValid = mongoDataReader.validateCollectionFiles();
            MigrationService.MigrationStatusInfo status = migrationService.getStatus();
            
            return ResponseEntity.ok(Map.of(
                    "service", "Migration API",
                    "status", "healthy",
                    "collections_valid", collectionsValid,
                    "current_migration_status", status.status(),
                    "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "service", "Migration API",
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Test endpoint to verify API is working
     */
    @GetMapping("/test")
    public ResponseEntity<Object> testEndpoint() {
        return ResponseEntity.ok(Map.of(
                "message", "Migration API is working",
                "timestamp", java.time.LocalDateTime.now(),
                "available_endpoints", Map.of(
                        "POST", "/api/admin/migration/start",
                        "GET", "/api/admin/migration/status",
                        "GET", "/api/admin/migration/logs",
                        "GET", "/api/admin/migration/validate",
                        "GET", "/api/admin/migration/data-stats",
                        "GET", "/api/admin/migration/mapping-stats",
                        "DELETE", "/api/admin/migration/mappings",
                        "GET", "/api/admin/migration/mapping",
                        "GET", "/api/admin/migration/health"
                )
        ));
    }
}


