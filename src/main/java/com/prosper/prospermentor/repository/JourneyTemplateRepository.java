package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.JourneyTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JourneyTemplateRepository extends JpaRepository<JourneyTemplate, UUID> {

    @EntityGraph(attributePaths = {"steps"})
    List<JourneyTemplate> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = {"steps"})
    List<JourneyTemplate> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"steps"})
    Optional<JourneyTemplate> findById(UUID id);
}
