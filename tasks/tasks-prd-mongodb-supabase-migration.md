# Task List: MongoDB to Supabase Migration

## Relevant Files

- `src/main/resources/db/migration/V3__Create_migration_tables.sql` - Database migration script for all required tables (mentee_profiles, mentor_profiles, skills, mentor_skills, sessions, migration_id_mapping)
- `src/main/java/com/prosper/prospermentor/service/MongoDataReaderService.java` - Service to read and parse MongoDB JSON files from collections directory
- `src/main/java/com/prosper/prospermentor/service/IdMappingService.java` - Service to manage MongoDB ObjectID to Supabase ID mappings
- `src/main/java/com/prosper/prospermentor/service/MigrationService.java` - Core migration service with transaction management and orchestration
- `src/main/java/com/prosper/prospermentor/service/DataValidationService.java` - Service for data integrity checks and validation
- `src/main/java/com/prosper/prospermentor/service/RollbackService.java` - Service to handle migration rollback and backup operations
- `src/main/java/com/prosper/prospermentor/controller/MigrationController.java` - REST API endpoints for migration management
- `src/main/java/com/prosper/prospermentor/entity/MenteeProfile.java` - JPA entity for mentee profiles
- `src/main/java/com/prosper/prospermentor/entity/MentorProfile.java` - JPA entity for mentor profiles
- `src/main/java/com/prosper/prospermentor/entity/Skill.java` - JPA entity for skills (topics)
- `src/main/java/com/prosper/prospermentor/entity/MentorSkill.java` - JPA entity for mentor-skill relationships
- `src/main/java/com/prosper/prospermentor/entity/Session.java` - JPA entity for sessions
- `src/main/java/com/prosper/prospermentor/entity/MigrationIdMapping.java` - JPA entity for ID mapping tracking
- `src/main/java/com/prosper/prospermentor/repository/MenteeProfileRepository.java` - Repository for mentee profiles
- `src/main/java/com/prosper/prospermentor/repository/MentorProfileRepository.java` - Repository for mentor profiles
- `src/main/java/com/prosper/prospermentor/repository/SkillRepository.java` - Repository for skills
- `src/main/java/com/prosper/prospermentor/repository/MentorSkillRepository.java` - Repository for mentor-skill relationships
- `src/main/java/com/prosper/prospermentor/repository/SessionRepository.java` - Repository for sessions
- `src/main/java/com/prosper/prospermentor/repository/MigrationIdMappingRepository.java` - Repository for ID mapping
- `src/main/java/com/prosper/prospermentor/dto/MigrationStatusDto.java` - DTO for migration status responses
- `src/main/java/com/prosper/prospermentor/dto/MigrationLogDto.java` - DTO for migration log entries
- `src/test/java/com/prosper/prospermentor/service/MigrationServiceTest.java` - Unit tests for migration service
- `src/test/java/com/prosper/prospermentor/service/MongoDataReaderServiceTest.java` - Unit tests for MongoDB data reader
- `src/test/java/com/prosper/prospermentor/controller/MigrationControllerTest.java` - Integration tests for migration controller

### Notes

- Use `./gradlew test` to run all tests
- Use `./gradlew test --tests "com.prosper.prospermentor.service.MigrationServiceTest"` to run specific test class
- Migration should be run in a transaction to ensure atomicity
- All MongoDB JSON files are located in the `collections/` directory

## Tasks

- [x] 1.0 **Analyze Existing Supabase Schema**
  - [x] 1.1 Get schema details for existing mentee_profiles table (UUID id FK to profiles, career_level, industry, goals[], interests[], learning_style, etc.)
  - [x] 1.2 Get schema details for existing mentor_profiles table (UUID id FK to profiles, title, company, years_experience, hourly_rate, specializations[], languages[], bio, avatar_url, etc.)
  - [x] 1.3 Get schema details for existing skills table (UUID id, name text UNIQUE)
  - [x] 1.4 Get schema details for existing mentor_skills junction table (mentor_id FK, skill_id FK, composite PK)
  - [x] 1.5 Get schema details for existing sessions table (UUID id, mentor_id FK, mentee_id FK, title, description, scheduled_start, status, etc.)
  - [x] 1.6 Identify that tables already exist - no new table creation needed
  - [ ] 1.7 Create migration_id_mapping table for tracking MongoDB ObjectIDs to Supabase UUIDs

- [x] 2.0 **MongoDB Data Processing Service**
  - [x] 2.1 Create MongoDataReaderService with method to read users.json and parse into User objects
  - [x] 2.2 Add method to filter users by userType (ADVISEE vs ADVISOR) 
  - [x] 2.3 Add method to read topics.json and parse into Topic objects
  - [x] 2.4 Add method to read sessions.json and parse into Session objects
  - [x] 2.5 Add error handling for malformed JSON and missing files
  - [x] 2.6 Add logging for data reading operations and statistics

- [x] 3.0 **ID Mapping System**
  - [x] 3.1 Create MigrationIdMapping entity with JPA annotations
  - [x] 3.2 Create MigrationIdMappingRepository with custom queries
  - [x] 3.3 Create IdMappingService with method to create new mappings
  - [x] 3.4 Add method to retrieve Supabase ID by MongoDB ObjectID
  - [x] 3.5 Add method to retrieve all mappings by entity type
  - [x] 3.6 Add method to clear mappings for rollback scenarios

- [x] 4.0 **Core Migration Service**
  - [x] 4.1 Create MigrationService with @Transactional annotation for atomicity
  - [x] 4.2 Implement method to migrate ADVISEE users to mentee_profiles
  - [x] 4.3 Implement method to migrate ADVISOR users to mentor_profiles  
  - [x] 4.4 Implement method to migrate topics.json to skills table
  - [x] 4.5 Implement method to create mentor-skill relationships from advisorDetails.topics
  - [x] 4.6 Implement method to migrate sessions.json to sessions table
  - [x] 4.7 Add orchestration method to run full migration in correct dependency order
  - [x] 4.8 Add progress tracking and status updates throughout migration
  - [x] 4.9 Add comprehensive error handling with detailed logging

- [ ] 5.0 **Data Validation Service**
  - [ ] 5.1 Create DataValidationService with method to validate row counts match
  - [ ] 5.2 Add method to validate foreign key relationships are correctly established
  - [ ] 5.3 Add method to validate required fields are not null
  - [ ] 5.4 Add method to validate data types and formats are correct
  - [ ] 5.5 Add method to validate mentor-skill relationships match advisor topics
  - [ ] 5.6 Add comprehensive validation report generation
  - [ ] 5.7 Add method to validate MongoDB ObjectID mappings are complete

- [x] 6.0 **Migration Controller**
  - [x] 6.1 Create MigrationController with @RestController annotation
  - [x] 6.2 Implement POST /api/admin/migration/start endpoint to initiate migration
  - [x] 6.3 Implement GET /api/admin/migration/status endpoint for progress monitoring
  - [x] 6.4 Implement GET /api/admin/migration/logs endpoint to retrieve detailed logs
  - [x] 6.5 Implement DELETE /api/admin/migration/mappings endpoint for clearing mappings (rollback)
  - [x] 6.6 Add proper HTTP status codes and error responses
  - [x] 6.7 Add validation and health check endpoints
  - [x] 6.8 Add concurrency protection to prevent multiple concurrent migrations

- [ ] 7.0 **Rollback System**
  - [ ] 7.1 Create RollbackService with method to backup current Supabase state before migration
  - [ ] 7.2 Implement method to store backup data in temporary tables or JSON format
  - [ ] 7.3 Implement method to restore original state by truncating migrated tables
  - [ ] 7.4 Implement method to restore backup data to original tables
  - [ ] 7.5 Add method to clean up ID mappings during rollback
  - [ ] 7.6 Add transaction management for rollback operations
  - [ ] 7.7 Add validation to ensure rollback completed successfully

- [ ] 8.0 **Testing and Integration**
  - [ ] 8.1 Create MigrationServiceTest with test data setup and teardown
  - [ ] 8.2 Add unit tests for user migration (ADVISEE and ADVISOR scenarios)
  - [ ] 8.3 Add unit tests for topics to skills migration
  - [ ] 8.4 Add unit tests for mentor-skill relationship creation
  - [ ] 8.5 Add unit tests for ID mapping functionality
  - [ ] 8.6 Create integration tests for full migration workflow
  - [ ] 8.7 Add tests for validation service functionality
  - [ ] 8.8 Add tests for rollback functionality
  - [ ] 8.9 Create controller integration tests for all API endpoints
  - [ ] 8.10 Perform end-to-end testing with actual MongoDB JSON files
