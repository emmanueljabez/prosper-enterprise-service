package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for updating a company
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyRequest {

    private String name;

    @Email(message = "Invalid email address")
    private String emailAddress;

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

    private Boolean isActive;
}
