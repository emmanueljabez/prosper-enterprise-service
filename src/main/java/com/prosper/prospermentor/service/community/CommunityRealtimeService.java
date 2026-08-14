package com.prosper.prospermentor.service.community;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityRealtimeService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CommunityRealtimeBroker broker;

    public CommunityRealtimeEventsResponse getEvents(
            UUID viewerId,
            OffsetDateTime since,
            Set<UUID> visiblePostIds,
            int requestedLimit
    ) {
        requireViewer(viewerId);
        int limit = normalizeLimit(requestedLimit);
        Set<UUID> scopedPostIds = normalizeIds(visiblePostIds);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("limit", limit);

        String sinceClause = "";
        if (since != null) {
            parameters.addValue("since", since);
            sinceClause = "  AND e.created_at > :since\n";
        }

        String visiblePostClause = "";
        if (!scopedPostIds.isEmpty()) {
            parameters.addValue("visiblePostIds", scopedPostIds);
            visiblePostClause = """
                      AND (
                          (e.aggregate_type = 'POST' AND e.aggregate_id IN (:visiblePostIds))
                          OR (
                              e.payload_json ? 'postId'
                              AND CAST(e.payload_json->>'postId' AS uuid) IN (:visiblePostIds)
                          )
                          OR e.aggregate_type NOT IN ('POST', 'COMMENT')
                      )
                    """;
        }

        String sql = """
                SELECT
                    e.id,
                    e.event_type,
                    e.aggregate_type,
                    e.aggregate_id,
                    e.actor_profile_id,
                    e.recipient_profile_id,
                    e.payload_json::text AS payload_json,
                    e.created_at
                FROM community_events_outbox e
                WHERE e.status <> 'SKIPPED'
                %s
                  AND (
                      e.recipient_profile_id IS NULL
                      OR e.recipient_profile_id = :viewerId
                      OR e.actor_profile_id = :viewerId
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM community_blocks b
                      WHERE e.actor_profile_id IS NOT NULL
                        AND (
                            (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = e.actor_profile_id)
                            OR (b.blocker_profile_id = e.actor_profile_id AND b.blocked_profile_id = :viewerId)
                        )
                  )
                %s
                ORDER BY e.created_at ASC
                LIMIT :limit
                """.formatted(sinceClause, visiblePostClause);

        return new CommunityRealtimeEventsResponse(jdbc.query(sql, parameters, this::mapEvent), limit);
    }

    public SseEmitter openStream(UUID viewerId, Set<UUID> visiblePostIds) {
        requireViewer(viewerId);
        return broker.subscribe(viewerId, normalizeIds(visiblePostIds));
    }

    public void publish(CommunityRealtimeEventItem event) {
        if (event != null) {
            broker.publish(event);
        }
    }

    public CommunityRealtimeEventItem toRealtimeEvent(
            UUID id,
            String sourceType,
            String aggregateType,
            UUID aggregateId,
            UUID actorProfileId,
            UUID recipientProfileId,
            Map<String, Object> payload,
            OffsetDateTime createdAt
    ) {
        return new CommunityRealtimeEventItem(
                id,
                CommunityRealtimeEventNames.toRealtimeType(sourceType),
                sourceType,
                aggregateType,
                aggregateId,
                actorProfileId,
                recipientProfileId,
                payload == null ? Map.of() : payload,
                createdAt
        );
    }

    private CommunityRealtimeEventItem mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        String sourceType = resultSet.getString("event_type");
        return toRealtimeEvent(
                resultSet.getObject("id", UUID.class),
                sourceType,
                resultSet.getString("aggregate_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getObject("actor_profile_id", UUID.class),
                resultSet.getObject("recipient_profile_id", UUID.class),
                readPayload(resultSet.getString("payload_json")),
                readCreatedAt(resultSet)
        );
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Community realtime event payload is not valid JSON", e);
        }
    }

    private OffsetDateTime readCreatedAt(ResultSet resultSet) throws SQLException {
        Object value = resultSet.getObject("created_at");
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        return resultSet.getObject("created_at", OffsetDateTime.class);
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private Set<UUID> normalizeIds(Set<UUID> ids) {
        return ids == null || ids.isEmpty() ? Set.of() : Set.copyOf(ids);
    }

    private void requireViewer(UUID viewerId) {
        if (viewerId == null) {
            throw new IllegalArgumentException("viewerId is required");
        }
    }
}
