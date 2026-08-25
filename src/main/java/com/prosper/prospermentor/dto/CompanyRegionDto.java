package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyLocationStatus;
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
public class CompanyRegionDto {
    private UUID id;
    private UUID companyId;
    private String name;
    private String code;
    private String description;
    private CompanyLocationStatus status;
    private boolean isActive;
    private long chapterCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
