package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ParticipantPulse;
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
public class ParticipantPulseDto {
    private UUID id;
    private UUID participantId;
    private UUID companyProgramId;
    private String companyProgramName;
    private UUID sessionId;
    private ParticipantPulse.PulseType pulseType;
    private ParticipantPulse.PulseStatus status;
    private Integer confidenceScore;
    private Integer satisfactionScore;
    private Integer goalClarityScore;
    private String freeTextFeedback;
    private LocalDateTime sentAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
