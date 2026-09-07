package com.prosper.prospermentor.service.meeting;

import com.prosper.prospermentor.dto.SessionBreakoutDtos;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionBreakoutParticipant;
import com.prosper.prospermentor.entity.SessionBreakoutRoom;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.SessionBreakoutParticipantRepository;
import com.prosper.prospermentor.repository.SessionBreakoutRoomRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SessionBreakoutService {

    private static final int MAX_BREAKOUT_ROOMS = 12;
    private static final int DEFAULT_ROOM_COUNT = 2;
    private static final long JOIN_WINDOW_MINUTES_BEFORE_START = 15;
    private static final long JOIN_WINDOW_MINUTES_AFTER_END = 30;

    private static final EnumSet<Session.SessionStatus> JOINABLE_STATUSES = EnumSet.of(
            Session.SessionStatus.CONFIRMED,
            Session.SessionStatus.SCHEDULED,
            Session.SessionStatus.IN_PROGRESS
    );

    private static final List<CompanyProgramParticipant.ParticipantStatus> ELIGIBLE_PROGRAM_PARTICIPANT_STATUSES = List.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE
    );

    private static final List<SessionBreakoutParticipant.AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = List.of(
            SessionBreakoutParticipant.AssignmentStatus.ASSIGNED,
            SessionBreakoutParticipant.AssignmentStatus.JOINED
    );

    private final SessionRepository sessionRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final SessionBreakoutRoomRepository roomRepository;
    private final SessionBreakoutParticipantRepository participantRepository;
    private final AgoraTokenService agoraTokenService;
    private final Clock clock;

    @Autowired
    public SessionBreakoutService(SessionRepository sessionRepository,
                                  CompanyProgramParticipantRepository companyProgramParticipantRepository,
                                  SessionBreakoutRoomRepository roomRepository,
                                  SessionBreakoutParticipantRepository participantRepository,
                                  AgoraTokenService agoraTokenService) {
        this(
                sessionRepository,
                companyProgramParticipantRepository,
                roomRepository,
                participantRepository,
                agoraTokenService,
                Clock.systemUTC()
        );
    }

    public SessionBreakoutService(SessionRepository sessionRepository,
                                  CompanyProgramParticipantRepository companyProgramParticipantRepository,
                                  SessionBreakoutRoomRepository roomRepository,
                                  SessionBreakoutParticipantRepository participantRepository,
                                  AgoraTokenService agoraTokenService,
                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.companyProgramParticipantRepository = companyProgramParticipantRepository;
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.agoraTokenService = agoraTokenService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionBreakoutDtos.SessionBreakoutStateDto getState(UUID sessionId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireCorporateAgoraSession(session);
        requireSessionAccess(session, requesterProfileId);
        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto createRooms(
            UUID sessionId,
            UUID requesterProfileId,
            SessionBreakoutDtos.CreateBreakoutRoomsRequest request
    ) {
        Session session = loadSession(sessionId);
        requireHostManagement(session, requesterProfileId);

        List<String> roomNames = resolveRoomNames(request);
        int existingRoomCount = roomRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).size();
        for (int index = 0; index < roomNames.size(); index++) {
            UUID roomId = UUID.randomUUID();
            SessionBreakoutRoom room = new SessionBreakoutRoom();
            room.setId(roomId);
            room.setSessionId(sessionId);
            room.setName(roomNames.get(index).isBlank() ? "Room " + (existingRoomCount + index + 1) : roomNames.get(index));
            room.setAgoraChannelName(breakoutChannelName(sessionId, roomId));
            room.setStatus(SessionBreakoutRoom.RoomStatus.DRAFT);
            room.setCreatedBy(requesterProfileId);
            roomRepository.save(room);
        }

        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto autoAssign(UUID sessionId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireHostManagement(session, requesterProfileId);

        List<SessionBreakoutRoom> rooms = roomRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(room -> room.getStatus() != SessionBreakoutRoom.RoomStatus.CLOSED)
                .toList();
        if (rooms.isEmpty()) {
            throw new IllegalStateException("Create breakout rooms before auto-assigning participants");
        }

        List<CompanyProgramParticipant> eligibleParticipants = eligibleProgramParticipants(session).stream()
                .filter(participant -> participant.getProfile() != null)
                .filter(participant -> !requesterProfileId.equals(participant.getProfile().getId()))
                .sorted(Comparator.comparing(participant -> displayName(participant.getProfile()).toLowerCase()))
                .toList();

        for (int index = 0; index < eligibleParticipants.size(); index++) {
            Profile profile = eligibleParticipants.get(index).getProfile();
            SessionBreakoutRoom room = rooms.get(index % rooms.size());
            assignParticipant(sessionId, room, profile.getId());
        }

        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto moveParticipant(
            UUID sessionId,
            UUID requesterProfileId,
            UUID profileId,
            SessionBreakoutDtos.MoveBreakoutParticipantRequest request
    ) {
        Session session = loadSession(sessionId);
        requireHostManagement(session, requesterProfileId);
        if (request == null || request.getRoomId() == null) {
            throw new IllegalArgumentException("Breakout room is required");
        }
        if (!eligibleProfileIds(session).contains(profileId)) {
            throw new SecurityException("Profile is not a participant in this corporate session");
        }

        SessionBreakoutRoom room = loadRoom(sessionId, request.getRoomId());
        if (room.getStatus() == SessionBreakoutRoom.RoomStatus.CLOSED) {
            throw new IllegalStateException("Cannot assign participants to a closed breakout room");
        }

        assignParticipant(sessionId, room, profileId);
        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto openBreakouts(UUID sessionId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireHostManagement(session, requesterProfileId);

        ZonedDateTime now = now();
        List<SessionBreakoutRoom> rooms = roomRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (rooms.isEmpty()) {
            throw new IllegalStateException("Create breakout rooms before opening them");
        }

        rooms.stream()
                .filter(room -> room.getStatus() != SessionBreakoutRoom.RoomStatus.CLOSED)
                .forEach(room -> {
                    room.setStatus(SessionBreakoutRoom.RoomStatus.OPEN);
                    if (room.getOpenedAt() == null) {
                        room.setOpenedAt(now);
                    }
                    roomRepository.save(room);
                });

        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto markJoined(UUID sessionId, UUID roomId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireCorporateAgoraSession(session);
        requireJoinWindow(session);
        SessionBreakoutRoom room = loadOpenRoom(sessionId, roomId);

        participantRepository.findBySessionIdAndProfileIdAndStatusIn(sessionId, requesterProfileId, ACTIVE_ASSIGNMENT_STATUSES)
                .filter(assignment -> room.getId().equals(assignment.getRoomId()))
                .ifPresentOrElse(assignment -> {
                    assignment.setStatus(SessionBreakoutParticipant.AssignmentStatus.JOINED);
                    assignment.setJoinedAt(now());
                    participantRepository.save(assignment);
                }, () -> {
                    if (!isHost(session, requesterProfileId)) {
                        throw new SecurityException("Authenticated user is not assigned to this breakout room");
                    }
                });

        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto returnToMain(UUID sessionId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireCorporateAgoraSession(session);
        requireSessionAccess(session, requesterProfileId);

        participantRepository.findBySessionIdAndProfileIdAndStatusIn(sessionId, requesterProfileId, ACTIVE_ASSIGNMENT_STATUSES)
                .ifPresent(assignment -> {
                    assignment.setStatus(SessionBreakoutParticipant.AssignmentStatus.RETURNED);
                    assignment.setLeftAt(now());
                    participantRepository.save(assignment);
                });

        return buildState(session, requesterProfileId);
    }

    public SessionBreakoutDtos.SessionBreakoutStateDto closeBreakouts(UUID sessionId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireHostManagement(session, requesterProfileId);
        ZonedDateTime now = now();

        roomRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(room -> room.getStatus() != SessionBreakoutRoom.RoomStatus.CLOSED)
                .forEach(room -> {
                    room.setStatus(SessionBreakoutRoom.RoomStatus.CLOSED);
                    room.setClosedAt(now);
                    roomRepository.save(room);
                });

        participantRepository.findBySessionIdAndStatusIn(sessionId, ACTIVE_ASSIGNMENT_STATUSES)
                .forEach(assignment -> {
                    assignment.setStatus(SessionBreakoutParticipant.AssignmentStatus.RETURNED);
                    assignment.setLeftAt(now);
                    participantRepository.save(assignment);
                });

        return buildState(session, requesterProfileId);
    }

    @Transactional(readOnly = true)
    public AgoraTokenService.AgoraJoinToken createRoomToken(UUID sessionId, UUID roomId, UUID requesterProfileId) {
        Session session = loadSession(sessionId);
        requireCorporateAgoraSession(session);
        requireJoinWindow(session);
        SessionBreakoutRoom room = loadOpenRoom(sessionId, roomId);

        if (!isHost(session, requesterProfileId)) {
            SessionBreakoutParticipant assignment = participantRepository
                    .findBySessionIdAndProfileIdAndStatusIn(sessionId, requesterProfileId, ACTIVE_ASSIGNMENT_STATUSES)
                    .orElseThrow(() -> new SecurityException("Authenticated user is not assigned to this breakout room"));
            if (!room.getId().equals(assignment.getRoomId())) {
                throw new SecurityException("Authenticated user is not assigned to this breakout room");
            }
        }

        return agoraTokenService.createJoinToken(room.getAgoraChannelName(), requesterProfileId);
    }

    public static String breakoutChannelName(UUID sessionId, UUID roomId) {
        if (sessionId == null || roomId == null) {
            throw new IllegalArgumentException("Session ID and room ID are required to build a breakout channel name");
        }
        return AgoraMeetingProvider.channelNameFor(sessionId) + "-breakout-" + roomId;
    }

    private void assignParticipant(UUID sessionId, SessionBreakoutRoom room, UUID profileId) {
        participantRepository.deleteBySessionIdAndProfileIdAndStatusIn(sessionId, profileId, ACTIVE_ASSIGNMENT_STATUSES);

        SessionBreakoutParticipant assignment = new SessionBreakoutParticipant();
        assignment.setId(UUID.randomUUID());
        assignment.setSessionId(sessionId);
        assignment.setRoomId(room.getId());
        assignment.setStatus(SessionBreakoutParticipant.AssignmentStatus.ASSIGNED);
        assignment.setProfileId(profileId);
        participantRepository.save(assignment);
    }

    private Session loadSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private SessionBreakoutRoom loadRoom(UUID sessionId, UUID roomId) {
        return roomRepository.findByIdAndSessionId(roomId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Breakout room not found"));
    }

    private SessionBreakoutRoom loadOpenRoom(UUID sessionId, UUID roomId) {
        SessionBreakoutRoom room = loadRoom(sessionId, roomId);
        if (room.getStatus() != SessionBreakoutRoom.RoomStatus.OPEN) {
            throw new IllegalStateException("Breakout room is not open");
        }
        return room;
    }

    private void requireHostManagement(Session session, UUID requesterProfileId) {
        requireCorporateAgoraSession(session);
        requireJoinWindow(session);
        requireHost(session, requesterProfileId);
    }

    private void requireCorporateAgoraSession(Session session) {
        if (session.getMeetingPlatform() != Session.MeetingPlatform.AGORA) {
            throw new IllegalStateException("Session is not configured for Agora");
        }
        if (session.getCompanyProgramId() == null) {
            throw new IllegalStateException("Breakout rooms are only available for corporate group sessions");
        }
        if (!JOINABLE_STATUSES.contains(session.getStatus())) {
            throw new IllegalStateException("Breakout rooms are only available for confirmed session meetings");
        }
    }

    private void requireHost(Session session, UUID requesterProfileId) {
        if (!isHost(session, requesterProfileId)) {
            throw new SecurityException("Only the session host can manage breakout rooms");
        }
    }

    private void requireSessionAccess(Session session, UUID requesterProfileId) {
        if (isHost(session, requesterProfileId) || eligibleProfileIds(session).contains(requesterProfileId)) {
            return;
        }
        throw new SecurityException("Authenticated user is not a participant in this session");
    }

    private void requireJoinWindow(Session session) {
        if (session.getScheduledStart() == null || session.getScheduledEnd() == null) {
            throw new IllegalStateException("Session schedule is required before joining Agora");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime opensAt = session.getScheduledStart().minusMinutes(JOIN_WINDOW_MINUTES_BEFORE_START);
        ZonedDateTime closesAt = session.getScheduledEnd().plusMinutes(JOIN_WINDOW_MINUTES_AFTER_END);

        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw new IllegalStateException("Agora token is outside the session join window");
        }
    }

    private boolean isHost(Session session, UUID requesterProfileId) {
        return requesterProfileId != null && requesterProfileId.equals(session.getMentorId());
    }

    private List<String> resolveRoomNames(SessionBreakoutDtos.CreateBreakoutRoomsRequest request) {
        if (request != null && request.getNames() != null && !request.getNames().isEmpty()) {
            List<String> names = request.getNames().stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> !value.isBlank())
                    .limit(MAX_BREAKOUT_ROOMS)
                    .toList();
            if (names.isEmpty()) {
                throw new IllegalArgumentException("At least one breakout room name is required");
            }
            return names;
        }

        int count = request != null && request.getCount() != null ? request.getCount() : DEFAULT_ROOM_COUNT;
        if (count < 1 || count > MAX_BREAKOUT_ROOMS) {
            throw new IllegalArgumentException("Breakout room count must be between 1 and 12");
        }

        List<String> names = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            names.add("Room " + index);
        }
        return names;
    }

    private List<CompanyProgramParticipant> eligibleProgramParticipants(Session session) {
        return companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                session.getCompanyProgramId(),
                ELIGIBLE_PROGRAM_PARTICIPANT_STATUSES
        );
    }

    private Set<UUID> eligibleProfileIds(Session session) {
        return eligibleProgramParticipants(session).stream()
                .map(CompanyProgramParticipant::getProfile)
                .filter(Objects::nonNull)
                .map(Profile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private SessionBreakoutDtos.SessionBreakoutStateDto buildState(Session session, UUID requesterProfileId) {
        List<SessionBreakoutRoom> rooms = roomRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<SessionBreakoutParticipant> assignments = participantRepository.findBySessionId(session.getId());
        List<CompanyProgramParticipant> availableParticipants = eligibleProgramParticipants(session);

        Map<UUID, Profile> profilesById = new HashMap<>();
        availableParticipants.stream()
                .map(CompanyProgramParticipant::getProfile)
                .filter(Objects::nonNull)
                .forEach(profile -> profilesById.put(profile.getId(), profile));
        assignments.stream()
                .map(SessionBreakoutParticipant::getProfile)
                .filter(Objects::nonNull)
                .forEach(profile -> profilesById.putIfAbsent(profile.getId(), profile));

        Map<UUID, SessionBreakoutRoom> roomsById = rooms.stream()
                .collect(Collectors.toMap(SessionBreakoutRoom::getId, room -> room, (first, second) -> first, LinkedHashMap::new));
        Map<UUID, List<SessionBreakoutParticipant>> assignmentsByRoomId = assignments.stream()
                .collect(Collectors.groupingBy(SessionBreakoutParticipant::getRoomId, LinkedHashMap::new, Collectors.toList()));

        List<SessionBreakoutDtos.SessionBreakoutRoomDto> roomDtos = rooms.stream()
                .map(room -> toRoomDto(room, assignmentsByRoomId.getOrDefault(room.getId(), List.of()), profilesById))
                .toList();

        SessionBreakoutDtos.SessionBreakoutRoomDto assignedRoom = participantRepository
                .findBySessionIdAndProfileIdAndStatusIn(session.getId(), requesterProfileId, ACTIVE_ASSIGNMENT_STATUSES)
                .map(SessionBreakoutParticipant::getRoomId)
                .map(roomsById::get)
                .filter(room -> room.getStatus() == SessionBreakoutRoom.RoomStatus.OPEN)
                .map(room -> toRoomDto(room, assignmentsByRoomId.getOrDefault(room.getId(), List.of()), profilesById))
                .orElse(null);

        return SessionBreakoutDtos.SessionBreakoutStateDto.builder()
                .host(isHost(session, requesterProfileId))
                .rooms(roomDtos)
                .availableParticipants(toAvailableParticipantDtos(availableParticipants, assignments, roomsById))
                .assignedRoom(assignedRoom)
                .build();
    }

    private List<SessionBreakoutDtos.SessionBreakoutParticipantDto> toAvailableParticipantDtos(
            List<CompanyProgramParticipant> availableParticipants,
            List<SessionBreakoutParticipant> assignments,
            Map<UUID, SessionBreakoutRoom> roomsById
    ) {
        Map<UUID, SessionBreakoutParticipant> activeAssignmentsByProfile = assignments.stream()
                .filter(assignment -> ACTIVE_ASSIGNMENT_STATUSES.contains(assignment.getStatus()))
                .collect(Collectors.toMap(
                        SessionBreakoutParticipant::getProfileId,
                        assignment -> assignment,
                        (first, second) -> first
                ));

        return availableParticipants.stream()
                .map(CompanyProgramParticipant::getProfile)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(profile -> displayName(profile).toLowerCase()))
                .map(profile -> {
                    SessionBreakoutParticipant assignment = activeAssignmentsByProfile.get(profile.getId());
                    SessionBreakoutRoom room = assignment == null ? null : roomsById.get(assignment.getRoomId());
                    return SessionBreakoutDtos.SessionBreakoutParticipantDto.builder()
                            .profileId(profile.getId())
                            .roomId(room == null ? null : room.getId())
                            .roomName(room == null ? null : room.getName())
                            .name(displayName(profile))
                            .avatarUrl(profile.getAvatarUrl())
                            .status(assignment == null ? null : assignment.getStatus())
                            .build();
                })
                .toList();
    }

    private SessionBreakoutDtos.SessionBreakoutRoomDto toRoomDto(
            SessionBreakoutRoom room,
            List<SessionBreakoutParticipant> assignments,
            Map<UUID, Profile> profilesById
    ) {
        return SessionBreakoutDtos.SessionBreakoutRoomDto.builder()
                .id(room.getId())
                .sessionId(room.getSessionId())
                .name(room.getName())
                .agoraChannelName(room.getAgoraChannelName())
                .status(room.getStatus())
                .participants(assignments.stream()
                        .map(assignment -> toParticipantDto(assignment, room, profilesById.get(assignment.getProfileId())))
                        .toList())
                .build();
    }

    private SessionBreakoutDtos.SessionBreakoutParticipantDto toParticipantDto(
            SessionBreakoutParticipant assignment,
            SessionBreakoutRoom room,
            Profile profile
    ) {
        return SessionBreakoutDtos.SessionBreakoutParticipantDto.builder()
                .profileId(assignment.getProfileId())
                .roomId(room.getId())
                .roomName(room.getName())
                .name(profile == null ? "Participant" : displayName(profile))
                .avatarUrl(profile == null ? null : profile.getAvatarUrl())
                .status(assignment.getStatus())
                .build();
    }

    private String displayName(Profile profile) {
        String name = String.join(" ",
                String.valueOf(profile.getFirstName() == null ? "" : profile.getFirstName()).trim(),
                String.valueOf(profile.getLastName() == null ? "" : profile.getLastName()).trim()
        ).trim();
        if (!name.isBlank()) {
            return name;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        return profile.getEmail() != null && !profile.getEmail().isBlank() ? profile.getEmail() : "Participant";
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }
}
