# Community Discovery API Design

## Goal

Add a backend-owned discovery API foundation for the B2C community experience so the frontend can search posts, people, categories, and hashtags through the same authorization and block-filtering layer used by the existing community APIs.

## Scope

This slice adds read-only backend endpoints only. It does not change frontend UI, ranking ML, messaging, WebSocket delivery, or the community write model.

## API Design

Add `GET /api/v1/community/search` with query parameters:

- `q`: required search text after trimming.
- `type`: optional result scope. Supported values are `all`, `posts`, `people`, `categories`, and `hashtags`.
- `limit`: optional per-result limit, using the existing `CommunityReadService.clampLimit` behavior.

Response data includes `query`, normalized `type`, `limit`, and result arrays for `posts`, `people`, `categories`, and `hashtags`.

Add `GET /api/v1/community/discovery/people` with query parameter:

- `limit`: optional result limit.

Response data includes `suggestedPeople` from the existing recommendation engine and `recentConnections` from accepted sync relationships, both filtered through community blocks.

## Data Flow

Requests continue through `CommunityController`, authenticated via the existing Supabase principal extraction helper. `CommunityReadService` owns SQL construction, limit/type normalization, scoring, and block filtering. Search reads from the deployed legacy community surface tables already used by the feed bridge: `posts`, `profiles`, `community_categories`, `post_likes`, `saved_posts`, `syncs`, `follows`, and `community_blocks`.

## Filtering Rules

All people and post discovery results must exclude users blocked by the viewer and users who blocked the viewer. Hidden posts are visible only to their author, matching feed behavior. Category and hashtag results are public metadata, but hashtag extraction only considers posts visible to the viewer.

## Testing

Controller tests cover authentication and delegation for both new endpoints. Service tests cover type normalization, blank query rejection, SQL block filtering, hashtag extraction, and recent connection filtering.
