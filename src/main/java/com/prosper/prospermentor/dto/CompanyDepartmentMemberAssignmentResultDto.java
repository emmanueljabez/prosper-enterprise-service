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
public class CompanyDepartmentMemberAssignmentResultDto {
    private UUID companyId;
    private UUID departmentId;
    private Integer assignedCount;
    private Integer skippedCount;
    private List<CompanyDepartmentMemberDto> members;
    private List<SkippedProfile> skippedProfiles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedProfile {
        private UUID profileId;
        private String reason;
    }
}
