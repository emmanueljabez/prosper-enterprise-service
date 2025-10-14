package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.Session;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequestDto {

    @NotNull(message = "Mentor ID is required")
    private String mentorId;

    @NotNull(message = "Mentee ID is required")
    private String menteeId;

    @NotNull(message = "Skill ID is required")
    private String skillId;

    @NotNull(message = "Scheduled start time is required")
    private ZonedDateTime scheduledStart;

    @NotNull(message = "Meeting platform is required")
    private Session.MeetingPlatform meetingPlatform;

    private String menteeMessage;
}
