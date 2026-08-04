package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitParticipantPulseResponseRequest {

    @Min(value = 1, message = "confidenceScore must be between 1 and 5")
    @Max(value = 5, message = "confidenceScore must be between 1 and 5")
    private Integer confidenceScore;

    @Min(value = 1, message = "satisfactionScore must be between 1 and 5")
    @Max(value = 5, message = "satisfactionScore must be between 1 and 5")
    private Integer satisfactionScore;

    @Min(value = 1, message = "goalClarityScore must be between 1 and 5")
    @Max(value = 5, message = "goalClarityScore must be between 1 and 5")
    private Integer goalClarityScore;

    private String freeTextFeedback;
}
