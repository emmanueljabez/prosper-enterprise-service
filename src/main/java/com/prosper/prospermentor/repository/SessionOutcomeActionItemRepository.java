package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionOutcomeActionItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionOutcomeActionItemRepository extends JpaRepository<SessionOutcomeActionItem, UUID> {

    @EntityGraph(attributePaths = {
            "sessionOutcome",
            "sessionOutcome.session",
            "sessionOutcome.session.companyProgramParticipant",
            "sessionOutcome.session.companyProgramParticipant.profile"
    })
    @Query("SELECT item FROM SessionOutcomeActionItem item WHERE item.id = :actionItemId")
    Optional<SessionOutcomeActionItem> findByIdWithSessionContext(@Param("actionItemId") UUID actionItemId);
}
