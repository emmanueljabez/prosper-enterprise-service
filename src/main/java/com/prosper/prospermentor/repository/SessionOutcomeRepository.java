package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionOutcome;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionOutcomeRepository extends JpaRepository<SessionOutcome, UUID> {

    @EntityGraph(attributePaths = {"session", "actionItems"})
    @Query("SELECT outcome FROM SessionOutcome outcome WHERE outcome.session.id = :sessionId")
    Optional<SessionOutcome> findDetailedBySessionId(@Param("sessionId") UUID sessionId);

    @EntityGraph(attributePaths = {"session", "actionItems"})
    @Query("SELECT outcome FROM SessionOutcome outcome WHERE outcome.session.id IN :sessionIds")
    List<SessionOutcome> findDetailedBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);
}
