package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionBreakoutRoom;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionBreakoutRoomRepository extends JpaRepository<SessionBreakoutRoom, UUID> {

    @EntityGraph(attributePaths = {"session"})
    List<SessionBreakoutRoom> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<SessionBreakoutRoom> findByIdAndSessionId(UUID id, UUID sessionId);
}
