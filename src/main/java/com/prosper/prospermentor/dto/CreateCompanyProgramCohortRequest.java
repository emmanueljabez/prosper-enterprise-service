package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohort;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyProgramCohortRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String chapter;
    private String region;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean selfJoinEnabled;
    private LocalDateTime selfJoinExpiresAt;
    private Integer selfJoinCapacity;
    private Integer circleMinSize;
    private Integer circleMaxSize;
    private List<String> interestTagSet;
    private CompanyProgramCohort.PlenaryEventType plenaryEventType;
    private String plenaryEventId;
    private Boolean matchingStartsAfterCirclesFinalized;
}
