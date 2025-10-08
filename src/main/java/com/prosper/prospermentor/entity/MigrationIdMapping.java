package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MigrationIdMapping entity for tracking MongoDB ObjectIDs to Supabase UUIDs
 * This table will be created during migration to maintain reference integrity
 */
@Entity
@Table(name = "migration_id_mapping", 
       indexes = {
           @Index(name = "idx_migration_old_id", columnList = "oldMongodbId"),
           @Index(name = "idx_migration_entity_type", columnList = "entityType"),
           @Index(name = "idx_migration_old_id_entity", columnList = "oldMongodbId, entityType")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MigrationIdMapping {

    /**
     * Auto-generated primary key
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Original MongoDB ObjectID (stored as string)
     */
    @NotBlank
    @Column(name = "old_mongodb_id", nullable = false, length = 50)
    private String oldMongodbId;

    /**
     * New Supabase UUID/ID (stored as string to handle both UUID and Long)
     */
    @NotBlank
    @Column(name = "new_supabase_id", nullable = false, length = 50)
    private String newSupabaseId;

    /**
     * Entity type (users, topics, sessions, etc.)
     */
    @NotBlank
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * When this mapping was created during migration
     */
    @Column(name = "migration_timestamp", nullable = false)
    private LocalDateTime migrationTimestamp;

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "version")
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (migrationTimestamp == null) {
            migrationTimestamp = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating new mappings
     */
    public MigrationIdMapping(String oldMongodbId, String newSupabaseId, String entityType) {
        this.oldMongodbId = oldMongodbId;
        this.newSupabaseId = newSupabaseId;
        this.entityType = entityType;
        this.migrationTimestamp = LocalDateTime.now();
    }

    /**
     * Entity types enumeration for reference
     */
    public static class EntityType {
        public static final String USERS = "users";
        public static final String TOPICS = "topics";
        public static final String SESSIONS = "sessions";
        public static final String PROFILES = "profiles";
        public static final String MENTEE_PROFILES = "mentee_profiles";
        public static final String MENTOR_PROFILES = "mentor_profiles";
        public static final String SKILLS = "skills";
    }
}
