# Community Discovery API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build backend read endpoints for community search and people discovery.

**Architecture:** Extend the existing `CommunityController` and `CommunityReadService` rather than creating a separate service. Reuse the deployed legacy post/feed query contract and centralize new DTOs in `CommunityDtos`.

**Tech Stack:** Java 17, Spring Boot, `NamedParameterJdbcTemplate`, JUnit 5, Mockito, AssertJ.

---

### Task 1: Search DTOs And Controller Contract

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/dto/community/CommunityDtos.java`
- Modify: `src/main/java/com/prosper/prospermentor/controller/CommunityController.java`
- Test: `src/test/java/com/prosper/prospermentor/controller/CommunityControllerTest.java`

- [ ] Add `CommunityHashtagItem`, `CommunitySearchPersonItem`, and `CommunitySearchResponse` records.
- [ ] Add controller tests for unauthenticated search rejection and authenticated search delegation.
- [ ] Add `GET /api/v1/community/search` and delegate to `communityReadService.search(...)`.

### Task 2: Search Service

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/service/community/CommunityReadService.java`
- Test: `src/test/java/com/prosper/prospermentor/service/community/CommunityReadServiceTest.java`

- [ ] Add failing tests for blank query rejection, type normalization, post/people block filters, and hashtag extraction.
- [ ] Implement `search(viewerId, query, type, limit)` with `all`, `posts`, `people`, `categories`, and `hashtags` scopes.
- [ ] Keep SQL read-only and scoped by the same block rules as feed/recommendations.

### Task 3: People Discovery

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/dto/community/CommunityDtos.java`
- Modify: `src/main/java/com/prosper/prospermentor/controller/CommunityController.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/community/CommunityReadService.java`
- Test: `src/test/java/com/prosper/prospermentor/controller/CommunityControllerTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/community/CommunityReadServiceTest.java`

- [ ] Add `CommunityPeopleDiscoveryResponse`.
- [ ] Add controller delegation test for `GET /api/v1/community/discovery/people`.
- [ ] Implement `getPeopleDiscovery(viewerId, limit)` with suggested people and recent accepted connections.

### Task 4: Verification And PR

**Files:**
- All modified files from Tasks 1-3.

- [ ] Run `./gradlew test --tests 'com.prosper.prospermentor.controller.CommunityControllerTest' --tests 'com.prosper.prospermentor.service.community.CommunityReadServiceTest'`.
- [ ] Run `git diff --check`.
- [ ] Commit with `feat: add community discovery api foundation`.
- [ ] Push branch `codex/community-discovery-api-foundation`.
- [ ] Open a draft PR against `main`.
