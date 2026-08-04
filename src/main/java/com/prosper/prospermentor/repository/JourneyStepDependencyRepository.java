package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.JourneyStepDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JourneyStepDependencyRepository extends JpaRepository<JourneyStepDependency, UUID> {

    List<JourneyStepDependency> findByJourneyTemplate_Id(UUID journeyTemplateId);

    void deleteByJourneyTemplate_Id(UUID journeyTemplateId);
}
