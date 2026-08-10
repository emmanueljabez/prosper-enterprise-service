package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorInvitation;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyMentorInvitationRepository;
import com.prosper.prospermentor.repository.CompanyMentorPoolMembershipRepository;
import com.prosper.prospermentor.repository.CompanyMentorProgramScopeRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.CompanyMentorNotificationService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyMentorEnrollmentServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MENTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private CompanyMentorInvitationRepository invitationRepository;
    @Mock
    private CompanyMentorPoolMembershipRepository membershipRepository;
    @Mock
    private CompanyMentorProgramScopeRepository programScopeRepository;
    @Mock
    private CompanyProgramRepository companyProgramRepository;
    @Mock
    private CompanyMentorNotificationService notificationService;

    private CompanyMentorEnrollmentService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new CompanyMentorEnrollmentService(
                companyRepository,
                profileRepository,
                mentorProfileRepository,
                invitationRepository,
                membershipRepository,
                programScopeRepository,
                companyProgramRepository,
                notificationService
        );

        company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Nautix AI");
        company.setEmailAddress("hr@nautix.ai");
        company.setPhoneNumber("+254700000000");
    }

    @Test
    void inviteMentor_shouldRequireEmailAndPhoneAndSendBothChannels() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendMentorInvitation(eq(company), eq("mentor@example.com"), eq("+254720482575"), anyString(), any(LocalDateTime.class)))
                .thenReturn(CompanyMentorNotificationService.DeliveryAttemptResult.builder()
                        .emailSent(true)
                        .whatsappSent(true)
                        .build());

        CompanyMentorDtos.InvitationDto result = service.inviteMentor(
                COMPANY_ID,
                CompanyMentorDtos.InviteRequest.builder()
                        .email(" Mentor@Example.com ")
                        .phone("0720482575")
                        .firstName("Maya")
                        .lastName("Otieno")
                        .title("Engineering Lead")
                        .defaultVisibility(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE)
                        .build(),
                ADMIN_ID
        );

        assertThat(result.getEmail()).isEqualTo("mentor@example.com");
        assertThat(result.getPhone()).isEqualTo("+254720482575");
        assertThat(result.getStatus()).isEqualTo(CompanyMentorInvitation.InvitationStatus.SENT);
        assertThat(result.getEmailDeliveryStatus()).isEqualTo(CompanyMentorInvitation.DeliveryStatus.SENT);
        assertThat(result.getWhatsappDeliveryStatus()).isEqualTo(CompanyMentorInvitation.DeliveryStatus.SENT);

        ArgumentCaptor<CompanyMentorInvitation> invitationCaptor = ArgumentCaptor.forClass(CompanyMentorInvitation.class);
        verify(invitationRepository, times(2)).save(invitationCaptor.capture());
        CompanyMentorInvitation sentInvitation = invitationCaptor.getAllValues().get(1);
        assertThat(sentInvitation.getInvitationTokenHash()).hasSize(64);
        assertThat(sentInvitation.getInvitationTokenExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));

        verify(notificationService).sendMentorInvitation(
                eq(company),
                eq("mentor@example.com"),
                eq("+254720482575"),
                anyString(),
                any(LocalDateTime.class)
        );

        assertThatThrownBy(() -> service.inviteMentor(
                COMPANY_ID,
                CompanyMentorDtos.InviteRequest.builder().email("missing-phone@example.com").build(),
                ADMIN_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number is required");
    }

    @Test
    void validateImport_shouldReturnRowErrorsAndSaveNothingForInvalidRows() throws Exception {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        MockMultipartFile file = workbook(
                new String[]{"email", "phone", "visibility"},
                new String[][]{
                        {"mentor@example.com", "0720482575", "COMPANY_PRIVATE"},
                        {"mentor@example.com", "", "INVALID_VISIBILITY"}
                }
        );

        CompanyMentorDtos.ImportValidationResponse response = service.validateImport(COMPANY_ID, file);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getErrorRows()).isEqualTo(1);
        assertThat(response.getErrors())
                .extracting(CompanyMentorDtos.ImportRowError::getField)
                .contains("email", "phone", "visibility");
        verify(invitationRepository, never()).save(any());
        verify(notificationService, never()).sendMentorInvitation(any(), anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void importMentors_shouldSaveNothingWhenOneRowFailsValidation() throws Exception {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        MockMultipartFile file = workbook(
                new String[]{"email", "phone"},
                new String[][]{
                        {"mentor-one@example.com", "0720482575"},
                        {"mentor-two@example.com", ""}
                }
        );

        CompanyMentorDtos.ImportValidationResponse response = service.importMentors(COMPANY_ID, file, ADMIN_ID);

        assertThat(response.isValid()).isFalse();
        verify(invitationRepository, never()).save(any());
        verify(notificationService, never()).sendMentorInvitation(any(), anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void importMentors_shouldCreateAllInvitationsWhenRowsAreValid() throws Exception {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendMentorInvitation(eq(company), anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(CompanyMentorNotificationService.DeliveryAttemptResult.builder()
                        .emailSent(true)
                        .whatsappSent(true)
                        .build());

        MockMultipartFile file = workbook(
                new String[]{"email", "phone", "first_name", "last_name", "visibility"},
                new String[][]{
                        {"mentor-one@example.com", "0720482575", "Amina", "Achieng", "COMPANY_PRIVATE"},
                        {"mentor-two@example.com", "254711111111", "Sam", "Mwangi", "PUBLIC_REQUESTED"}
                }
        );

        CompanyMentorDtos.ImportValidationResponse response = service.importMentors(COMPANY_ID, file, ADMIN_ID);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getValidRows()).isEqualTo(2);
        verify(invitationRepository, times(4)).save(any(CompanyMentorInvitation.class));
        verify(notificationService, times(2)).sendMentorInvitation(eq(company), anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void acceptInvitation_shouldAttachExistingMentorAccountToCompanyPool() {
        CompanyMentorInvitation invitation = invitation("mentor@example.com", "+254720482575");
        Profile mentor = mentorProfile();
        MentorProfile mentorProfile = mentorDetails(true);

        when(invitationRepository.findByInvitationTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(profileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(mentorProfileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentorProfile));
        when(membershipRepository.findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(eq(COMPANY_ID), eq(MENTOR_ID), any()))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(CompanyMentorPoolMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMentorDtos.PoolMemberDto result = service.acceptInvitation("plain-token", MENTOR_ID);

        assertThat(result.getMentorProfileId()).isEqualTo(MENTOR_ID);
        assertThat(result.isProfileComplete()).isTrue();
        assertThat(result.isAvailabilityComplete()).isTrue();
        assertThat(result.isCompanyBookable()).isTrue();

        ArgumentCaptor<CompanyMentorPoolMembership> membershipCaptor = ArgumentCaptor.forClass(CompanyMentorPoolMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getPublicListingPreexisting()).isTrue();
        assertThat(invitation.getStatus()).isEqualTo(CompanyMentorInvitation.InvitationStatus.ACCEPTED);
        assertThat(invitation.getInvitationTokenHash()).isNull();
    }

    @Test
    void acceptInvitation_shouldCreateMembershipForNewMentorAfterSignup() {
        CompanyMentorInvitation invitation = invitation("mentor@example.com", "+254720482575");
        Profile mentor = mentorProfile();
        mentor.setIsVerified(false);
        MentorProfile mentorProfile = mentorDetails(true);

        when(invitationRepository.findByInvitationTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(profileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(mentorProfileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentorProfile));
        when(membershipRepository.findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(eq(COMPANY_ID), eq(MENTOR_ID), any()))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(CompanyMentorPoolMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMentorDtos.PoolMemberDto result = service.acceptInvitation("plain-token", MENTOR_ID);

        assertThat(result.getMembershipStatus()).isEqualTo(CompanyMentorPoolMembership.MembershipStatus.ACTIVE);
        assertThat(result.isPublicListingPreexisting()).isFalse();
        assertThat(invitation.getAcceptedProfile()).isSameAs(mentor);
    }

    @Test
    void resendInvitation_shouldReplaceTokenAndAttemptBothChannels() {
        UUID invitationId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        CompanyMentorInvitation invitation = invitation("mentor@example.com", "+254720482575");
        invitation.setId(invitationId);
        invitation.setInvitationTokenHash("old-token-hash");

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendMentorInvitation(eq(company), eq("mentor@example.com"), eq("+254720482575"), anyString(), any(LocalDateTime.class)))
                .thenReturn(CompanyMentorNotificationService.DeliveryAttemptResult.builder()
                        .emailSent(false)
                        .whatsappSent(true)
                        .build());

        CompanyMentorDtos.InvitationDto result = service.resendInvitation(COMPANY_ID, invitationId);

        assertThat(result.getStatus()).isEqualTo(CompanyMentorInvitation.InvitationStatus.SENT);
        assertThat(result.getEmailDeliveryStatus()).isEqualTo(CompanyMentorInvitation.DeliveryStatus.FAILED);
        assertThat(result.getWhatsappDeliveryStatus()).isEqualTo(CompanyMentorInvitation.DeliveryStatus.SENT);
        assertThat(invitation.getInvitationTokenHash()).isNotEqualTo("old-token-hash");
        assertThat(invitation.getInvitationTokenHash()).hasSize(64);
        assertThat(invitation.getLastSentAt()).isNotNull();
    }

    @Test
    void companyBookable_shouldBeFalseUntilProfileAndAvailabilityAreComplete() {
        CompanyMentorInvitation invitation = invitation("mentor@example.com", "+254720482575");
        Profile mentor = mentorProfile();
        MentorProfile mentorProfile = mentorDetails(false);

        when(invitationRepository.findByInvitationTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(profileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(mentorProfileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentorProfile));
        when(membershipRepository.findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(eq(COMPANY_ID), eq(MENTOR_ID), any()))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(CompanyMentorPoolMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(CompanyMentorInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMentorDtos.PoolMemberDto result = service.acceptInvitation("plain-token", MENTOR_ID);

        assertThat(result.isProfileComplete()).isTrue();
        assertThat(result.isAvailabilityComplete()).isFalse();
        assertThat(result.isCompanyBookable()).isFalse();
    }

    @Test
    void getMentorPool_shouldSearchMentorsWhenOptionalTitleIsMissing() {
        CompanyMentorInvitation invitation = invitation("mentor@example.com", "+254720482575");
        invitation.setTitle(null);
        CompanyMentorPoolMembership membership = membership(invitation, mentorProfile());

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(invitationRepository.findByCompany_Id(eq(COMPANY_ID), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(invitation)));
        when(membershipRepository.findByCompany_IdAndMembershipStatusIn(eq(COMPANY_ID), any()))
                .thenReturn(List.of(membership));

        CompanyMentorDtos.MentorPoolResponse response = service.getMentorPool(COMPANY_ID, 0, 20, "mentor@example.com");

        assertThat(response.getMembers()).hasSize(1);
        assertThat(response.getMembers().get(0).getMentorEmail()).isEqualTo("mentor@example.com");
        assertThat(response.getMetrics().getTotalCompanyMentors()).isEqualTo(1);
    }

    private CompanyMentorInvitation invitation(String email, String phone) {
        CompanyMentorInvitation invitation = new CompanyMentorInvitation();
        invitation.setId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        invitation.setCompany(company);
        invitation.setEmail(email);
        invitation.setPhone(phone);
        invitation.setDefaultVisibility(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE);
        invitation.setStatus(CompanyMentorInvitation.InvitationStatus.SENT);
        invitation.setInvitationTokenHash(sha256Hex("plain-token"));
        invitation.setInvitationTokenExpiresAt(LocalDateTime.now().plusDays(3));
        return invitation;
    }

    private CompanyMentorPoolMembership membership(CompanyMentorInvitation invitation, Profile mentor) {
        CompanyMentorPoolMembership membership = new CompanyMentorPoolMembership();
        membership.setId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        membership.setCompany(company);
        membership.setMentorProfile(mentor);
        membership.setSourceInvitation(invitation);
        membership.setVisibilityMode(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE);
        membership.setMembershipStatus(CompanyMentorPoolMembership.MembershipStatus.ACTIVE);
        membership.setProfileComplete(true);
        membership.setAvailabilityComplete(true);
        membership.setCompanyBookable(true);
        membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.NOT_REQUESTED);
        membership.setPublicListingPreexisting(false);
        return membership;
    }

    private Profile mentorProfile() {
        Profile mentor = new Profile();
        mentor.setId(MENTOR_ID);
        mentor.setEmail("mentor@example.com");
        mentor.setRole("mentor");
        mentor.setFirstName("Maya");
        mentor.setLastName("Otieno");
        mentor.setPhone("+254720482575");
        mentor.setIsVerified(true);
        return mentor;
    }

    private MentorProfile mentorDetails(boolean available) {
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(MENTOR_ID);
        mentorProfile.setTitle("Engineering Lead");
        mentorProfile.setIsAvailable(available);
        return mentorProfile;
    }

    private MockMultipartFile workbook(String[] headers, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Mentors");
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "company-mentors.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()
            );
        }
    }

    private String sha256Hex(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
