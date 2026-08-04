package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.JourneyInstance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JourneyInstanceRepository extends JpaRepository<JourneyInstance, UUID> {

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.companyProgram.journeyTemplate",
            "participant.profile",
            "journeyTemplate"
    })
    List<JourneyInstance> findByParticipant_IdIn(Collection<UUID> participantIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.companyProgram.journeyTemplate",
            "participant.profile",
            "journeyTemplate"
    })
    Optional<JourneyInstance> findByParticipant_Id(UUID participantId);
}
