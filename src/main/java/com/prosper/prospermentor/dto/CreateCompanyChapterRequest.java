package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyChapterRequest {
    @NotBlank
    private String name;
    private String code;
    private String description;
    private UUID regionId;
}
