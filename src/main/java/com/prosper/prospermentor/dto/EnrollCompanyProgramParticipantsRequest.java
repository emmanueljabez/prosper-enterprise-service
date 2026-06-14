package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollCompanyProgramParticipantsRequest {

    @NotEmpty(message = "At least one profileId is required")
    private List<@NotNull(message = "profileId cannot be null") UUID> profileIds;
}
