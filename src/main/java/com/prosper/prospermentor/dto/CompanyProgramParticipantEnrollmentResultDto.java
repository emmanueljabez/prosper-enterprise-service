package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramParticipantEnrollmentResultDto {
    private UUID companyProgramId;
    private int enrolledCount;
    private int skippedCount;
    private long totalParticipants;
    private List<CompanyProgramParticipantDto> enrolledParticipants;
    private List<SkippedParticipantDto> skippedParticipants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedParticipantDto {
        private UUID profileId;
        private String reason;
    }
}
