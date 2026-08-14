package com.prosper.prospermentor.service.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityRealtimeServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityRealtimeBroker broker = mock(CommunityRealtimeBroker.class);
    private final CommunityRealtimeService service = new CommunityRealtimeService(jdbc, new ObjectMapper(), broker);

    @Test
    void getEventsMapsOutboxRowsToRoadmapEventNamesAndScopesByVisiblePosts() {
        UUID viewerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.parse("2026-08-14T09:15:00Z");
        OffsetDateTime createdAt = since.plusSeconds(2);

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<CommunityRealtimeEventItem> rowMapper = invocation.getArgument(2);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getObject("id", UUID.class)).thenReturn(eventId);
                    when(resultSet.getString("event_type")).thenReturn("COMMUNITY_COMMENT_CREATED");
                    when(resultSet.getString("aggregate_type")).thenReturn("COMMENT");
                    when(resultSet.getObject("aggregate_id", UUID.class)).thenReturn(commentId);
                    when(resultSet.getObject("actor_profile_id", UUID.class)).thenReturn(actorId);
                    when(resultSet.getObject("recipient_profile_id", UUID.class)).thenReturn(null);
                    when(resultSet.getString("payload_json")).thenReturn("""
                            {"postId":"%s","commentId":"%s"}
                            """.formatted(postId, commentId));
                    when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        var response = service.getEvents(viewerId, since, Set.of(postId), 10);

        assertThat(response.limit()).isEqualTo(10);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).id()).isEqualTo(eventId);
        assertThat(response.events().get(0).type()).isEqualTo("community.post.comment.created");
        assertThat(response.events().get(0).sourceType()).isEqualTo("COMMUNITY_COMMENT_CREATED");
        assertThat(response.events().get(0).aggregateType()).isEqualTo("COMMENT");
        assertThat(response.events().get(0).payload()).containsEntry("postId", postId.toString());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("community_events_outbox");
        assertThat(sql.getValue()).contains("community_blocks");
        assertThat(sql.getValue()).contains("payload_json->>'postId'");
        assertThat(sql.getValue()).contains("e.aggregate_type NOT IN ('POST', 'COMMENT')");
        assertThat(sql.getValue()).doesNotContain("e.aggregate_type <> 'POST'");
        assertThat(parameters.getValue().getValue("viewerId")).isEqualTo(viewerId);
        assertThat(parameters.getValue().getValue("since")).isEqualTo(since);
        assertThat(parameters.getValue().getValue("visiblePostIds")).isEqualTo(Set.of(postId));
        assertThat(parameters.getValue().getValue("limit")).isEqualTo(10);
    }

    @Test
    void openStreamDelegatesToBrokerWithVisiblePostScope() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        when(broker.subscribe(eq(viewerId), eq(Set.of(postId)))).thenReturn(emitter);

        SseEmitter result = service.openStream(viewerId, Set.of(postId));

        assertThat(result).isEqualTo(emitter);
        verify(broker).subscribe(viewerId, Set.of(postId));
    }

    @Test
    void getEventsOmitsSincePredicateWhenNoCursorIsProvided() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var response = service.getEvents(viewerId, null, Set.of(), 0);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(response.limit()).isEqualTo(50);
        assertThat(sql.getValue()).doesNotContain(":since");
        assertThat(parameters.getValue().hasValue("since")).isFalse();
    }
}
