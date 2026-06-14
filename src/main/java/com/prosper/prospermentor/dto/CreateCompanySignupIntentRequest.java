package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCompanySignupIntentRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    @Email
    private String workEmail;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private UUID planId;

    @Min(1)
    private Integer sessionCount;
}
