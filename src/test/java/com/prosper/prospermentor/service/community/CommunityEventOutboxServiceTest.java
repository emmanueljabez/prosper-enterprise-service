package com.prosper.prospermentor.service.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityEventOutboxServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityRealtimeService realtimeService = mock(CommunityRealtimeService.class);
    private final CommunityEventOutboxService service = new CommunityEventOutboxService(
            jdbc,
            new ObjectMapper(),
            realtimeService
    );

    @Test
    void recordEventPersistsOutboxRowAndPublishesRealtimeProjection() {
        UUID postId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        service.recordEvent(
                "COMMUNITY_POST_REACTED",
                "POST",
                postId,
                actorId,
                null,
                Map.of("postId", postId, "reactionType", "LIKE")
        );

        var parameters = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), parameters.capture());
        var event = org.mockito.ArgumentCaptor.forClass(CommunityRealtimeEventItem.class);
        verify(realtimeService).publish(event.capture());

        assertThat(parameters.getValue().getValue("id")).isEqualTo(event.getValue().id());
        assertThat(event.getValue().type()).isEqualTo("community.post.reaction.updated");
        assertThat(event.getValue().sourceType()).isEqualTo("COMMUNITY_POST_REACTED");
        assertThat(event.getValue().aggregateType()).isEqualTo("POST");
        assertThat(event.getValue().aggregateId()).isEqualTo(postId);
        assertThat(event.getValue().actorProfileId()).isEqualTo(actorId);
        assertThat(event.getValue().payload()).containsEntry("reactionType", "LIKE");
    }
}
