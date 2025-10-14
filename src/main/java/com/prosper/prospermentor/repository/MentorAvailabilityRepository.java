package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.MentorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MentorAvailability entity operations
 */
@Repository
public interface MentorAvailabilityRepository extends JpaRepository<MentorAvailability, Long> {

    /**
     * Find all availability slots for a specific mentor
     */
    List<MentorAvailability> findByMentorId(UUID mentorId);

    /**
     * Find all active availability slots for a specific mentor
     */
    List<MentorAvailability> findByMentorIdAndIsActiveTrue(UUID mentorId);

    /**
     * Find availability slots for a mentor on a specific day
     */
    List<MentorAvailability> findByMentorIdAndDayOfWeek(UUID mentorId, DayOfWeek dayOfWeek);

    /**
     * Find active availability slots for a mentor on a specific day
     */
    List<MentorAvailability> findByMentorIdAndDayOfWeekAndIsActiveTrue(UUID mentorId, DayOfWeek dayOfWeek);

    /**
     * Find availability by mentor, day, and time range
     */
    Optional<MentorAvailability> findByMentorIdAndDayOfWeekAndStartTimeAndEndTime(
            UUID mentorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime);

    /**
     * Check if there are overlapping time slots for a mentor on a specific day
     * Excludes a specific availability ID (useful for updates)
     */
    @Query("SELECT ma FROM MentorAvailability ma WHERE ma.mentorId = :mentorId " +
           "AND ma.dayOfWeek = :dayOfWeek " +
           "AND ma.id != :excludeId " +
           "AND ((ma.startTime < :endTime AND ma.endTime > :startTime))")
    List<MentorAvailability> findOverlappingSlots(
            @Param("mentorId") UUID mentorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    /**
     * Check if there are overlapping time slots for a new availability (no ID to exclude)
     */
    @Query("SELECT ma FROM MentorAvailability ma WHERE ma.mentorId = :mentorId " +
           "AND ma.dayOfWeek = :dayOfWeek " +
           "AND ((ma.startTime < :endTime AND ma.endTime > :startTime))")
    List<MentorAvailability> findOverlappingSlotsForNew(
            @Param("mentorId") UUID mentorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    /**
     * Delete all availability slots for a specific mentor
     */
    void deleteByMentorId(UUID mentorId);

    /**
     * Delete availability slots for a mentor on a specific day
     */
    void deleteByMentorIdAndDayOfWeek(UUID mentorId, DayOfWeek dayOfWeek);

    /**
     * Count active availability slots for a mentor
     */
    long countByMentorIdAndIsActiveTrue(UUID mentorId);

    /**
     * Find all mentors who have availability on a specific day
     */
    @Query("SELECT DISTINCT ma.mentorId FROM MentorAvailability ma " +
           "WHERE ma.dayOfWeek = :dayOfWeek AND ma.isActive = true")
    List<UUID> findMentorsByDayOfWeek(@Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * Find mentors available within a specific time range on a given day
     */
    @Query("SELECT DISTINCT ma.mentorId FROM MentorAvailability ma " +
           "WHERE ma.dayOfWeek = :dayOfWeek " +
           "AND ma.isActive = true " +
           "AND ma.startTime <= :time " +
           "AND ma.endTime > :time")
    List<UUID> findMentorsByDayAndTime(
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("time") LocalTime time);
}
