package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyMentorInvitation;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class CompanyMentorDtos {
    private CompanyMentorDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String phone;
        private String firstName;
        private String lastName;
        private String title;
        private String department;
        private List<String> tags;
        private CompanyMentorPoolMembership.VisibilityMode defaultVisibility;
        private List<UUID> companyProgramIds;
        private String cohortReference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportValidationResponse {
        private boolean valid;
        private int totalRows;
        private int validRows;
        private int errorRows;
        private List<ImportRowResult> rows;
        private List<ImportRowError> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowResult {
        private int rowNumber;
        private String email;
        private String phone;
        private String firstName;
        private String lastName;
        private String title;
        private String department;
        private List<String> tags;
        private CompanyMentorPoolMembership.VisibilityMode visibility;
        private String programOrCohortReference;
        private boolean existingProsperMentor;
        private List<ImportRowError> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowError {
        private int rowNumber;
        private String field;
        private String value;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvitationDto {
        private UUID id;
        private UUID companyId;
        private String companyName;
        private String email;
        private String phone;
        private String firstName;
        private String lastName;
        private String title;
        private String department;
        private List<String> tags;
        private CompanyMentorPoolMembership.VisibilityMode defaultVisibility;
        private String programOrCohortReference;
        private CompanyMentorInvitation.InvitationStatus status;
        private CompanyMentorInvitation.DeliveryStatus emailDeliveryStatus;
        private CompanyMentorInvitation.DeliveryStatus whatsappDeliveryStatus;
        private UUID acceptedProfileId;
        private LocalDateTime acceptedAt;
        private LocalDateTime lastSentAt;
        private LocalDateTime invitationTokenExpiresAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoolMemberDto {
        private UUID id;
        private UUID companyId;
        private UUID mentorProfileId;
        private UUID sourceInvitationId;
        private String mentorName;
        private String mentorEmail;
        private String phone;
        private String title;
        private String department;
        private List<String> tags;
        private CompanyMentorPoolMembership.VisibilityMode visibilityMode;
        private CompanyMentorPoolMembership.MembershipStatus membershipStatus;
        private boolean profileComplete;
        private boolean availabilityComplete;
        private boolean companyBookable;
        private CompanyMentorPoolMembership.PublicApprovalStatus publicApprovalStatus;
        private boolean publicListingPreexisting;
        private List<ProgramScopeDto> programScopes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramScopeDto {
        private UUID id;
        private UUID companyProgramId;
        private String companyProgramName;
        private UUID cohortId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorPoolResponse {
        private List<InvitationDto> invitations;
        private List<PoolMemberDto> members;
        private MentorPoolMetrics metrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorPoolMetrics {
        private long totalCompanyMentors;
        private long pendingInvites;
        private long acceptedIncompleteProfile;
        private long profileCompleteNoAvailability;
        private long companyBookable;
        private long publicRequested;
        private long publicApproved;
        private long failedEmailDeliveries;
        private long failedWhatsappDeliveries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyInviteResponse {
        private String email;
        private String phone;
        private String firstName;
        private String lastName;
        private String title;
        private String department;
        private List<String> tags;
        private UUID companyId;
        private String companyName;
        private CompanyMentorPoolMembership.VisibilityMode defaultVisibility;
        private boolean existingProsperMentor;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcceptInviteRequest {
        @NotBlank
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisibilityUpdateRequest {
        private CompanyMentorPoolMembership.VisibilityMode visibilityMode;
        private List<UUID> companyProgramIds;
        private List<UUID> cohortIds;
    }
}
