package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * DTO for company-scoped employee session reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySessionDto {

    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeEmail;
    private String department;
    private UUID mentorId;
    private String mentorName;
    private String title;
    private String description;
    private String status;
    private String platform;
    private String platformDisplayName;
    private ZonedDateTime scheduledStart;
    private ZonedDateTime scheduledEnd;
    private Long durationMin;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private String cancelledBy;
    private Integer rating;
    private BigDecimal cost;
    private String currency;
    private Boolean paid;
}
