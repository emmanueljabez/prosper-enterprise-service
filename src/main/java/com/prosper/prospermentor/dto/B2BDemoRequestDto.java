package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2BDemoRequestDto {
    private UUID id;
    private String fullName;
    private String workEmail;
    private String organisation;
    private String phoneNumber;
    private String partnershipType;
    private String cohortSize;
    private String timeline;
    private String details;
    private String status;
    private String sourcePage;
    private Instant createdAt;
    private Instant updatedAt;
}
