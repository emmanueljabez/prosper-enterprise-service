package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyWalkthroughProgressDto;
import com.prosper.prospermentor.dto.UpdateCompanyWalkthroughProgressRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyWalkthroughProgressService;
import com.prosper.prospermentor.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyWalkthroughProgressControllerTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String VERSION = "2026-08-company-admin-v1";

    @Mock
    private CompanyWalkthroughProgressService walkthroughProgressService;
    @Mock
    private ProfileService profileService;

    @InjectMocks
    private CompanyWalkthroughProgressController controller;

    @Test
    void getProgress_shouldUseAuthenticatedProfileId() {
        CompanyWalkthroughProgressDto dto = CompanyWalkthroughProgressDto.builder()
                .companyId(COMPANY_ID)
                .profileId(ADMIN_ID)
                .version(VERSION)
                .build();
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(COMPANY_ID)));
        when(walkthroughProgressService.getProgress(COMPANY_ID, ADMIN_ID, VERSION))
                .thenReturn(ApiResponse.success("Walkthrough progress retrieved successfully", dto));

        ResponseEntity<ApiResponse<CompanyWalkthroughProgressDto>> response = controller.getProgress(
                COMPANY_ID,
                VERSION,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getProfileId()).isEqualTo(ADMIN_ID);
        verify(walkthroughProgressService).getProgress(COMPANY_ID, ADMIN_ID, VERSION);
    }

    @Test
    void updateProgress_shouldUseAuthenticatedProfileId() {
        UpdateCompanyWalkthroughProgressRequest request = new UpdateCompanyWalkthroughProgressRequest();
        request.setVersion(VERSION);
        CompanyWalkthroughProgressDto dto = CompanyWalkthroughProgressDto.builder()
                .companyId(COMPANY_ID)
                .profileId(ADMIN_ID)
                .version(VERSION)
                .build();
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(COMPANY_ID)));
        when(walkthroughProgressService.updateProgress(eq(COMPANY_ID), eq(ADMIN_ID), any(UpdateCompanyWalkthroughProgressRequest.class)))
                .thenReturn(ApiResponse.success("Walkthrough progress saved successfully", dto));

        ResponseEntity<ApiResponse<CompanyWalkthroughProgressDto>> response = controller.updateProgress(
                COMPANY_ID,
                request,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(walkthroughProgressService).updateProgress(COMPANY_ID, ADMIN_ID, request);
    }

    @Test
    void getProgress_shouldRejectCompanyAdminFromAnotherCompany() {
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(OTHER_COMPANY_ID)));

        ResponseEntity<ApiResponse<CompanyWalkthroughProgressDto>> response = controller.getProgress(
                COMPANY_ID,
                VERSION,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(walkthroughProgressService, never()).getProgress(any(), any(), any());
    }

    private Profile companyAdminProfile(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        Profile profile = new Profile();
        profile.setId(ADMIN_ID);
        profile.setRole("company_admin");
        profile.setCompany(company);
        return profile;
    }

    private Authentication authentication(UUID userId, String role) {
        SupabaseUserDetails userDetails = new SupabaseUserDetails(
                userId.toString(),
                userId + "@example.com",
                role
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }
}
