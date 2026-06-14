package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for adding a single employee to whitelist
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddEmployeeToWhitelistRequest {

    @Email(message = "Invalid email address")
    @NotBlank(message = "Email is required")
    private String emailAddress;
}
