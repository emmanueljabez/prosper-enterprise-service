package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramMentorCandidateDto {
    private UUID mentorId;
    private String mentorName;
    private String mentorEmail;
    private String title;
    private String company;
    private Integer yearsExperience;
    private BigDecimal rating;
    private Integer totalSessions;
    private String avatarUrl;
    private List<String> specializations;
    private Boolean isAvailable;
    private String source;
    private Integer rankOrder;
    private BigDecimal recommendationScore;
    private String recommendationReason;
}
