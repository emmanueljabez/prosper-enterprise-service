package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.JourneyInstanceStep;
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
public interface JourneyInstanceStepRepository extends JpaRepository<JourneyInstanceStep, UUID> {

    boolean existsByJourneyStep_JourneyTemplate_Id(UUID journeyTemplateId);
    void deleteByJourneyInstance_Id(UUID journeyInstanceId);

    @EntityGraph(attributePaths = {
            "journeyInstance",
            "journeyInstance.participant",
            "journeyInstance.participant.companyProgram",
            "journeyInstance.participant.profile",
            "journeyStep",
            "journeyStep.journeyTemplate"
    })
    List<JourneyInstanceStep> findByJourneyInstance_IdIn(Collection<UUID> journeyInstanceIds);

    @EntityGraph(attributePaths = {
            "journeyInstance",
            "journeyInstance.participant",
            "journeyInstance.participant.companyProgram",
            "journeyInstance.participant.profile",
            "journeyStep",
            "journeyStep.journeyTemplate"
    })
    Optional<JourneyInstanceStep> findById(UUID id);

    @EntityGraph(attributePaths = {
            "journeyInstance",
            "journeyInstance.participant",
            "journeyInstance.participant.companyProgram",
            "journeyInstance.participant.profile",
            "journeyStep",
            "journeyStep.journeyTemplate"
    })
    @Query("""
            SELECT step
            FROM JourneyInstanceStep step
            WHERE step.journeyInstance.id = :journeyInstanceId
            ORDER BY step.journeyStep.defaultSequence ASC
            """)
    List<JourneyInstanceStep> findDetailedByJourneyInstanceId(@Param("journeyInstanceId") UUID journeyInstanceId);
}
