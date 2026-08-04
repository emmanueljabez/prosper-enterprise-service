package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceRegistrationNotificationTest {

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
    void completeCompanyRegistrationWithProfile_shouldAllowSuppressingRegistrationCompletedEmail() {
        UUID companyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Company company = new Company();
        company.setId(companyId);
        company.setName("Nautix AI");
        company.setEmailAddress("info@nautix.io");
        company.setPhoneNumber("0720482575");
        company.setRegistrationToken("registration-token");
        company.setRegistrationTokenExpiry(LocalDateTime.now().plusDays(1));
        company.setRegistrationCompleted(false);

        Profile profile = new Profile();
        profile.setId(userId);
        profile.setUsername("info");

        when(companyRepository.findAll()).thenReturn(java.util.List.of(company));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileService.getCompleteProfile(userId)).thenReturn(Optional.of(Map.of(
                "id", userId,
                "email", "info@nautix.io",
                "role", "company"
        )));

        ApiResponse<Map<String, Object>> response = companyService.completeCompanyRegistrationWithProfile(
                "registration-token",
                userId,
                "info@nautix.io",
                "Info",
                "Nautix",
                "0720482575",
                null,
                false
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(company.getRegistrationCompleted()).isTrue();
        verify(companyNotificationService, never()).sendRegistrationCompletedEmail(any(Company.class));
    }
}
