# Community Data Model Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-owned community persistence, mutation APIs, block/report/preference behavior, and realtime-ready outbox events while preserving existing Phase 1 community reads.

**Architecture:** Keep Phase 1 reads in `CommunityReadService`, add one additive Flyway migration for `community_*` tables, and implement Phase 2 writes through JDBC-backed community services under `com.prosper.prospermentor.service.community`. Wire all endpoints through the existing `/api/v1/community` controller and keep websocket delivery out of scope by writing durable `community_events_outbox` rows.

**Tech Stack:** Java 17, Spring Boot 3.5, PostgreSQL, Flyway, `NamedParameterJdbcTemplate`, Jackson, JUnit 5, Mockito, AssertJ

---

## File Structure

- Create: `src/main/resources/db/migration/V78__Create_community_foundation_tables.sql`
  - Defines `community_categories`, `community_posts`, `community_post_reactions`, `community_comments`, `community_saved_posts`, `community_blocks`, `community_reports`, `community_mentions`, `community_notification_preferences`, and `community_events_outbox`.
- Modify: `src/main/java/com/prosper/prospermentor/dto/community/CommunityDtos.java`
  - Adds request/response records for categories, posts, reactions, comments, saves, blocks, reports, and preferences.
- Create: `src/main/java/com/prosper/prospermentor/service/community/CommunityEventOutboxService.java`
  - Inserts durable outbox events inside caller transactions.
- Create: `src/main/java/com/prosper/prospermentor/service/community/CommunityMutationService.java`
  - Owns validation, idempotency, ownership checks, counter updates, block checks, reports, notification preferences, and outbox writes.
- Modify: `src/main/java/com/prosper/prospermentor/service/community/CommunityReadService.java`
  - Adds community block filtering to existing Phase 1 feed/recommendation/network reads.
- Modify: `src/main/java/com/prosper/prospermentor/controller/CommunityController.java`
  - Adds authenticated Phase 2 endpoints under the existing `/api/v1/community` route prefix.
- Create: `src/test/java/com/prosper/prospermentor/db/CommunityMigrationSourceTest.java`
  - Verifies the migration contains all required tables, constraints, indexes, and seed categories.
- Create: `src/test/java/com/prosper/prospermentor/service/community/CommunityMutationServiceTest.java`
  - Verifies service validation, idempotency, outbox writes, defaults, and ownership/block helpers.
- Modify: `src/test/java/com/prosper/prospermentor/service/community/CommunityReadServiceTest.java`
  - Verifies existing reads now include block filtering.
- Create: `src/test/java/com/prosper/prospermentor/controller/CommunityControllerMutationTest.java`
  - Verifies controller authentication, success responses, and bad-request mapping for Phase 2 routes.

---

### Task 1: Add Migration Source Test And Schema Foundation

**Files:**
- Create: `src/test/java/com/prosper/prospermentor/db/CommunityMigrationSourceTest.java`
- Create: `src/main/resources/db/migration/V78__Create_community_foundation_tables.sql`

- [ ] **Step 1: Write the failing migration source test**

Create `src/test/java/com/prosper/prospermentor/db/CommunityMigrationSourceTest.java` with assertions that read `src/main/resources/db/migration/V78__Create_community_foundation_tables.sql` and require these strings:

```java
package com.prosper.prospermentor.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityMigrationSourceTest {
    @Test
    void communityFoundationMigrationCreatesRequiredTablesAndSeedsCategories() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V78__Create_community_foundation_tables.sql"));

        assertThat(sql)
                .contains("CREATE TABLE community_categories")
                .contains("CREATE TABLE community_posts")
                .contains("CREATE TABLE community_post_reactions")
                .contains("CREATE TABLE community_comments")
                .contains("CREATE TABLE community_saved_posts")
                .contains("CREATE TABLE community_blocks")
                .contains("CREATE TABLE community_reports")
                .contains("CREATE TABLE community_mentions")
                .contains("CREATE TABLE community_notification_preferences")
                .contains("CREATE TABLE community_events_outbox")
                .contains("uniq_community_blocks_pair")
                .contains("uniq_community_open_report")
                .contains("idx_community_events_outbox_pending")
                .contains("'career-growth'")
                .contains("'mentorship'")
                .contains("'questions'");
    }
}
```

- [ ] **Step 2: Run the migration source test to verify it fails**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.db.CommunityMigrationSourceTest
```

Expected: FAIL because `V78__Create_community_foundation_tables.sql` does not exist yet.

- [ ] **Step 3: Add the migration**

Create `src/main/resources/db/migration/V78__Create_community_foundation_tables.sql` with the additive community tables, check constraints, indexes, and category seed data from the approved design.

- [ ] **Step 4: Run the migration source test to verify it passes**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.db.CommunityMigrationSourceTest
```

Expected: PASS.

---

### Task 2: Add DTOs, Outbox Service, And Mutation Service Tests

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/dto/community/CommunityDtos.java`
- Create: `src/main/java/com/prosper/prospermentor/service/community/CommunityEventOutboxService.java`
- Create: `src/main/java/com/prosper/prospermentor/service/community/CommunityMutationService.java`
- Create: `src/test/java/com/prosper/prospermentor/service/community/CommunityMutationServiceTest.java`

- [ ] **Step 1: Write failing mutation service tests**

Create tests covering these concrete behaviors:
- `normalizeReactionTypeDefaultsToLike`
- `createPostRejectsBlankContent`
- `createPostStoresPostAndRecordsOutboxEvent`
- `reactToPostIsIdempotentAndOnlyRecordsOutboxWhenInserted`
- `savePostIsIdempotentAndReturnsLatestCounter`
- `blockUserRejectsSelfBlock`
- `getNotificationPreferencesReturnsDefaultsWhenNoRowExists`
- `updateNotificationPreferencesUpsertsAndRecordsOutbox`

Use Mockito for `NamedParameterJdbcTemplate` and `CommunityEventOutboxService`, matching the existing `CommunityReadServiceTest` pattern.

- [ ] **Step 2: Run the mutation service test to verify it fails**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.service.community.CommunityMutationServiceTest
```

Expected: FAIL with missing DTOs and missing `CommunityMutationService`.

- [ ] **Step 3: Add DTO records and outbox service**

Add records to `CommunityDtos.java` for the Phase 2 request/response contracts. Add `CommunityEventOutboxService.recordEvent(...)` that inserts into `community_events_outbox` using `CAST(:payloadJson AS jsonb)`.

- [ ] **Step 4: Add mutation service implementation**

Implement `CommunityMutationService` with validation, idempotent writes, ownership checks, counter updates, report/block/preference behavior, and event writes.

- [ ] **Step 5: Run mutation service tests to verify they pass**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.service.community.CommunityMutationServiceTest
```

Expected: PASS.

---

### Task 3: Add Block Filtering To Phase 1 Reads

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/service/community/CommunityReadService.java`
- Modify: `src/test/java/com/prosper/prospermentor/service/community/CommunityReadServiceTest.java`

- [ ] **Step 1: Add failing read-service block filter tests**

Add tests that inspect generated SQL and assert feed and recommendation queries contain `community_blocks` and both profile directions:

```java
assertThat(sqlCaptor.getValue())
        .contains("community_blocks")
        .contains("blocker_profile_id = :viewerId")
        .contains("blocked_profile_id = p.user_id");
```

For recommendations, assert the candidate SQL contains `blocked_profile_id = p.id`.

- [ ] **Step 2: Run the focused read-service tests to verify failure**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.service.community.CommunityReadServiceTest
```

Expected: FAIL because the current SQL does not filter `community_blocks`.

- [ ] **Step 3: Add block filtering SQL**

Update feed, recommendation, and network queries so blocked relationships are excluded in both directions. Keep the legacy `posts`, `follows`, and `syncs` reads intact.

- [ ] **Step 4: Run the focused read-service tests to verify success**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.service.community.CommunityReadServiceTest
```

Expected: PASS.

---

### Task 4: Wire Controller Endpoints

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/controller/CommunityController.java`
- Create: `src/test/java/com/prosper/prospermentor/controller/CommunityControllerMutationTest.java`

- [ ] **Step 1: Write failing controller tests**

Create direct controller tests that instantiate `CommunityController` with mocked read and mutation services and call:
- `createPost` with `SupabaseUserPrincipal`
- `reactToPost` with `SupabaseUserPrincipal`
- `getNotificationPreferences` with `SupabaseUserPrincipal`
- `createPost` with blank content to verify HTTP 400
- `createPost` with null authentication to verify HTTP 401

- [ ] **Step 2: Run controller tests to verify failure**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.controller.CommunityControllerMutationTest
```

Expected: FAIL because controller methods and mutation service constructor wiring do not exist yet.

- [ ] **Step 3: Add controller endpoints**

Inject `CommunityMutationService` into `CommunityController` and add the Phase 2 routes:
- `GET /categories`
- `POST /posts`
- `PUT /posts/{postId}`
- `DELETE /posts/{postId}`
- `POST /posts/{postId}/reactions`
- `DELETE /posts/{postId}/reactions/{reactionType}`
- `GET /posts/{postId}/comments`
- `POST /posts/{postId}/comments`
- `DELETE /comments/{commentId}`
- `POST /posts/{postId}/save`
- `DELETE /posts/{postId}/save`
- `POST /blocks`
- `DELETE /blocks/{blockedProfileId}`
- `POST /reports`
- `GET /preferences/notifications`
- `PUT /preferences/notifications`

Map `IllegalArgumentException` to HTTP 400, `SecurityException` to HTTP 403, `NoSuchElementException` to HTTP 404, and missing authentication to HTTP 401.

- [ ] **Step 4: Run controller tests to verify success**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.controller.CommunityControllerMutationTest
```

Expected: PASS.

---

### Task 5: Full Verification And Commit

**Files:**
- All files changed by Tasks 1-4

- [ ] **Step 1: Run all focused community tests**

Run:

```bash
./gradlew test --tests com.prosper.prospermentor.db.CommunityMigrationSourceTest --tests com.prosper.prospermentor.service.community.CommunityReadServiceTest --tests com.prosper.prospermentor.service.community.CommunityMutationServiceTest --tests com.prosper.prospermentor.controller.CommunityControllerMutationTest
```

Expected: PASS.

- [ ] **Step 2: Run the full backend test suite**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Review the diff**

Run:

```bash
git diff --check
git status -sb
```

Expected: no whitespace errors and only Phase 2 backend community files changed.

- [ ] **Step 4: Commit the implementation**

Run:

```bash
git add docs/superpowers/plans/2026-08-13-community-data-model-phase-2-implementation-plan.md src/main/resources/db/migration/V78__Create_community_foundation_tables.sql src/main/java/com/prosper/prospermentor/dto/community/CommunityDtos.java src/main/java/com/prosper/prospermentor/service/community/CommunityEventOutboxService.java src/main/java/com/prosper/prospermentor/service/community/CommunityMutationService.java src/main/java/com/prosper/prospermentor/service/community/CommunityReadService.java src/main/java/com/prosper/prospermentor/controller/CommunityController.java src/test/java/com/prosper/prospermentor/db/CommunityMigrationSourceTest.java src/test/java/com/prosper/prospermentor/service/community/CommunityMutationServiceTest.java src/test/java/com/prosper/prospermentor/service/community/CommunityReadServiceTest.java src/test/java/com/prosper/prospermentor/controller/CommunityControllerMutationTest.java
git commit -m "feat: add community data model phase 2 foundation"
```

Expected: commit created on `codex/community-data-model-phase-2`.
