package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateCommonInterestCircleRequest {
    @NotBlank
    private String name;
    private String theme;
    private List<String> interestTags;
    private UUID facilitatorProfileId;
    private Integer minSize;
    private Integer maxSize;
    private LocalDateTime nextSessionAt;
}
