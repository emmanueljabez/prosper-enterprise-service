# Community Data Model Phase 2 Design

Date: 2026-08-13
Repo: `/Users/macbookpro/IdeaProjects/ProsperMentor`
Related frontends:
- Enterprise: `/Users/macbookpro/WebstormProjects/myProsperV2`
- B2C network: `/Users/macbookpro/WebstormProjects/prosper-link-africa`

## 1. Summary

Phase 2 introduces backend-owned community persistence and mutation APIs for the B2C community experience while preserving the Phase 1 read contract. The goal is to stop expanding the direct frontend-to-Supabase community model and move posts, reactions, comments, saved posts, blocks, reports, mentions, notification preferences, and realtime-ready events behind the Prosper enterprise backend.

The recommended approach is a hybrid compatibility layer. New community writes go to dedicated `community_*` tables. Existing Phase 1 reads can continue reading the legacy `posts`, `follows`, and `syncs` tables until the B2C UI is moved fully to backend actions and feed reads are cut over or unioned. This keeps production behavior stable while closing the main architectural gap: community business rules must live in the backend, not in the B2C frontend.

## 2. Goals

- Create a backend-owned schema for community posts, categories, reactions, comments, saves, blocks, reports, mentions, notification preferences, and events.
- Add authenticated REST APIs for community create/update/delete/report/block/preference actions under `/api/v1/community`.
- Make write actions idempotent where users expect repeat clicks to be safe, especially reactions, saves, blocks, and reports.
- Record durable outbox events for post, comment, reaction, mention, block, report, and preference changes.
- Keep Phase 1 B2C feed, recommendations, and network reads working during rollout.
- Prepare the data model for later websocket or server-sent event delivery without coupling realtime delivery to the request transaction.
- Keep deployment compatible with the existing backend `deploy.sh` flow and Flyway migration process.

## 3. Non-Goals

- No websocket server or live push delivery in Phase 2.
- No direct-message schema in Phase 2.
- No migration of all legacy B2C posts into the new table during Phase 2.
- No B2C UI action migration in this phase unless needed to verify API contracts manually.
- No enterprise community UI changes.
- No generalized moderation console; moderation data is captured for later admin workflows.

## 4. Current State

Phase 1 added:
- `CommunityController` under `/api/v1/community`
- `CommunityReadService`
- authenticated feed reads
- authenticated people recommendations
- authenticated network overview reads

The current read service still queries legacy B2C tables:
- `posts` for feed content
- `profiles` for author and recommendation metadata
- `follows` for follower/following data
- `syncs` for mentorship connection state

The immediate gap is that community mutations and community-specific policy are still not represented as backend-owned domain behavior. Without Phase 2, the B2C frontend would keep carrying business rules for posting, liking, saving, reporting, blocking, and notification preferences.

## 5. Recommended Approach

Use dedicated backend-owned tables for new community behavior and keep the existing Phase 1 read endpoints stable while the frontend migrates.

Why this approach:
- It avoids risky production migration of legacy community content before the backend APIs are proven.
- It gives the backend clear ownership over moderation, notification, and privacy policy.
- It makes websocket readiness an internal event contract instead of a UI-specific implementation detail.
- It allows B2C UI migration to happen route-by-route in Phase 3 without breaking the existing public network.

Rejected alternatives:
- Extend only the legacy `posts` table: fast, but it keeps the old frontend-owned model and mixes compatibility columns with future moderation and notification concerns.
- Replace all reads immediately with `community_posts`: cleaner long term, but too much behavioral change for a schema/API foundation phase.
- Add websocket delivery now: premature until mutation semantics, block filtering, notification preferences, and outbox events are settled.

## 6. Data Model

Migration numbering should start at `V78` because `V77__Create_company_mentor_enrollment_tables.sql` is currently the latest backend migration.

Use UUID primary keys, `TIMESTAMP` or `TIMESTAMP WITH TIME ZONE` consistently with nearby backend migrations, additive `CREATE TABLE` migrations, and indexes for feed, ownership, status, and moderation lookups. Use `VARCHAR` plus check constraints for status fields unless the repository already has a stronger enum convention for a specific area.

### 6.1 `community_categories`

Purpose: stable backend-controlled grouping for posts and filtering.

Fields:
- `id`
- `slug`
- `name`
- `description`
- `sort_order`
- `is_active`
- `created_at`
- `updated_at`

Rules:
- `slug` is unique.
- Seed initial active categories with stable slugs such as `career-growth`, `leadership`, `industry-insights`, `mentorship`, `wins`, and `questions`.

### 6.2 `community_posts`

Purpose: authoritative post table for new backend-owned community posts.

Fields:
- `id`
- `author_profile_id` references `profiles(id)`
- `category_id` references `community_categories(id)`
- `content`
- `visibility`
- `status`
- `moderation_status`
- `media_url`
- `media_type`
- `image_url`
- `link_url`
- `link_title`
- `link_description`
- `link_image`
- `hashtags`
- `likes_count`
- `comments_count`
- `saves_count`
- `reports_count`
- `pinned_at`
- `pinned_by_profile_id` references `profiles(id)`
- `created_at`
- `updated_at`
- `deleted_at`

Rules:
- `content` is required unless future media-only posts are intentionally supported.
- `status` supports `ACTIVE`, `HIDDEN`, and `DELETED`.
- `moderation_status` supports `APPROVED`, `PENDING_REVIEW`, and `REJECTED`.
- Soft deletes set `status = 'DELETED'` and `deleted_at`, but keep rows for audit and report integrity.
- Phase 1 feed compatibility can alias `author_profile_id` to the old response field currently named `userId`.

### 6.3 `community_post_reactions`

Purpose: user reactions on posts.

Fields:
- `id`
- `post_id` references `community_posts(id)`
- `user_profile_id` references `profiles(id)`
- `reaction_type`
- `created_at`

Rules:
- Phase 2 supports `LIKE` as the primary reaction type.
- Unique index on `post_id`, `user_profile_id`, and `reaction_type`.
- React calls are idempotent: repeating the same reaction does not create duplicates.
- Unreact calls are idempotent: deleting a missing reaction returns success with no counter change.

### 6.4 `community_comments`

Purpose: comments and replies on community posts.

Fields:
- `id`
- `post_id` references `community_posts(id)`
- `author_profile_id` references `profiles(id)`
- `parent_comment_id` references `community_comments(id)`
- `content`
- `status`
- `created_at`
- `updated_at`
- `deleted_at`

Rules:
- `parent_comment_id` is nullable for top-level comments.
- Phase 2 supports one reply level in API validation, even if the database can represent deeper nesting.
- Delete is soft delete using `status = 'DELETED'`.
- Comment counters update in the same transaction as create/delete.

### 6.5 `community_saved_posts`

Purpose: saved/bookmarked posts per user.

Fields:
- `post_id` references `community_posts(id)`
- `user_profile_id` references `profiles(id)`
- `created_at`

Rules:
- Composite primary key or unique index on `post_id` and `user_profile_id`.
- Save and unsave are idempotent.

### 6.6 `community_blocks`

Purpose: community-level user blocking.

Fields:
- `id`
- `blocker_profile_id` references `profiles(id)`
- `blocked_profile_id` references `profiles(id)`
- `reason`
- `created_at`

Rules:
- Unique index on `blocker_profile_id` and `blocked_profile_id`.
- A profile cannot block itself.
- Feed, recommendations, mentions, and notification generation must exclude both directions of a block where relevant.
- Blocking does not delete existing content; it changes visibility and notification behavior for the blocker.

### 6.7 `community_reports`

Purpose: capture user reports for posts, comments, and profiles.

Fields:
- `id`
- `reporter_profile_id` references `profiles(id)`
- `target_type`
- `target_id`
- `reason_code`
- `reason_detail`
- `status`
- `reviewed_by_profile_id` references `profiles(id)`
- `reviewed_at`
- `created_at`
- `updated_at`

Rules:
- `target_type` supports `POST`, `COMMENT`, and `PROFILE`.
- `status` supports `OPEN`, `REVIEWED`, `DISMISSED`, and `ACTIONED`.
- A partial unique index should allow only one open report per reporter and target.
- Report creation increments the target report counter when the target is a post.

### 6.8 `community_mentions`

Purpose: normalized mention records for posts and comments.

Fields:
- `id`
- `content_type`
- `content_id`
- `mentioned_profile_id` references `profiles(id)`
- `mentioning_profile_id` references `profiles(id)`
- `mention_text`
- `mention_position`
- `created_at`

Rules:
- `content_type` supports `POST` and `COMMENT`.
- Mention creation must respect block rules.
- Mentions produce outbox events for future notification and websocket delivery.

### 6.9 `community_notification_preferences`

Purpose: community-specific notification controls.

Fields:
- `profile_id` references `profiles(id)`
- `in_app_enabled`
- `email_enabled`
- `whatsapp_enabled`
- `mentions_enabled`
- `comments_enabled`
- `reactions_enabled`
- `connections_enabled`
- `recommendations_enabled`
- `digest_frequency`
- `quiet_hours_start`
- `quiet_hours_end`
- `created_at`
- `updated_at`

Rules:
- One row per profile.
- Missing row means default preferences are used.
- Preferences are evaluated when creating notification or realtime fanout events, not by the frontend.

### 6.10 `community_events_outbox`

Purpose: durable internal event queue for notifications, analytics, and future websocket delivery.

Fields:
- `id`
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `actor_profile_id` references `profiles(id)`
- `recipient_profile_id` references `profiles(id)`
- `payload_json`
- `status`
- `attempts`
- `next_attempt_at`
- `published_at`
- `created_at`
- `updated_at`

Rules:
- `status` supports `PENDING`, `PUBLISHED`, `FAILED`, and `SKIPPED`.
- Every mutation service writes outbox rows inside the same transaction as the domain change.
- Phase 2 does not publish events to websocket clients.
- Later websocket workers can read `PENDING` events, apply preferences and block rules again, then mark events as `PUBLISHED`, `FAILED`, or `SKIPPED`.
- Index by `status`, `next_attempt_at`, `created_at`, `event_type`, and `recipient_profile_id`.

## 7. API Contract

All endpoints are authenticated and live under `/api/v1/community`.

Read endpoints that already exist:
- `GET /feed?mode=latest|ranked&limit=20`
- `GET /recommendations/people?limit=12`
- `GET /networks`

New Phase 2 endpoints:
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

API behavior:
- Controllers extract the authenticated Supabase profile ID the same way `CommunityController` does today.
- Services own validation, block checks, idempotency, counter updates, and outbox writes.
- DTOs should keep the B2C response shape stable where it overlaps with Phase 1 feed and network contracts.
- Request bodies should use explicit fields instead of passing raw frontend state maps.

## 8. Permissions And Policy

- All Phase 2 community endpoints require authentication.
- Only the author can update or delete their post.
- Only the comment author can delete their comment.
- Admin moderation permissions can be added later; Phase 2 stores moderation state but does not expose a moderation console.
- Hidden or deleted content is not returned to normal viewers.
- Authors can still receive enough state to understand their own hidden or deleted content where needed.
- Blocked relationships suppress feed visibility, people recommendations, mentions, reaction notifications, comment notifications, and future realtime fanout.
- Reports remain allowed even if the reporting user has blocked or been blocked by the target profile.

## 9. Realtime And Websocket Readiness

Phase 2 should not add websocket delivery, but it must create the backend events that a websocket or server-sent event worker can consume later.

Event types to record:
- `COMMUNITY_POST_CREATED`
- `COMMUNITY_POST_UPDATED`
- `COMMUNITY_POST_DELETED`
- `COMMUNITY_POST_REACTED`
- `COMMUNITY_POST_UNREACTED`
- `COMMUNITY_COMMENT_CREATED`
- `COMMUNITY_COMMENT_DELETED`
- `COMMUNITY_POST_SAVED`
- `COMMUNITY_POST_UNSAVED`
- `COMMUNITY_USER_BLOCKED`
- `COMMUNITY_USER_UNBLOCKED`
- `COMMUNITY_CONTENT_REPORTED`
- `COMMUNITY_MENTION_CREATED`
- `COMMUNITY_NOTIFICATION_PREFERENCES_UPDATED`

Realtime delivery should be a later worker concern:
- It reads `community_events_outbox`.
- It applies block and notification preference checks.
- It publishes to websocket topics such as profile notifications, post comment streams, and feed invalidation channels.
- It marks outbox rows as complete or retryable.

This keeps user-facing mutations fast and reliable while preserving an auditable event stream.

## 10. Compatibility And Rollout

Phase 2 rollout order:
- Add schema migrations and seed categories.
- Add backend entities/repositories or JDBC access consistent with the service style chosen during implementation.
- Add DTOs and services for categories, posts, reactions, comments, saves, blocks, reports, preferences, and outbox events.
- Extend `CommunityController` or split into focused community controllers under the same route prefix.
- Keep Phase 1 feed reads on legacy `posts` until B2C write flows are migrated.
- Add block filtering to reads where possible without requiring legacy data migration.
- Deploy backend with `deploy.sh`.
- Verify production health and authenticated community endpoints.

The first implementation should not change B2C visible behavior. B2C can start using mutation APIs in Phase 3 after the backend contract is deployed and verified.

## 11. Testing Strategy

Backend tests should cover:
- category listing returns active categories ordered by `sort_order`
- post create stores a post, initializes counters, and writes an outbox event
- post update/delete enforce ownership
- reaction create/delete are idempotent and update counters once
- comment create/delete enforce post visibility and ownership
- save/unsave are idempotent and update counters once
- block/unblock are idempotent and suppress blocked users in service-level queries
- report creation prevents duplicate open reports for the same reporter and target
- notification preferences return defaults when no row exists and persist overrides
- outbox event records contain event type, actor, aggregate, recipient when known, and payload JSON

Verification commands:
- `./gradlew test`
- focused test commands for new community service/controller tests during implementation
- production smoke checks after deployment using `/api/admin/migration/health` and authenticated `/api/v1/community/*` calls

## 12. Risks And Mitigations

Counter drift:
- Update counters inside the same transaction as reactions, comments, saves, and reports.
- Add a future reconciliation job if high-volume activity exposes drift.

Legacy/new data split:
- Keep reads stable in Phase 2.
- Make Phase 3 explicitly choose between unioned reads or full cutover to `community_posts`.

Outbox growth:
- Index pending events and add retention/archival rules when delivery workers are introduced.

Realtime semantics:
- Avoid exposing websocket behavior before block rules, preferences, and event payloads are stable.

Moderation workflow:
- Store moderation and report data now.
- Add admin review surfaces in a later moderation-focused phase.

## 13. Acceptance Criteria

- Flyway migrations apply cleanly from current `main`.
- Backend tests pass for the new community schema and service behavior.
- `/api/v1/community` exposes the new authenticated mutation and preference endpoints.
- Mutations create `community_events_outbox` rows without requiring websocket infrastructure.
- Existing Phase 1 community read endpoints continue to work.
- No direct production data edits are required.
- Deployment uses the backend `deploy.sh` flow.
