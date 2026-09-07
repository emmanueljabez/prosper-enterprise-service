package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.dto.SessionBreakoutDtos;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionBreakoutParticipant;
import com.prosper.prospermentor.entity.SessionBreakoutRoom;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.SessionBreakoutParticipantRepository;
import com.prosper.prospermentor.repository.SessionBreakoutRoomRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionBreakoutServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROGRAM_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROOM_TWO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MENTOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PARTICIPANT_ONE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PARTICIPANT_TWO_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID SKILL_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T09:55:00Z"), ZoneId.of("UTC"));

    private SessionRepository sessionRepository;
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private SessionBreakoutRoomRepository roomRepository;
    private SessionBreakoutParticipantRepository participantRepository;
    private AgoraTokenService agoraTokenService;
    private SessionBreakoutService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        companyProgramParticipantRepository = mock(CompanyProgramParticipantRepository.class);
        roomRepository = mock(SessionBreakoutRoomRepository.class);
        participantRepository = mock(SessionBreakoutParticipantRepository.class);
        agoraTokenService = mock(AgoraTokenService.class);
        service = new SessionBreakoutService(
                sessionRepository,
                companyProgramParticipantRepository,
                roomRepository,
                participantRepository,
                agoraTokenService,
                CLOCK
        );
    }

    @Test
    void createRooms_shouldAllowSessionMentorForCorporateAgoraSession() {
        List<SessionBreakoutRoom> savedRooms = new ArrayList<>();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        when(roomRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenAnswer(invocation -> savedRooms);
        when(roomRepository.save(any(SessionBreakoutRoom.class))).thenAnswer(invocation -> {
            SessionBreakoutRoom room = invocation.getArgument(0);
            assertThat(room.getId()).as("new breakout rooms should rely on JPA/database UUID generation").isNull();
            room.setId(savedRooms.isEmpty() ? ROOM_ID : ROOM_TWO_ID);
            savedRooms.add(room);
            return room;
        });
        when(participantRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                eq(PROGRAM_ID),
                org.mockito.ArgumentMatchers.<Collection<CompanyProgramParticipant.ParticipantStatus>>any()))
                .thenReturn(List.of(programParticipant(PARTICIPANT_ONE_ID, "Faith", "Wainaina")));
        SessionBreakoutDtos.CreateBreakoutRoomsRequest request = new SessionBreakoutDtos.CreateBreakoutRoomsRequest();
        request.setCount(2);

        SessionBreakoutDtos.SessionBreakoutStateDto state = service.createRooms(SESSION_ID, MENTOR_ID, request);

        assertThat(state.isHost()).isTrue();
        assertThat(state.getRooms()).extracting(SessionBreakoutDtos.SessionBreakoutRoomDto::getName)
                .containsExactly("Room 1", "Room 2");
        assertThat(state.getRooms()).allMatch(room -> room.getAgoraChannelName().startsWith("pm-session-" + SESSION_ID + "-breakout-"));
    }

    @Test
    void autoAssign_shouldSaveNewAssignmentsWithoutPreassignedIds() {
        List<SessionBreakoutRoom> rooms = List.of(
                draftRoom(ROOM_ID, "Room 1"),
                draftRoom(ROOM_TWO_ID, "Room 2")
        );
        List<SessionBreakoutParticipant> savedAssignments = new ArrayList<>();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        when(roomRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(rooms);
        when(participantRepository.findBySessionId(SESSION_ID)).thenAnswer(invocation -> savedAssignments);
        when(participantRepository.save(any(SessionBreakoutParticipant.class))).thenAnswer(invocation -> {
            SessionBreakoutParticipant assignment = invocation.getArgument(0);
            assertThat(assignment.getId()).as("new breakout assignments should rely on JPA/database UUID generation").isNull();
            assignment.setId(UUID.randomUUID());
            savedAssignments.add(assignment);
            return assignment;
        });
        when(companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                eq(PROGRAM_ID),
                org.mockito.ArgumentMatchers.<Collection<CompanyProgramParticipant.ParticipantStatus>>any()))
                .thenReturn(List.of(
                        programParticipant(PARTICIPANT_ONE_ID, "Faith", "Wainaina"),
                        programParticipant(PARTICIPANT_TWO_ID, "Marcus", "Chen")
                ));

        SessionBreakoutDtos.SessionBreakoutStateDto state = service.autoAssign(SESSION_ID, MENTOR_ID);

        assertThat(savedAssignments).hasSize(2);
        assertThat(state.getRooms()).extracting(room -> room.getParticipants().size())
                .containsExactly(1, 1);
    }

    @Test
    void createRooms_shouldRejectNonHost() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        SessionBreakoutDtos.CreateBreakoutRoomsRequest request = new SessionBreakoutDtos.CreateBreakoutRoomsRequest();
        request.setCount(1);

        assertThatThrownBy(() -> service.createRooms(SESSION_ID, PARTICIPANT_ONE_ID, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only the session host can manage breakout rooms");
    }

    @Test
    void createRoomToken_shouldAllowAssignedParticipantForOpenRoom() {
        SessionBreakoutRoom room = openRoom();
        SessionBreakoutParticipant assignment = assignment(room, PARTICIPANT_ONE_ID, SessionBreakoutParticipant.AssignmentStatus.ASSIGNED);
        AgoraTokenService.AgoraJoinToken expected = new AgoraTokenService.AgoraJoinToken(
                "app-id",
                room.getAgoraChannelName(),
                PARTICIPANT_ONE_ID.toString(),
                "token",
                Instant.parse("2026-09-07T10:55:00Z")
        );
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        when(roomRepository.findByIdAndSessionId(ROOM_ID, SESSION_ID)).thenReturn(Optional.of(room));
        when(participantRepository.findBySessionIdAndProfileIdAndStatusIn(
                eq(SESSION_ID),
                eq(PARTICIPANT_ONE_ID),
                org.mockito.ArgumentMatchers.<Collection<SessionBreakoutParticipant.AssignmentStatus>>any()))
                .thenReturn(Optional.of(assignment));
        when(agoraTokenService.createJoinToken(room.getAgoraChannelName(), PARTICIPANT_ONE_ID)).thenReturn(expected);

        AgoraTokenService.AgoraJoinToken token = service.createRoomToken(SESSION_ID, ROOM_ID, PARTICIPANT_ONE_ID);

        assertThat(token).isEqualTo(expected);
    }

    @Test
    void createRoomToken_shouldRejectUnassignedParticipant() {
        SessionBreakoutRoom room = openRoom();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        when(roomRepository.findByIdAndSessionId(ROOM_ID, SESSION_ID)).thenReturn(Optional.of(room));
        when(participantRepository.findBySessionIdAndProfileIdAndStatusIn(
                eq(SESSION_ID),
                eq(PARTICIPANT_TWO_ID),
                org.mockito.ArgumentMatchers.<Collection<SessionBreakoutParticipant.AssignmentStatus>>any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRoomToken(SESSION_ID, ROOM_ID, PARTICIPANT_TWO_ID))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not assigned");
    }

    @Test
    void closeBreakouts_shouldMarkOpenRoomsAndAssignmentsReturned() {
        SessionBreakoutRoom room = openRoom();
        SessionBreakoutParticipant assignment = assignment(room, PARTICIPANT_ONE_ID, SessionBreakoutParticipant.AssignmentStatus.JOINED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(corporateAgoraSession()));
        when(roomRepository.findBySessionIdOrderByCreatedAtAsc(SESSION_ID)).thenReturn(List.of(room));
        when(participantRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(assignment));
        when(participantRepository.findBySessionIdAndStatusIn(
                eq(SESSION_ID),
                org.mockito.ArgumentMatchers.<Collection<SessionBreakoutParticipant.AssignmentStatus>>any()))
                .thenReturn(List.of(assignment));
        when(roomRepository.save(any(SessionBreakoutRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.save(any(SessionBreakoutParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                eq(PROGRAM_ID),
                org.mockito.ArgumentMatchers.<Collection<CompanyProgramParticipant.ParticipantStatus>>any()))
                .thenReturn(List.of(programParticipant(PARTICIPANT_ONE_ID, "Faith", "Wainaina")));

        SessionBreakoutDtos.SessionBreakoutStateDto state = service.closeBreakouts(SESSION_ID, MENTOR_ID);

        assertThat(room.getStatus()).isEqualTo(SessionBreakoutRoom.RoomStatus.CLOSED);
        assertThat(assignment.getStatus()).isEqualTo(SessionBreakoutParticipant.AssignmentStatus.RETURNED);
        assertThat(state.getRooms()).allMatch(roomDto -> roomDto.getStatus() == SessionBreakoutRoom.RoomStatus.CLOSED);
    }

    private Session corporateAgoraSession() {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setMentorId(MENTOR_ID);
        session.setMenteeId(PARTICIPANT_ONE_ID);
        session.setSkillId(SKILL_ID);
        session.setCompanyProgramId(PROGRAM_ID);
        session.setTitle("Corporate group session");
        session.setStatus(Session.SessionStatus.CONFIRMED);
        session.setMeetingPlatform(Session.MeetingPlatform.AGORA);
        session.setScheduledStart(ZonedDateTime.parse("2026-09-07T10:00:00Z"));
        session.setScheduledEnd(ZonedDateTime.parse("2026-09-07T11:00:00Z"));
        return session;
    }

    private SessionBreakoutRoom openRoom() {
        SessionBreakoutRoom room = new SessionBreakoutRoom();
        room.setId(ROOM_ID);
        room.setSessionId(SESSION_ID);
        room.setName("Room 1");
        room.setAgoraChannelName("pm-session-" + SESSION_ID + "-breakout-" + ROOM_ID);
        room.setStatus(SessionBreakoutRoom.RoomStatus.OPEN);
        room.setCreatedBy(MENTOR_ID);
        return room;
    }

    private SessionBreakoutRoom draftRoom(UUID roomId, String name) {
        SessionBreakoutRoom room = new SessionBreakoutRoom();
        room.setId(roomId);
        room.setSessionId(SESSION_ID);
        room.setName(name);
        room.setAgoraChannelName("pm-session-" + SESSION_ID + "-breakout-" + roomId);
        room.setStatus(SessionBreakoutRoom.RoomStatus.DRAFT);
        room.setCreatedBy(MENTOR_ID);
        return room;
    }

    private SessionBreakoutParticipant assignment(SessionBreakoutRoom room, UUID profileId, SessionBreakoutParticipant.AssignmentStatus status) {
        SessionBreakoutParticipant assignment = new SessionBreakoutParticipant();
        assignment.setId(UUID.randomUUID());
        assignment.setRoomId(room.getId());
        assignment.setRoom(room);
        assignment.setSessionId(SESSION_ID);
        assignment.setProfileId(profileId);
        assignment.setProfile(profile(profileId, "Faith", "Wainaina"));
        assignment.setStatus(status);
        return assignment;
    }

    private CompanyProgramParticipant programParticipant(UUID profileId, String firstName, String lastName) {
        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(UUID.randomUUID());
        participant.setCompanyProgram(companyProgram());
        participant.setProfile(profile(profileId, firstName, lastName));
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
        return participant;
    }

    private CompanyProgram companyProgram() {
        CompanyProgram program = new CompanyProgram();
        program.setId(PROGRAM_ID);
        program.setName("Leadership Accelerator");
        return program;
    }

    private Profile profile(UUID profileId, String firstName, String lastName) {
        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setEmail(firstName.toLowerCase() + "@example.com");
        profile.setUsername(firstName.toLowerCase());
        return profile;
    }
}
