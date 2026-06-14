package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.JourneyStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JourneyStepRepository extends JpaRepository<JourneyStep, UUID> {

    List<JourneyStep> findByJourneyTemplate_IdOrderByDefaultSequenceAsc(UUID journeyTemplateId);

    void deleteByJourneyTemplate_Id(UUID journeyTemplateId);
}
