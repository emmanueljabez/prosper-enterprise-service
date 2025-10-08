package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.MenteeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MenteeProfile entity
 */
@Repository
public interface MenteeProfileRepository extends JpaRepository<MenteeProfile, UUID> {

    /**
     * Find mentee profiles by industry
     */
    List<MenteeProfile> findByIndustry(String industry);

    /**
     * Find mentee profiles by career level
     */
    List<MenteeProfile> findByCareerLevel(String careerLevel);

    /**
     * Find mentee profiles by budget range
     */
    List<MenteeProfile> findByBudgetRange(String budgetRange);

    /**
     * Find mentee profiles by learning style
     */
    List<MenteeProfile> findByLearningStyle(String learningStyle);

    /**
     * Find mentee profiles with specific goals
     */
    @Query(value = "SELECT * FROM mentee_profiles m WHERE :goal = ANY(m.goals)", nativeQuery = true)
    List<MenteeProfile> findByGoalsContaining(@Param("goal") String goal);

    /**
     * Find mentee profiles with specific interests
     */
    @Query(value = "SELECT * FROM mentee_profiles m WHERE :interest = ANY(m.interests)", nativeQuery = true)
    List<MenteeProfile> findByInterestsContaining(@Param("interest") String interest);

    /**
     * Find mentee profiles by preferred session duration range
     */
    @Query("SELECT m FROM MenteeProfile m WHERE m.preferredSessionDuration BETWEEN :min AND :max")
    List<MenteeProfile> findByPreferredSessionDurationBetween(@Param("min") Integer min, @Param("max") Integer max);

    /**
     * Count mentee profiles by industry
     */
    @Query("SELECT COUNT(m) FROM MenteeProfile m WHERE m.industry = :industry")
    long countByIndustry(@Param("industry") String industry);
}
