package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFeedResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionRequestsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPeopleDiscoveryResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileAnalyticsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileNetworkResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileViewTrackRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileViewTrackResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySearchResponse;
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
import static org.mockito.Mockito.verify;
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

    @Test
    void profileNetworkUsesAuthenticatedViewerAndPathProfile() {
        UUID viewerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(communityReadService.getProfileNetwork(eq(viewerId), eq(profileId)))
                .thenReturn(new CommunityProfileNetworkResponse(
                        profileId,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        0
                ));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(viewerId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getProfileNetwork(auth, profileId);
        var body = response.getBody();
        var data = (CommunityProfileNetworkResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.profileId()).isEqualTo(profileId);
        verify(communityReadService).getProfileNetwork(viewerId, profileId);
    }

    @Test
    void profileAnalyticsUsesAuthenticatedViewerAndPathProfile() {
        UUID viewerId = UUID.randomUUID();
        UUID profileId = viewerId;
        when(communityReadService.getProfileAnalytics(eq(viewerId), eq(profileId), eq(50)))
                .thenReturn(new CommunityProfileAnalyticsResponse(
                        profileId,
                        List.of(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                ));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(viewerId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getProfileAnalytics(auth, profileId, 50);
        var body = response.getBody();
        var data = (CommunityProfileAnalyticsResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.profileId()).isEqualTo(profileId);
        verify(communityReadService).getProfileAnalytics(viewerId, profileId, 50);
    }

    @Test
    void profileViewTrackingUsesAuthenticatedViewerAndPathProfile() {
        UUID viewerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        var request = new CommunityProfileViewTrackRequest("mentor_profile", "recommendation", true);
        when(communityMutationService.trackProfileView(eq(viewerId), eq(profileId), eq(request)))
                .thenReturn(new CommunityProfileViewTrackResponse(profileId, viewerId, true));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(viewerId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.trackProfileView(auth, profileId, request);
        var body = response.getBody();
        var data = (CommunityProfileViewTrackResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body).isNotNull();
        assertThat(data.profileId()).isEqualTo(profileId);
        assertThat(data.viewerId()).isEqualTo(viewerId);
        assertThat(data.tracked()).isTrue();
        verify(communityMutationService).trackProfileView(viewerId, profileId, request);
    }

    @Test
    void connectionRequestsUseAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.getConnectionRequests(eq(userId)))
                .thenReturn(new CommunityConnectionRequestsResponse(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getConnectionRequests(auth);
        var body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        var data = (CommunityConnectionRequestsResponse) body.getData();
        assertThat(data.incoming()).isEmpty();
        verify(communityReadService).getConnectionRequests(userId);
    }

    @Test
    void searchRequiresAuthentication() {
        var response = controller.search(null, "mentor", "all", 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void searchUsesAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.search(eq(userId), eq("mentor"), eq("all"), eq(10)))
                .thenReturn(new CommunitySearchResponse("mentor", "all", 10, List.of(), List.of(), List.of(), List.of()));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.search(auth, "mentor", "all", 10);
        var body = response.getBody();
        var data = (CommunitySearchResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.query()).isEqualTo("mentor");
        assertThat(data.type()).isEqualTo("all");
    }

    @Test
    void peopleDiscoveryUsesAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        when(communityReadService.getPeopleDiscovery(eq(userId), eq(8)))
                .thenReturn(new CommunityPeopleDiscoveryResponse(List.of(), List.of(), 8));

        var auth = new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );

        var response = controller.getPeopleDiscovery(auth, 8);
        var body = response.getBody();
        var data = (CommunityPeopleDiscoveryResponse) body.getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(data.limit()).isEqualTo(8);
    }
}
