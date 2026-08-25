package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyChapterRequest {
    private String name;
    private String code;
    private String description;
    private UUID regionId;
    private Boolean isActive;
}
