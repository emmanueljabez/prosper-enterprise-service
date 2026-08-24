package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CommonInterestCircleService;
import com.prosper.prospermentor.service.CompanyProgramCohortIntakeService;
import com.prosper.prospermentor.service.CompanyProgramCohortService;
import com.prosper.prospermentor.service.CompanyProgramService;
import com.prosper.prospermentor.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramCohortControllerTest {

    @Mock
    private CompanyProgramCohortService cohortService;
    @Mock
    private CompanyProgramCohortIntakeService intakeService;
    @Mock
    private CommonInterestCircleService circleService;
    @Mock
    private CompanyProgramService companyProgramService;
    @Mock
    private ProfileService profileService;

    @Test
    void recordPlenaryAttendance_shouldRejectCompanyAdminOutsideParticipantCompanyBeforeMutation() {
        UUID participantId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID participantCompanyId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID requesterCompanyId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID requesterUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CompanyProgramCohortController controller = new CompanyProgramCohortController(
                cohortService,
                intakeService,
                circleService,
                companyProgramService,
                profileService
        );

        when(intakeService.getParticipantCompanyId(participantId)).thenReturn(participantCompanyId);
        when(profileService.getProfileWithCompany(requesterUserId)).thenReturn(Optional.of(profileWithCompany(requesterCompanyId)));

        ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> response = controller.recordPlenaryAttendance(
                participantId,
                null,
                authentication(requesterUserId, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(intakeService, never()).recordPlenaryAttendance(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private TestingAuthenticationToken authentication(UUID userId, String role) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "admin@example.com", role),
                null
        );
        authentication.setAuthenticated(true);
        return authentication;
    }

    private Profile profileWithCompany(UUID companyId) {
        Company company = new Company();
        company.setId(companyId);

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setCompany(company);
        return profile;
    }
}
