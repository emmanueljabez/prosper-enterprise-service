package com.prosper.prospermentor.service.community;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityReadServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityReadService service = new CommunityReadService(jdbc);

    @Test
    void normalizesFeedModeToLatestWhenInputIsUnknown() {
        assertThat(service.normalizeFeedMode("popular")).isEqualTo("latest");
        assertThat(service.normalizeFeedMode("ranked")).isEqualTo("ranked");
        assertThat(service.normalizeFeedMode("latest")).isEqualTo("latest");
        assertThat(service.normalizeFeedMode(null)).isEqualTo("latest");
    }

    @Test
    void clampsLimitToSafeRange() {
        assertThat(service.clampLimit(-1)).isEqualTo(20);
        assertThat(service.clampLimit(0)).isEqualTo(20);
        assertThat(service.clampLimit(10)).isEqualTo(10);
        assertThat(service.clampLimit(200)).isEqualTo(50);
    }

    @Test
    void recommendationScoreUsesSharedSignals() {
        var viewer = Map.of(
                "role", "mentee",
                "industry", "Technology",
                "country", "Kenya",
                "interests", List.of("leadership", "product")
        );
        var candidate = Map.of(
                "role", "mentor",
                "industry", "technology",
                "country", "Kenya",
                "interests", List.of("product", "strategy")
        );

        assertThat(service.calculateRecommendationScore(viewer, candidate)).isGreaterThanOrEqualTo(70);
    }

    @Test
    void feedQueryDoesNotRequireOptionalLinkPreviewColumns() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getFeed(UUID.randomUUID(), "latest", 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .doesNotContain("p.link_url")
                .doesNotContain("p.link_title")
                .doesNotContain("p.link_description")
                .doesNotContain("p.link_image")
                .contains("NULL::text AS link_url");
    }

    @Test
    void feedQueryExcludesCommunityBlocksInBothDirections() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getFeed(UUID.randomUUID(), "latest", 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId")
                .contains("blocked_profile_id = p.user_id")
                .contains("blocker_profile_id = p.user_id")
                .contains("blocked_profile_id = :viewerId");
    }

    @Test
    void recommendationQueryExcludesCommunityBlocksInBothDirections() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", viewerId,
                        "role", "mentee",
                        "industry", "Technology",
                        "country", "Kenya",
                        "interests", List.of("leadership")
                ));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        service.getRecommendedPeople(viewerId, 12);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));

        assertThat(sqlCaptor.getValue())
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId")
                .contains("blocked_profile_id = p.id")
                .contains("blocker_profile_id = p.id")
                .contains("blocked_profile_id = :viewerId");
    }
}
