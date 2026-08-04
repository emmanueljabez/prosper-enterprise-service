package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for CompanyEmployeeWhitelist response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEmployeeWhitelistDto {

    private UUID id;
    private UUID companyId;
    private String companyName;
    private String email;
    private String firstName;
    private String lastName;
    private String department;
    private Boolean isUsed;
    private LocalDateTime usedAt;
    private UUID profileId;
    private Boolean isActive;
    private String notes;
    private String addedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
