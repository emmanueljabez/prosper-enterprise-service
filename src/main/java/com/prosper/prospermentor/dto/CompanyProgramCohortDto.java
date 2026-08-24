package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohort;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohortDto {
    private UUID id;
    private UUID companyProgramId;
    private String companyProgramName;
    private UUID companyId;
    private String companyName;
    private String name;
    private String code;
    private String chapter;
    private String region;
    private CompanyProgramCohort.CohortStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private boolean selfJoinEnabled;
    private LocalDateTime selfJoinExpiresAt;
    private Integer selfJoinCapacity;
    private Integer circleMinSize;
    private Integer circleMaxSize;
    private List<String> interestTagSet;
    private CompanyProgramCohort.PlenaryEventType plenaryEventType;
    private String plenaryEventId;
    private Boolean matchingStartsAfterCirclesFinalized;
    private UUID createdByUserId;
    private long participantCount;
    private long pendingCount;
    private long confirmedCount;
    private long circleCount;
    private long unplacedCount;
    private long matchedCount;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
