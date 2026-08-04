package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorInvitation;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyMentorEnrollmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyMentorEnrollmentControllerTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MENTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID INVITATION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private CompanyMentorEnrollmentService companyMentorEnrollmentService;
    @Mock
    private ProfileService profileService;

    @InjectMocks
    private CompanyMentorEnrollmentController controller;

    @Test
    void verify_shouldReturnInviteContextWithoutAuthentication() {
        CompanyMentorDtos.VerifyInviteResponse inviteContext = CompanyMentorDtos.VerifyInviteResponse.builder()
                .email("mentor@example.com")
                .companyName("Nautix AI")
                .defaultVisibility(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE)
                .build();
        when(companyMentorEnrollmentService.verifyInvitation("plain-token")).thenReturn(inviteContext);

        ResponseEntity<ApiResponse<CompanyMentorDtos.VerifyInviteResponse>> response = controller.verify("plain-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(inviteContext);
    }

    @Test
    void accept_shouldUseAuthenticatedMentorProfileId() {
        CompanyMentorDtos.PoolMemberDto accepted = CompanyMentorDtos.PoolMemberDto.builder()
                .mentorProfileId(MENTOR_ID)
                .membershipStatus(CompanyMentorPoolMembership.MembershipStatus.ACTIVE)
                .build();
        when(companyMentorEnrollmentService.acceptInvitation("plain-token", MENTOR_ID)).thenReturn(accepted);

        ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> response = controller.accept(
                CompanyMentorDtos.AcceptInviteRequest.builder().token("plain-token").build(),
                authentication(MENTOR_ID, "MENTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(accepted);
        verify(companyMentorEnrollmentService).acceptInvitation("plain-token", MENTOR_ID);
    }

    @Test
    void accept_shouldRejectUnauthenticatedRequests() {
        ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> response = controller.accept(
                CompanyMentorDtos.AcceptInviteRequest.builder().token("plain-token").build(),
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(companyMentorEnrollmentService, never()).acceptInvitation(any(), any());
    }

    @Test
    void invite_shouldRejectNonCompanyAdmins() {
        CompanyMentorDtos.InviteRequest request = CompanyMentorDtos.InviteRequest.builder()
                .email("mentor@example.com")
                .phone("+254720482575")
                .build();

        ResponseEntity<ApiResponse<CompanyMentorDtos.InvitationDto>> response = controller.invite(
                COMPANY_ID,
                request,
                authentication(ADMIN_ID, "MENTEE")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(companyMentorEnrollmentService, never()).inviteMentor(any(), any(), any());
    }

    @Test
    void importMentors_shouldAcceptOnlyXlsxFiles() {
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile()));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "mentors.csv",
                "text/csv",
                "email,phone\nmentor@example.com,+254720482575".getBytes()
        );

        ResponseEntity<ApiResponse<CompanyMentorDtos.ImportValidationResponse>> response = controller.importMentors(
                COMPANY_ID,
                file,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Mentor import must be an .xlsx file");
        verify(companyMentorEnrollmentService, never()).importMentors(any(), any(), any());
    }

    @Test
    void resend_shouldReturnBadRequestForAcceptedInvites() {
        when(profileService.getProfileWithCompany(ADMIN_ID)).thenReturn(Optional.of(companyAdminProfile()));
        when(companyMentorEnrollmentService.resendInvitation(COMPANY_ID, INVITATION_ID))
                .thenThrow(new IllegalArgumentException("Invitation has already been accepted"));

        ResponseEntity<ApiResponse<CompanyMentorDtos.InvitationDto>> response = controller.resend(
                COMPANY_ID,
                INVITATION_ID,
                authentication(ADMIN_ID, "COMPANY_ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invitation has already been accepted");
    }

    private Profile companyAdminProfile() {
        Company company = new Company();
        company.setId(COMPANY_ID);
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
