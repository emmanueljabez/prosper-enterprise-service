package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ConsentRecord;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateParticipantConsentRequest {

    @NotNull
    private ConsentRecord.ConsentType consentType;

    @NotNull
    private ConsentRecord.ConsentStatus status;

    private LocalDateTime expiresAt;
}
