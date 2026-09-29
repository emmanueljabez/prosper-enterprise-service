package com.prosper.prospermentor.badge;

import com.prosper.prospermentor.controller.BadgeAdminController;
import com.prosper.prospermentor.controller.ProfileBadgeController;
import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeDto;
import com.prosper.prospermentor.dto.badge.ManualBadgeGrantRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.BadgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeControllerTest {

    @Mock
    BadgeService badgeService;

    @Test
    void getMyBadgesReturnsCurrentUserBadges() {
        UUID profileId = UUID.randomUUID();
        BadgeAwardDto award = award(profileId);
        when(badgeService.getMyBadges(profileId)).thenReturn(List.of(award));

        ProfileBadgeController controller = new ProfileBadgeController(badgeService);
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getMyBadges(authentication(profileId, "MENTEE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsEntry("count", 1);
    }

    @Test
    void getPublicBadgesDoesNotRequireAuthentication() {
        UUID profileId = UUID.randomUUID();
        when(badgeService.getPublicBadges(profileId)).thenReturn(List.of(award(profileId)));

        ProfileBadgeController controller = new ProfileBadgeController(badgeService);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getPublicBadges(profileId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("count", 1);
    }

    @Test
    void adminGrantBadgeRejectsNonProsperAdmin() {
        UUID profileId = UUID.randomUUID();
        UUID badgeTypeId = UUID.randomUUID();

        BadgeAdminController controller = new BadgeAdminController(badgeService);
        ResponseEntity<ApiResponse<BadgeAwardDto>> response = controller.grantBadge(
                profileId,
                new ManualBadgeGrantRequest(badgeTypeId, "Founding mentor"),
                authentication(UUID.randomUUID(), "MENTEE")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Prosper admin access is required");
        verify(badgeService, never()).grantManualBadge(profileId, badgeTypeId, "Founding mentor", null);
    }

    @Test
    void adminGrantBadgeCallsServiceForProsperAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID badgeTypeId = UUID.randomUUID();
        BadgeAwardDto award = award(profileId);
        when(badgeService.grantManualBadge(profileId, badgeTypeId, "Founding mentor", adminId)).thenReturn(award);

        BadgeAdminController controller = new BadgeAdminController(badgeService);
        ResponseEntity<ApiResponse<BadgeAwardDto>> response = controller.grantBadge(
                profileId,
                new ManualBadgeGrantRequest(badgeTypeId, "Founding mentor"),
                authentication(adminId, "ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(award);
        verify(badgeService).grantManualBadge(profileId, badgeTypeId, "Founding mentor", adminId);
    }

    @Test
    void adminListTypesRequiresProsperAdmin() {
        UUID adminId = UUID.randomUUID();
        BadgeTypeDto type = new BadgeTypeDto(
                UUID.randomUUID(),
                "G4G Mentor",
                "g4g-mentor",
                "Girls for Girls mentor",
                "AFFILIATION",
                "AFFILIATION_RULE",
                "ACTIVE",
                "G4G Mentor",
                "#8f1f74",
                "#f7e8f3",
                "#6f1859",
                10
        );
        when(badgeService.getBadgeTypes()).thenReturn(List.of(type));

        BadgeAdminController controller = new BadgeAdminController(badgeService);
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.listTypes(authentication(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("count", 1);
    }

    private Authentication authentication(UUID userId, String role) {
        SupabaseUserDetails userDetails = new SupabaseUserDetails(
                userId.toString(),
                userId + "@example.com",
                role
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }

    private BadgeAwardDto award(UUID profileId) {
        return new BadgeAwardDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "G4G Mentee",
                "G4G Mentee",
                "AFFILIATION",
                "#8f1f74",
                "#f7e8f3",
                "#6f1859",
                "AFFILIATION_RULE",
                "Automatic - company link confirmed",
                LocalDateTime.now(),
                true,
                false,
                "SHOWN"
        );
    }
}
