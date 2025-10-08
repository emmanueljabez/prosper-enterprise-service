package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for MentorProfile entity
 */
@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {

    /**
     * Find mentor profiles by company
     */
    List<MentorProfile> findByCompany(String company);

    /**
     * Find mentor profiles by title
     */
    List<MentorProfile> findByTitle(String title);

    /**
     * Find mentor profiles by years of experience range
     */
    @Query("SELECT m FROM MentorProfile m WHERE m.yearsExperience BETWEEN :min AND :max")
    List<MentorProfile> findByYearsExperienceBetween(@Param("min") Integer min, @Param("max") Integer max);

    /**
     * Find mentor profiles by hourly rate range
     */
    @Query("SELECT m FROM MentorProfile m WHERE m.hourlyRate BETWEEN :min AND :max")
    List<MentorProfile> findByHourlyRateBetween(@Param("min") BigDecimal min, @Param("max") BigDecimal max);

    /**
     * Find mentor profiles with specific specialization
     */
    @Query(value = "SELECT * FROM mentor_profiles m WHERE :specialization = ANY(m.specializations)", nativeQuery = true)
    List<MentorProfile> findBySpecializationsContaining(@Param("specialization") String specialization);

    /**
     * Find mentor profiles speaking specific language
     */
    @Query(value = "SELECT * FROM mentor_profiles m WHERE :language = ANY(m.languages)", nativeQuery = true)
    List<MentorProfile> findByLanguagesContaining(@Param("language") String language);

    /**
     * Find mentor profiles by timezone
     */
    List<MentorProfile> findByTimezone(String timezone);

    /**
     * Find available mentors
     */
    List<MentorProfile> findByIsAvailableTrue();

    /**
     * Find mentor profiles with rating above threshold
     */
    @Query("SELECT m FROM MentorProfile m WHERE m.rating >= :minRating")
    List<MentorProfile> findByRatingGreaterThanEqual(@Param("minRating") BigDecimal minRating);

    /**
     * Find mentor profiles ordered by rating
     */
    @Query("SELECT m FROM MentorProfile m WHERE m.isAvailable = true ORDER BY m.rating DESC, m.totalReviews DESC")
    List<MentorProfile> findAvailableMentorsOrderedByRating();

    /**
     * Count mentor profiles by company
     */
    long countByCompany(String company);

    /**
     * Find mentors with most sessions
     */
    @Query("SELECT m FROM MentorProfile m ORDER BY m.totalSessions DESC")
    List<MentorProfile> findMentorsOrderedBySessionCount();
}
