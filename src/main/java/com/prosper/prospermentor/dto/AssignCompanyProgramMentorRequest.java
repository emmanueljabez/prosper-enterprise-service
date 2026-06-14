package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignCompanyProgramMentorRequest {

    @NotNull(message = "mentorId is required")
    private UUID mentorId;

    private UUID journeyInstanceStepId;
}
