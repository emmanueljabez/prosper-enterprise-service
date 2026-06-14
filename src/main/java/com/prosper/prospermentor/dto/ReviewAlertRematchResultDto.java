package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAlertRematchResultDto {

    private UUID alertId;
    private UUID companyProgramId;
    private UUID participantId;
    private UUID previousMentorId;
    private String previousMentorName;
    private int resolvedAlertCount;
    private boolean mentorAssignmentRemoved;
}
