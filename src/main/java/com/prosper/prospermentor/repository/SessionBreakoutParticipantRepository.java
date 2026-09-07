package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionBreakoutParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionBreakoutParticipantRepository extends JpaRepository<SessionBreakoutParticipant, UUID> {

    @EntityGraph(attributePaths = {"room", "profile"})
    List<SessionBreakoutParticipant> findBySessionId(UUID sessionId);

    @EntityGraph(attributePaths = {"room", "profile"})
    Optional<SessionBreakoutParticipant> findBySessionIdAndProfileIdAndStatusIn(
            UUID sessionId,
            UUID profileId,
            Collection<SessionBreakoutParticipant.AssignmentStatus> statuses
    );

    @EntityGraph(attributePaths = {"room", "profile"})
    List<SessionBreakoutParticipant> findBySessionIdAndStatusIn(
            UUID sessionId,
            Collection<SessionBreakoutParticipant.AssignmentStatus> statuses
    );

    void deleteBySessionIdAndProfileIdAndStatusIn(
            UUID sessionId,
            UUID profileId,
            Collection<SessionBreakoutParticipant.AssignmentStatus> statuses
    );
}
