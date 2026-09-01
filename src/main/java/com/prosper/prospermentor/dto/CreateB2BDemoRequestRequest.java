package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateB2BDemoRequestRequest {

    @NotBlank
    @Size(max = 160)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 254)
    private String workEmail;

    @NotBlank
    @Size(max = 200)
    private String organisation;

    @Size(max = 60)
    private String phoneNumber;

    @Size(max = 80)
    private String partnershipType;

    @Size(max = 80)
    private String cohortSize;

    @Size(max = 120)
    private String timeline;

    @Size(max = 5000)
    private String details;

    @Size(max = 120)
    private String sourcePage;
}
