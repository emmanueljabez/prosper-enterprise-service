package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramMatchWorkspaceRepository extends JpaRepository<CompanyProgramMatchWorkspace, UUID> {

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.profile"
    })
    Optional<CompanyProgramMatchWorkspace> findByParticipant_Id(UUID participantId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.profile"
    })
    List<CompanyProgramMatchWorkspace> findByParticipant_IdIn(Collection<UUID> participantIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.profile"
    })
    List<CompanyProgramMatchWorkspace> findByStatusAndSelectionDeadlineAtBeforeOrderBySelectionDeadlineAtAsc(
            CompanyProgramMatchWorkspace.MatchStatus status,
            LocalDateTime selectionDeadlineAt,
            Pageable pageable
    );
}
