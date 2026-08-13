package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFeedResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.NetworkOverviewResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendedPeopleResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.community.CommunityMutationService;
import com.prosper.prospermentor.service.community.CommunityReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityControllerTest {
    private final CommunityReadService communityReadService = mock(CommunityReadService.class);
    private final CommunityMutationService communityMutationService = mock(CommunityMutationService.class);
    private final CommunityController controller = new CommunityController(communityReadService, communityMutationService);

    @Test
    void feedRequiresAuthentication() {
        var response = controller.getFeed(null, "latest", 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void feedUsesAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.getFeed(eq(userId), eq("latest"), eq(20)))
                .thenReturn(new CommunityFeedResponse(List.of(), "latest", 20));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getFeed(auth, "latest", 20);
        var body = response.getBody();
        var data = (CommunityFeedResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.mode()).isEqualTo("latest");
    }

    @Test
    void recommendationsUseAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.getRecommendedPeople(eq(userId), eq(12)))
                .thenReturn(new RecommendedPeopleResponse(List.of(), 12));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getRecommendedPeople(auth, 12);
        var body = response.getBody();
        var data = (RecommendedPeopleResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.limit()).isEqualTo(12);
    }

    @Test
    void networkOverviewUsesAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.getNetworkOverview(eq(userId)))
                .thenReturn(new NetworkOverviewResponse(List.of(), List.of(), List.of(), List.of(), List.of()));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getNetworkOverview(auth);
        var body = response.getBody();
        var data = (NetworkOverviewResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.connections()).isEmpty();
    }
}
