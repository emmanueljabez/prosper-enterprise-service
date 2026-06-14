package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ConsentRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantConsentSummaryDto {
    private UUID participantId;
    private ConsentRecord.ConsentStatus programParticipationStatus;
    private ConsentRecord.ConsentStatus aggregatedAnalyticsStatus;
    private ConsentRecord.ConsentStatus employerProgressVisibilityStatus;
    private int grantedCount;
    private int revokedCount;
    private int pendingCount;
    private boolean programParticipationGranted;
    private boolean aggregatedAnalyticsGranted;
    private boolean employerProgressVisibilityGranted;
}
