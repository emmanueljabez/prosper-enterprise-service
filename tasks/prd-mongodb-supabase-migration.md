# Product Requirements Document: MongoDB to Supabase Migration

## Introduction/Overview

The ProsperMentor application currently stores data in MongoDB collections (users.json, topics.json, sessions.json) and needs to migrate this data to Supabase (PostgreSQL) to leverage SQL capabilities, improve data relationships, and align with the existing Supabase authentication system. This feature will provide a one-time migration tool that safely transfers all MongoDB data to the appropriate Supabase tables while maintaining data integrity and establishing proper foreign key relationships.

## Goals

1. **Complete Data Migration**: Successfully migrate all user profiles (mentees and mentors), topics, and sessions from MongoDB collections to Supabase tables
2. **Maintain Data Integrity**: Ensure no data loss during migration with full validation checks
3. **Establish Proper Relationships**: Create correct foreign key relationships between migrated entities (users → mentor_skills → topics)
4. **ID Mapping Strategy**: Implement a robust system to track MongoDB ObjectIDs to Supabase UUIDs/IDs for reference integrity
5. **Rollback Capability**: Provide ability to rollback migration in case of issues
6. **Comprehensive Logging**: Generate detailed logs for monitoring, debugging, and manual review of any issues

## User Stories

**As a System Administrator**, I want to migrate all MongoDB data to Supabase so that the application can leverage SQL capabilities and maintain consistency with the existing authentication system.

**As a System Administrator**, I want to track the migration progress through API endpoints so that I can monitor the process and identify any issues in real-time.

**As a System Administrator**, I want detailed validation reports after migration so that I can verify data integrity and completeness.

**As a System Administrator**, I want rollback capability so that I can revert changes if the migration encounters critical issues.

**As a Developer**, I want a mapping table of old MongoDB IDs to new Supabase IDs so that I can maintain data relationships and handle any future data references.

## Functional Requirements

### Core Migration Features
1. **The system must migrate user data** from users.json to appropriate Supabase tables:
   - Users with `userType: "ADVISEE"` → `mentee_profiles` table
   - Users with `userType: "ADVISOR"` → `mentor_profiles` table
   - Basic user information → `users` table (if separate from profiles)

2. **The system must migrate topics data** from topics.json to `skills` table in Supabase

3. **The system must migrate sessions data** from sessions.json to `sessions` table in Supabase

4. **The system must create mentor-skill relationships** by mapping advisor topics from `advisorDetails.topics` to the `mentor_skills` junction table

5. **The system must implement ID mapping strategy** by creating and maintaining a `migration_id_mapping` table with:
   - `old_mongodb_id` (string)
   - `new_supabase_id` (UUID/integer)
   - `entity_type` (users, topics, sessions)
   - `migration_timestamp`

### Data Processing & Validation
6. **The system must transform data automatically** where possible (e.g., date formats, field mappings)

7. **The system must provide detailed logs** for manual review including:
   - Records processed successfully
   - Records skipped with reasons
   - Data transformation applied
   - Validation errors encountered

8. **The system must perform full data integrity checks** after migration:
   - Row count verification
   - Foreign key relationship validation
   - Data type consistency checks
   - Required field validation

### API & Monitoring
9. **The system must provide migration API endpoints**:
   - `POST /api/admin/migration/start` - Initiate migration
   - `GET /api/admin/migration/status` - Check migration progress
   - `GET /api/admin/migration/logs` - Retrieve migration logs
   - `POST /api/admin/migration/rollback` - Rollback migration

10. **The system must track migration progress** with status updates:
    - PENDING, IN_PROGRESS, COMPLETED, FAILED, ROLLED_BACK

### Error Handling & Recovery
11. **The system must handle migration errors gracefully** by:
    - Continuing migration when possible (skip problematic records)
    - Logging all errors with context
    - Providing detailed error reports

12. **The system must support full rollback capability** by:
    - Maintaining backup of original Supabase state
    - Reversing all changes made during migration
    - Restoring previous data state

## Non-Goals (Out of Scope)

1. **Real-time synchronization** - This is a one-time migration, not ongoing sync
2. **MongoDB data cleanup** - Original MongoDB collections will remain unchanged
3. **Data archival** - No automatic archival of old MongoDB data
4. **Performance optimization** - No specific performance requirements for migration speed
5. **Custom data transformations** - Only standard field mapping, no complex business logic transformations
6. **Multi-tenant migration** - Assumes single tenant data migration
7. **Incremental migration** - Full migration only, no partial or incremental options

## Technical Considerations

### Database Schema Requirements
- Ensure Supabase schema includes all required tables: `users`, `mentee_profiles`, `mentor_profiles`, `skills`, `mentor_skills`, `sessions`, `migration_id_mapping`
- Verify foreign key constraints are properly defined
- Ensure UUID generation is configured for primary keys

### Dependencies
- Leverage existing `DatabaseInfoService` for Supabase schema introspection
- Integrate with existing Supabase configuration (`SupabaseConfig`)
- Use existing Jackson `ObjectMapper` for JSON processing
- Utilize Spring Boot's `JdbcTemplate` for database operations

### Data Mapping Strategy
```
MongoDB → Supabase Mapping:
- users.json (userType: "ADVISEE") → mentee_profiles
- users.json (userType: "ADVISOR") → mentor_profiles  
- users.json (advisorDetails.topics[]) → mentor_skills (via topics mapping)
- topics.json → skills
- sessions.json → sessions
```

### Transaction Management
- Use database transactions to ensure atomicity
- Implement checkpoint system for rollback capability
- Maintain transaction logs for audit trail

## Success Metrics

1. **100% Data Migration Accuracy** - All records from MongoDB collections successfully migrated to Supabase with data integrity maintained
2. **Zero Data Loss** - Complete validation confirms all original data is preserved in Supabase
3. **Successful Relationship Mapping** - All foreign key relationships properly established (mentors linked to skills, etc.)
4. **Complete ID Mapping** - Full mapping table created allowing reference to original MongoDB IDs
5. **Successful Rollback Testing** - Rollback functionality verified and working
6. **Comprehensive Logging** - Detailed logs available for all migration activities and any issues encountered

## Open Questions

1. **Should we preserve MongoDB ObjectID format** in the mapping table or convert to string representation?
2. **What should be the timeout duration** for the migration API endpoints?
3. **Should migration logs be persisted in database** or file system?
4. **Do we need to handle duplicate data** if migration is accidentally run multiple times?
5. **Should we validate Supabase schema compatibility** before starting migration?
6. **What level of concurrent access** should be supported during migration (read-only mode)?

## Implementation Priority

### Phase 1 (Core Migration)
- Implement basic migration service
- Create ID mapping functionality
- Migrate users data (mentees and mentors)
- Basic validation and logging

### Phase 2 (Relationships & Advanced Features)
- Migrate topics to skills
- Create mentor-skills relationships
- Implement full data integrity checks
- API endpoints for monitoring

### Phase 3 (Recovery & Production Readiness)
- Implement rollback capability
- Comprehensive error handling
- Production-level logging and monitoring
- Final testing and validation


