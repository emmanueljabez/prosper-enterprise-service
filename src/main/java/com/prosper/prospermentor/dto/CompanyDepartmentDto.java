package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyDepartment;
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
public class CompanyDepartmentDto {
    private UUID id;
    private UUID companyId;
    private String name;
    private String code;
    private String description;
    private CompanyDepartment.DepartmentStatus status;
    private Boolean isActive;
    private Long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
