package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmEmailRequest {
    @NotBlank(message = "Token hash is required")
    private String tokenHash;

    private String type = "signup";
}
