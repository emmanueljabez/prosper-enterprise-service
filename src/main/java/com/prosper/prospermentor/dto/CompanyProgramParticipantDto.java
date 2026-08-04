package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramParticipantDto {
    private UUID id;
    private UUID companyProgramId;
    private UUID profileId;
    private String profileName;
    private String profileEmail;
    private String profileRole;
    private String department;
    private CompanyProgramParticipant.ParticipantStatus status;
    private ParticipantConsentSummaryDto consentSummary;
    private MentorAssignmentSummaryDto mentorAssignment;
    private MatchWorkspaceSummaryDto matchWorkspace;
    private LocalDateTime enrolledAt;
    private UUID enrolledByUserId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
