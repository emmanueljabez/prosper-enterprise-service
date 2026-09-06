package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyBrandingService;
import com.prosper.prospermentor.service.CompanyService;
import com.prosper.prospermentor.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerBrandingTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private CompanyService companyService;
    @Mock
    private ProfileService profileService;
    @Mock
    private CompanyBrandingService companyBrandingService;

    @InjectMocks
    private CompanyController companyController;

    @Test
    void uploadCompanyLogo_shouldAuthorizeCompanyAdminAndReturnUpdatedCompany() {
        Company company = company(COMPANY_ID);
        company.setLogoUrl("/api/v1/companies/" + COMPANY_ID + "/branding/logo/logo.png");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(COMPANY_ID)));
        when(companyBrandingService.uploadLogo(COMPANY_ID, file))
                .thenReturn(ApiResponse.success("Company logo uploaded successfully", company));

        ResponseEntity<ApiResponse<Company>> response = companyController.uploadCompanyLogo(
                COMPANY_ID,
                file,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getLogoUrl()).isEqualTo(company.getLogoUrl());
        verify(companyBrandingService).uploadLogo(COMPANY_ID, file);
    }

    @Test
    void deleteCompanyLogo_shouldRejectCompanyAdminFromAnotherCompany() {
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(OTHER_COMPANY_ID)));

        ResponseEntity<ApiResponse<Company>> response = companyController.deleteCompanyLogo(
                COMPANY_ID,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Not authorized to access this company");
        verify(companyBrandingService, never()).deleteLogo(any());
    }

    @Test
    void deleteCompanyLogo_shouldClearLogoForCompanyAdmin() {
        Company company = company(COMPANY_ID);
        company.setLogoUrl(null);

        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile(COMPANY_ID)));
        when(companyBrandingService.deleteLogo(COMPANY_ID))
                .thenReturn(ApiResponse.success("Company logo removed successfully", company));

        ResponseEntity<ApiResponse<Company>> response = companyController.deleteCompanyLogo(
                COMPANY_ID,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getLogoUrl()).isNull();
        verify(companyBrandingService).deleteLogo(COMPANY_ID);
    }

    private Company company(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        company.setName("Kenya Airways");
        company.setEmailAddress("admin@kenya-airways.test");
        company.setPhoneNumber("+254700000000");
        return company;
    }

    private Profile companyAdminProfile(UUID companyId) {
        Profile profile = new Profile();
        profile.setId(ADMIN_ID);
        profile.setRole("company_admin");
        profile.setCompany(company(companyId));
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
