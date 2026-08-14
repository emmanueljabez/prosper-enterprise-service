package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyWalkthroughProgressDto {
    private UUID companyId;
    private UUID profileId;
    private String version;
    private boolean introDismissed;
    @Builder.Default
    private List<String> completedTaskIds = new ArrayList<>();
    @Builder.Default
    private List<String> completedTourIds = new ArrayList<>();
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
