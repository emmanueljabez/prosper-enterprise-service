package com.prosper.prospermentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCohortRosterParticipantsRequest {

    @NotEmpty(message = "At least one roster participant is required")
    private List<@Valid CohortRosterParticipantRequest> participants;
}
