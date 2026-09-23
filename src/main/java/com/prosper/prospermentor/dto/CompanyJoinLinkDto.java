package com.prosper.prospermentor.dto;

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
public class CompanyJoinLinkDto {
    private UUID companyId;
    private String companyName;
    private String joinToken;
    private String joinUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;
    private boolean linked;
}
