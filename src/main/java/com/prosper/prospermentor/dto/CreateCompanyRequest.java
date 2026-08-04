package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating a new company
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email address")
    private String emailAddress;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private String logoUrl;
    private String website;
    private String description;
    private String address;
    private String city;
    private String country;
    private String industry;
    private String companySizeBand;
    private String timezone;
    private String mentorshipObjective;
    private String targetAudienceDescription;
    private String programDesignPreference;
    private String primaryColor;
    private String secondaryColor;
}
