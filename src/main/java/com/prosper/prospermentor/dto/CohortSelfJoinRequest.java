package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohortSelfJoinRequest {
    @Email
    @NotBlank
    private String email;
    private String phone;
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    private String chapter;
    private String region;
    private List<String> interestTags;
}
