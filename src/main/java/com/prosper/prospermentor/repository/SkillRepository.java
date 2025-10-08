package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Skill entity
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    /**
     * Find skill by name
     */
    Optional<Skill> findByName(String name);

    /**
     * Find skills by name containing (case insensitive)
     */
    @Query("SELECT s FROM Skill s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Skill> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find all skills ordered by name
     */
    @Query("SELECT s FROM Skill s ORDER BY s.name ASC")
    List<Skill> findAllOrderedByName();

    /**
     * Check if skill exists by name (case insensitive)
     */
    @Query("SELECT COUNT(s) > 0 FROM Skill s WHERE LOWER(s.name) = LOWER(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    /**
     * Find skills that are used by mentors
     */
    @Query("SELECT DISTINCT s FROM Skill s JOIN MentorSkill ms ON s.id = ms.skillId")
    List<Skill> findSkillsUsedByMentors();

    /**
     * Find skills not used by any mentor
     */
    @Query("SELECT s FROM Skill s WHERE s.id NOT IN (SELECT ms.skillId FROM MentorSkill ms)")
    List<Skill> findUnusedSkills();

    /**
     * Count skills by name pattern
     */
    @Query("SELECT COUNT(s) FROM Skill s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :pattern, '%'))")
    long countByNamePattern(@Param("pattern") String pattern);
}


