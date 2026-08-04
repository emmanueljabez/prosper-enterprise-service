package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionProposal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionProposalRepository extends JpaRepository<SessionProposal, UUID> {

    @EntityGraph(attributePaths = "slots")
    Optional<SessionProposal> findFirstBySessionIdAndStatusOrderByProposedAtDesc(
            UUID sessionId,
            SessionProposal.ProposalStatus status
    );
}
