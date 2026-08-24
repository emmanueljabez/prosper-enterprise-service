package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CommonInterestCircle;
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
public class CommonInterestCircleDto {
    private UUID id;
    private UUID cohortId;
    private String name;
    private String theme;
    private List<String> interestTags;
    private UUID facilitatorProfileId;
    private String facilitatorName;
    private Integer minSize;
    private Integer maxSize;
    private CommonInterestCircle.CircleStatus status;
    private LocalDateTime nextSessionAt;
    private long memberCount;
    private List<CommonInterestCircleMemberDto> members;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
