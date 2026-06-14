package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessAuditSummaryDto {
    private UUID companyId;
    private long totalEvents;
    private long last7DaysEvents;
    private long distinctActorsLast7Days;
    private long participantRosterViewsLast7Days;
    private long participantConsentViewsLast7Days;
    private long participantConsentUpdatesLast7Days;
    private long reviewAlertViewsLast7Days;
    private long rematchActionsLast7Days;
    private List<AccessAuditLogDto> recentLogs;
}
