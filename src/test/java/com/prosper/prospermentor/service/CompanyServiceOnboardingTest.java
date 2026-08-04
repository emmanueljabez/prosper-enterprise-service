package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyOnboardingStatusDto;
import com.prosper.prospermentor.dto.UpdateCompanyOnboardingRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyEmployeeWhitelistRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ProgramRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.service.notification.CompanyNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceOnboardingTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private CompanyNotificationService companyNotificationService;
    @Mock
    private CompanyEmployeeWhitelistRepository whitelistRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void getCompanyOnboardingStatus_shouldReportMissingRequiredFieldsBeforeActivation() {
        UUID companyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Company company = new Company();
        company.setId(companyId);
        company.setName("Example Co");
        company.setEmailAddress("admin@example.com");
        company.setPhoneNumber("0720482575");

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        ApiResponse<CompanyOnboardingStatusDto> response = companyService.getCompanyOnboardingStatus(companyId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isCompleted()).isFalse();
        assertThat(response.getData().getMissingFields()).containsExactlyInAnyOrder(
                "industry",
                "companySizeBand",
                "country",
                "timezone"
        );
    }

    @Test
    void updateCompanyOnboarding_shouldPersistRequiredFieldsAndMarkOnboardingCompleted() {
        UUID companyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        Company company = new Company();
        company.setId(companyId);
        company.setName("Example Co");
        company.setEmailAddress("admin@example.com");
        company.setPhoneNumber("0720482575");

        UpdateCompanyOnboardingRequest request = new UpdateCompanyOnboardingRequest();
        request.setIndustry("Aviation");
        request.setCompanySizeBand("1001-5000");
        request.setCountry("Kenya");
        request.setTimezone("Africa/Nairobi");

        when(companyRepository.findByIdWithRecommendedPrograms(companyId)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<CompanyOnboardingStatusDto> response = companyService.updateCompanyOnboarding(companyId, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isCompleted()).isTrue();
        assertThat(response.getData().getMissingFields()).isEmpty();
        assertThat(company.getIndustry()).isEqualTo("Aviation");
        assertThat(company.getCompanySizeBand()).isEqualTo("1001-5000");
        assertThat(company.getTimezone()).isEqualTo("Africa/Nairobi");
        assertThat(company.getMentorshipObjective()).isNull();
        assertThat(company.getTargetAudienceDescription()).isNull();
        assertThat(company.getProgramDesignPreference()).isNull();
        assertThat(company.getOnboardingCompleted()).isTrue();
        assertThat(company.getOnboardingCompletedAt()).isNotNull();
        assertThat(company.getRecommendedPrograms()).isEmpty();
    }
}
