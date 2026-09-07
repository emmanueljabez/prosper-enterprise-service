package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.SessionBreakoutParticipant;
import com.prosper.prospermentor.entity.SessionBreakoutRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class SessionBreakoutDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBreakoutRoomsRequest {
        private Integer count;
        private List<String> names;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveBreakoutParticipantRequest {
        private UUID roomId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionBreakoutParticipantDto {
        private UUID profileId;
        private UUID roomId;
        private String roomName;
        private String name;
        private String avatarUrl;
        private SessionBreakoutParticipant.AssignmentStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionBreakoutRoomDto {
        private UUID id;
        private UUID sessionId;
        private String name;
        private String agoraChannelName;
        private SessionBreakoutRoom.RoomStatus status;
        private List<SessionBreakoutParticipantDto> participants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionBreakoutStateDto {
        private boolean host;
        private List<SessionBreakoutRoomDto> rooms;
        private List<SessionBreakoutParticipantDto> availableParticipants;
        private SessionBreakoutRoomDto assignedRoom;
    }
}
