package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Session entity
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * Find sessions by mentor ID
     */
    List<Session> findByMentorId(UUID mentorId);

    /**
     * Find sessions by mentee ID
     */
    List<Session> findByMenteeId(UUID menteeId);

    List<Session> findByCompanyProgramParticipantId(UUID companyProgramParticipantId);

    @Query("SELECT s FROM Session s WHERE s.companyProgramParticipantId IN :participantIds ORDER BY s.scheduledStart DESC")
    List<Session> findByCompanyProgramParticipantIdInOrderByScheduledStartDesc(@Param("participantIds") Collection<UUID> participantIds);

    long countByCompanyProgramParticipantIdAndMentorIdAndStatus(UUID companyProgramParticipantId,
                                                                UUID mentorId,
                                                                Session.SessionStatus status);

    /**
     * Find sessions for a group of mentees.
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId IN :menteeIds ORDER BY s.scheduledStart DESC")
    List<Session> findByMenteeIdIn(@Param("menteeIds") Collection<UUID> menteeIds);

    /**
     * Find sessions by status
     */
    List<Session> findByStatus(Session.SessionStatus status);

    /**
     * Find sessions by mentor and status
     */
    List<Session> findByMentorIdAndStatus(UUID mentorId, Session.SessionStatus status);

    /**
     * Find sessions by mentee and status
     */
    List<Session> findByMenteeIdAndStatus(UUID menteeId, Session.SessionStatus status);
    
    /**
     * Find sessions by mentor ID, ordered by start time (paginated)
     */
    Page<Session> findByMentorIdOrderByScheduledStartDesc(UUID mentorId, Pageable pageable);
    
    /**
     * Find sessions by mentee ID, ordered by start time (paginated)
     */
    Page<Session> findByMenteeIdOrderByScheduledStartDesc(UUID menteeId, Pageable pageable);
    
    /**
     * Find sessions by skill/topic
     */
    List<Session> findBySkillId(UUID skillId);

    /**
     * Find sessions scheduled between dates
     */
    @Query("SELECT s FROM Session s WHERE s.scheduledStart BETWEEN :startDate AND :endDate")
    List<Session> findByScheduledStartBetween(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /**
     * Find blocking sessions for a mentor in a time range, excluding the session being confirmed.
     */
    @Query("SELECT s FROM Session s WHERE s.mentorId = :mentorId " +
           "AND s.id <> :excludedSessionId " +
           "AND s.status IN ('CONFIRMED', 'SCHEDULED', 'IN_PROGRESS') " +
           "AND s.scheduledStart < :endTime " +
           "AND s.scheduledEnd > :startTime")
    List<Session> findBlockingSessions(@Param("mentorId") UUID mentorId,
                                       @Param("startTime") ZonedDateTime startTime,
                                       @Param("endTime") ZonedDateTime endTime,
                                       @Param("excludedSessionId") UUID excludedSessionId);

    /**
     * Find upcoming sessions for mentor
     */
    @Query("SELECT s FROM Session s WHERE s.mentorId = :mentorId AND s.scheduledStart > :now AND s.status IN ('CONFIRMED', 'SCHEDULED')")
    List<Session> findUpcomingSessionsForMentor(@Param("mentorId") UUID mentorId, @Param("now") ZonedDateTime now);

    /**
     * Find upcoming sessions for mentee
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId = :menteeId AND s.scheduledStart > :now AND s.status IN ('CONFIRMED', 'SCHEDULED')")
    List<Session> findUpcomingSessionsForMentee(@Param("menteeId") UUID menteeId, @Param("now") ZonedDateTime now);

    /**
     * Find past sessions for mentor
     */
    @Query("SELECT s FROM Session s WHERE s.mentorId = :mentorId AND s.scheduledEnd < :now ORDER BY s.scheduledEnd DESC")
    List<Session> findPastSessionsForMentor(@Param("mentorId") UUID mentorId, @Param("now") ZonedDateTime now);

    /**
     * Find past sessions for mentee
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId = :menteeId AND s.scheduledEnd < :now ORDER BY s.scheduledEnd DESC")
    List<Session> findPastSessionsForMentee(@Param("menteeId") UUID menteeId, @Param("now") ZonedDateTime now);

    /**
     * Find upcoming sessions for mentee with pagination
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId = :menteeId AND s.scheduledStart > :now AND s.status IN ('CONFIRMED', 'SCHEDULED') ORDER BY s.scheduledStart ASC")
    Page<Session> findUpcomingSessionsForMentee(@Param("menteeId") UUID menteeId, @Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Find past sessions for mentee with pagination
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId = :menteeId AND s.scheduledEnd < :now ORDER BY s.scheduledEnd DESC")
    Page<Session> findPastSessionsForMentee(@Param("menteeId") UUID menteeId, @Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Find today's sessions for mentee with pagination
     */
    @Query("SELECT s FROM Session s WHERE s.menteeId = :menteeId AND s.scheduledStart >= :startOfDay AND s.scheduledStart < :endOfDay ORDER BY s.scheduledStart ASC")
    Page<Session> findTodaySessionsForMentee(@Param("menteeId") UUID menteeId, @Param("startOfDay") ZonedDateTime startOfDay, @Param("endOfDay") ZonedDateTime endOfDay, Pageable pageable);

    /**
     * Find sessions needing reminders
     */
    @Query("SELECT s FROM Session s WHERE s.reminderSent = false AND s.scheduledStart <= :reminderTime " +
           "AND s.scheduledStart > CURRENT_TIMESTAMP AND s.status = 'CONFIRMED'")
    List<Session> findSessionsNeedingReminder(@Param("reminderTime") ZonedDateTime reminderTime);

    /**
     * Find unpaid sessions
     */
    @Query("SELECT s FROM Session s WHERE s.paid = false AND s.status = 'completed'")
    List<Session> findUnpaidCompletedSessions();

    /**
     * Count sessions by mentor
     */
    long countByMentorId(UUID mentorId);

    /**
     * Count sessions by mentee
     */
    long countByMenteeId(UUID menteeId);

    /**
     * Count completed sessions by mentor
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.mentorId = :mentorId AND s.status = 'completed'")
    long countCompletedSessionsByMentor(@Param("mentorId") UUID mentorId);

    /**
     * Count completed sessions by mentee
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.menteeId = :menteeId AND s.status = 'completed'")
    long countCompletedSessionsByMentee(@Param("menteeId") UUID menteeId);

    /**
     * Find sessions with rating
     */
    @Query("SELECT s FROM Session s WHERE s.rating IS NOT NULL ORDER BY s.rating DESC")
    List<Session> findRatedSessions();

    /**
     * Calculate average rating for mentor
     */
    @Query("SELECT AVG(s.rating) FROM Session s WHERE s.mentorId = :mentorId AND s.rating IS NOT NULL")
    Double calculateAverageRatingForMentor(@Param("mentorId") UUID mentorId);
}
