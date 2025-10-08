package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.MentorSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for MentorSkill entity (junction table)
 */
@Repository
public interface MentorSkillRepository extends JpaRepository<MentorSkill, MentorSkill.MentorSkillId> {

    /**
     * Find all skills for a specific mentor
     */
    @Query("SELECT ms FROM MentorSkill ms WHERE ms.mentorId = :mentorId")
    List<MentorSkill> findByMentorId(@Param("mentorId") UUID mentorId);

    /**
     * Find all mentors for a specific skill
     */
    @Query("SELECT ms FROM MentorSkill ms WHERE ms.skillId = :skillId")
    List<MentorSkill> findBySkillId(@Param("skillId") UUID skillId);

    /**
     * Check if mentor has specific skill
     */
    @Query("SELECT COUNT(ms) > 0 FROM MentorSkill ms WHERE ms.mentorId = :mentorId AND ms.skillId = :skillId")
    boolean existsByMentorIdAndSkillId(@Param("mentorId") UUID mentorId, @Param("skillId") UUID skillId);

    /**
     * Delete all skills for a mentor
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MentorSkill ms WHERE ms.mentorId = :mentorId")
    void deleteByMentorId(@Param("mentorId") UUID mentorId);

    /**
     * Delete specific mentor-skill relationship
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MentorSkill ms WHERE ms.mentorId = :mentorId AND ms.skillId = :skillId")
    void deleteByMentorIdAndSkillId(@Param("mentorId") UUID mentorId, @Param("skillId") UUID skillId);

    /**
     * Get skill IDs for a mentor
     */
    @Query("SELECT ms.skillId FROM MentorSkill ms WHERE ms.mentorId = :mentorId")
    List<UUID> findSkillIdsByMentorId(@Param("mentorId") UUID mentorId);

    /**
     * Get mentor IDs for a skill
     */
    @Query("SELECT ms.mentorId FROM MentorSkill ms WHERE ms.skillId = :skillId")
    List<UUID> findMentorIdsBySkillId(@Param("skillId") UUID skillId);

    /**
     * Count skills for a mentor
     */
    @Query("SELECT COUNT(ms) FROM MentorSkill ms WHERE ms.mentorId = :mentorId")
    long countByMentorId(@Param("mentorId") UUID mentorId);

    /**
     * Count mentors for a skill
     */
    @Query("SELECT COUNT(ms) FROM MentorSkill ms WHERE ms.skillId = :skillId")
    long countBySkillId(@Param("skillId") UUID skillId);

    /**
     * Find mentors with specific skills (intersection)
     */
    @Query("SELECT ms.mentorId FROM MentorSkill ms WHERE ms.skillId IN :skillIds " +
           "GROUP BY ms.mentorId HAVING COUNT(DISTINCT ms.skillId) = :skillCount")
    List<UUID> findMentorsWithAllSkills(@Param("skillIds") List<UUID> skillIds, @Param("skillCount") long skillCount);

    /**
     * Find mentors with any of the specified skills (union)
     */
    @Query("SELECT DISTINCT ms.mentorId FROM MentorSkill ms WHERE ms.skillId IN :skillIds")
    List<UUID> findMentorsWithAnySkill(@Param("skillIds") List<UUID> skillIds);
}


