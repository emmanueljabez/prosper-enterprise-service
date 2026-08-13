package com.prosper.prospermentor.service.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityEventOutboxService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void recordEvent(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            UUID actorProfileId,
            UUID recipientProfileId,
            Map<String, Object> payload
    ) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(safePayload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Community event payload must be serializable", e);
        }

        jdbc.update("""
                INSERT INTO community_events_outbox (
                    id,
                    event_type,
                    aggregate_type,
                    aggregate_id,
                    actor_profile_id,
                    recipient_profile_id,
                    payload_json,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :eventType,
                    :aggregateType,
                    :aggregateId,
                    :actorProfileId,
                    :recipientProfileId,
                    CAST(:payloadJson AS jsonb),
                    'PENDING',
                    now(),
                    now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("eventType", eventType)
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId)
                .addValue("actorProfileId", actorProfileId)
                .addValue("recipientProfileId", recipientProfileId)
                .addValue("payloadJson", payloadJson));
    }
}
