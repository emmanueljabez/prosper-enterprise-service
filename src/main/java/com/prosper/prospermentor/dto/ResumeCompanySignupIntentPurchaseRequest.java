package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeCompanySignupIntentPurchaseRequest {

    @NotBlank
    private String redirectSuccessUrl;

    @NotBlank
    private String redirectCancelUrl;

    @Min(1)
    private Integer sessionCount;
}
