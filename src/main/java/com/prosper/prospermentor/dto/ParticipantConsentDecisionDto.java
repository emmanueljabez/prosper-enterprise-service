package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ConsentRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantConsentDecisionDto {
    private ConsentRecord.ConsentType consentType;
    private ConsentRecord.ConsentStatus status;
    private LocalDateTime capturedAt;
    private LocalDateTime expiresAt;
    private boolean pending;
}
