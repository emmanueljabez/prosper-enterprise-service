package com.prosper.prospermentor.service.community;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
