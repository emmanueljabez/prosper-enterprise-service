package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyWalkthroughProgressDto;
import com.prosper.prospermentor.dto.UpdateCompanyWalkthroughProgressRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyWalkthroughProgress;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanyWalkthroughProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyWalkthroughProgressServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROFILE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String VERSION = "2026-08-company-admin-v1";

    @Mock
    private CompanyWalkthroughProgressRepository walkthroughProgressRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProfileService profileService;

    @InjectMocks
    private CompanyWalkthroughProgressService service;

    @Test
    void getProgress_shouldReturnDefaultForAuthenticatedProfileWhenMissing() {
        Company company = company(COMPANY_ID);
        Profile profile = profile(PROFILE_ID, company);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(profileService.getProfileWithCompany(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(walkthroughProgressRepository.findByCompanyIdAndProfileIdAndVersion(COMPANY_ID, PROFILE_ID, VERSION))
                .thenReturn(Optional.empty());

        ApiResponse<CompanyWalkthroughProgressDto> response = service.getProgress(COMPANY_ID, PROFILE_ID, VERSION);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(response.getData().getProfileId()).isEqualTo(PROFILE_ID);
        assertThat(response.getData().getVersion()).isEqualTo(VERSION);
        assertThat(response.getData().isIntroDismissed()).isFalse();
        assertThat(response.getData().getCompletedTaskIds()).isEmpty();
        assertThat(response.getData().getCompletedTourIds()).isEmpty();
    }

    @Test
    void updateProgress_shouldUpsertByCompanyProfileVersionAndDeduplicateIds() {
        Company company = company(COMPANY_ID);
        Profile profile = profile(PROFILE_ID, company);
        UpdateCompanyWalkthroughProgressRequest request = new UpdateCompanyWalkthroughProgressRequest();
        request.setVersion(VERSION);
        request.setIntroDismissed(true);
        request.setCompletedTaskIds(List.of("review_dashboard", "invite_mentees", "review_dashboard"));
        request.setCompletedTourIds(List.of("admin-dashboard-overview", "admin-mentees-overview", "admin-dashboard-overview"));

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(profileService.getProfileWithCompany(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(walkthroughProgressRepository.findByCompanyIdAndProfileIdAndVersion(COMPANY_ID, PROFILE_ID, VERSION))
                .thenReturn(Optional.empty());
        when(walkthroughProgressRepository.save(any(CompanyWalkthroughProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<CompanyWalkthroughProgressDto> response = service.updateProgress(COMPANY_ID, PROFILE_ID, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isIntroDismissed()).isTrue();
        assertThat(response.getData().getCompletedTaskIds()).containsExactly("review_dashboard", "invite_mentees");
        assertThat(response.getData().getCompletedTourIds()).containsExactly("admin-dashboard-overview", "admin-mentees-overview");
        assertThat(response.getData().getLastSeenAt()).isNotNull();

        ArgumentCaptor<CompanyWalkthroughProgress> savedCaptor = ArgumentCaptor.forClass(CompanyWalkthroughProgress.class);
        verify(walkthroughProgressRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getCompany()).isSameAs(company);
        assertThat(savedCaptor.getValue().getProfile()).isSameAs(profile);
        assertThat(savedCaptor.getValue().getVersion()).isEqualTo(VERSION);
    }

    @Test
    void updateProgress_shouldRejectProfileFromDifferentCompany() {
        Company requestedCompany = company(COMPANY_ID);
        Profile profile = profile(PROFILE_ID, company(OTHER_COMPANY_ID));
        UpdateCompanyWalkthroughProgressRequest request = new UpdateCompanyWalkthroughProgressRequest();
        request.setVersion(VERSION);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(requestedCompany));
        when(profileService.getProfileWithCompany(PROFILE_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.updateProgress(COMPANY_ID, PROFILE_ID, request))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Not authorized to access this company");
    }

    private Company company(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        company.setName("Example Co");
        company.setEmailAddress(companyId + "@example.com");
        company.setPhoneNumber("+254720482575");
        return company;
    }

    private Profile profile(UUID profileId, Company company) {
        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setEmail(profileId + "@example.com");
        profile.setUsername(profileId.toString());
        profile.setRole("company_admin");
        profile.setCompany(company);
        return profile;
    }
}
