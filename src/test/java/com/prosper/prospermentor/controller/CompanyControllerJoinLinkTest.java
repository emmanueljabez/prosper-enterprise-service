package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyJoinLinkDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyBrandingService;
import com.prosper.prospermentor.service.CompanyJoinLinkService;
import com.prosper.prospermentor.service.CompanyService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerJoinLinkTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private CompanyService companyService;
    @Mock
    private ProfileService profileService;
    @Mock
    private CompanyBrandingService companyBrandingService;
    @Mock
    private CompanyJoinLinkService companyJoinLinkService;

    @InjectMocks
    private CompanyController companyController;

    @Test
    void getJoinLink_shouldRequireCompanyAdminAndReturnLink() {
        CompanyJoinLinkDto dto = CompanyJoinLinkDto.builder()
                .companyId(COMPANY_ID)
                .companyName("Girls for Girls")
                .joinToken("join-token")
                .joinUrl("https://enterprise.prospermentor.com/auth/signup?audience=mentee&companyJoinToken=join-token")
                .status("ACTIVE")
                .build();

        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(COMPANY_ID)));
        when(companyJoinLinkService.getOrCreateJoinLink(COMPANY_ID, ADMIN_ID)).thenReturn(dto);

        ResponseEntity<ApiResponse<CompanyJoinLinkDto>> response = companyController.getCompanyJoinLink(
                COMPANY_ID,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(dto);
        verify(companyJoinLinkService).getOrCreateJoinLink(COMPANY_ID, ADMIN_ID);
    }

    @Test
    void regenerateJoinLink_shouldRejectAdminFromAnotherCompany() {
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(OTHER_COMPANY_ID)));

        ResponseEntity<ApiResponse<CompanyJoinLinkDto>> response = companyController.regenerateCompanyJoinLink(
                COMPANY_ID,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Not authorized to access this company");
        verify(companyJoinLinkService, never()).regenerateJoinLink(COMPANY_ID, ADMIN_ID);
    }

    private Profile companyAdminProfile(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        company.setName("Girls for Girls");
        company.setEmailAddress("admin@example.com");
        company.setPhoneNumber("+254700000000");

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
