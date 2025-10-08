package com.prosper.prospermentor.service;

import com.prosper.prospermentor.controller.dto.SessionDtos.CreateSessionRequestDto;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.repository.*;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import com.prosper.prospermentor.service.meeting.MeetingService;
import com.prosper.prospermentor.service.notification.SessionNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Session booking service handling the complete booking workflow using Session entity
 * Replaces the previous BookingService to use the unified Session approach
 */
@Service
@Slf4j
@Transactional
public class SessionBookingService {
    
    private final SessionRepository sessionRepository;
    private final ProfileRepository profileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MenteeProfileRepository menteeProfileRepository;
    private final SkillRepository skillRepository;
    private final MeetingService meetingService;
    private final SessionNotificationService notificationService;
    private final CalendarService calendarService;
    private final SubscriptionService subscriptionService;
    private final MpesaService mpesaService;

    public SessionBookingService(SessionRepository sessionRepository,
                                ProfileRepository profileRepository,
                                MentorProfileRepository mentorProfileRepository,
                                MenteeProfileRepository menteeProfileRepository,
                                SkillRepository skillRepository,
                                MeetingService meetingService,
                                SessionNotificationService notificationService,
                                CalendarService calendarService,
                                SubscriptionService subscriptionService,
                                MpesaService mpesaService) {
        this.sessionRepository = sessionRepository;
        this.profileRepository = profileRepository;
        this.mentorProfileRepository = mentorProfileRepository;
        this.menteeProfileRepository = menteeProfileRepository;
        this.skillRepository = skillRepository;
        this.meetingService = meetingService;
        this.notificationService = notificationService;
        this.calendarService = calendarService;
        this.subscriptionService = subscriptionService;
        this.mpesaService = mpesaService;
    }
    
    /**
     * Create a new session request (booking)
     */
    public Session createSessionRequest(CreateSessionRequestDto request) {
        log.info("Creating session request from mentee: {} to mentor: {}",
                request.getMenteeId(), request.getMentorId());

        // Check subscription limits
        UUID menteeId = request.getMenteeId();
        boolean canBook = subscriptionService.canBookSession(menteeId);

        if (!canBook) {
            log.warn("Mentee {} has maxed out their subscription. Payment required.", menteeId);
            throw new IllegalStateException("Subscription limit reached. Please upgrade your plan or make a payment.");
        }

        // Validate session request
        //validateSessionRequest(request);

        // Get entities
        Profile mentor = profileRepository.findById(request.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));

        Profile mentee = profileRepository.findById(request.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        // Validate roles
        if (mentor.getRole().trim().compareTo("mentor") != 0) {
            throw new IllegalArgumentException("User is not a mentor");
        }

        if (mentee.getRole().trim().compareTo("mentee") != 0) {
            throw new IllegalArgumentException("User is not a mentee");
        }

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        
        // Check mentor availability
        //validateMentorAvailability(mentor, request.getScheduledStart(), request.getScheduledEnd());
        
        // Create session
        Session session = new Session();
        session.setMentorId(request.getMentorId());
        session.setMenteeId(request.getMenteeId());
        session.setSkillId(request.getSkillId());
        session.setTitle( skill.getName());
        session.setDescription("Session requested by mentee");
        session.setScheduledStart(request.getScheduledStart());
        session.setScheduledEnd(request.getScheduledStart().plusHours(1));
        session.setMeetingPlatform(request.getMeetingPlatform());
        session.setMenteeMessage(request.getMenteeMessage());
        // Get mentor profile for hourly rate (since it's not in base Profile entity)
        MentorProfile mentorProfile = mentorProfileRepository.findById(request.getMentorId())
                .orElse(null);
        session.setPrice(mentorProfile != null ? mentorProfile.getHourlyRate() : null);
        session.setCurrency("KES");
        session.setStatus(Session.SessionStatus.PENDING);
        session.setPaymentStatus(Session.PaymentStatus.PENDING);
        
        session = sessionRepository.save(session);

        // Consume a session from subscription
        try {
            subscriptionService.consumeSession(menteeId);
            log.info("Consumed session from subscription for mentee {}", menteeId);
        } catch (Exception e) {
            log.error("Failed to consume session from subscription: {}", e.getMessage(), e);
            // Rollback session creation
            sessionRepository.delete(session);
            throw new IllegalStateException("Failed to consume session from subscription", e);
        }

        // Send notification to mentor
        try {
            notificationService.sendSessionNotificationToMentor(session, mentor, mentee, null, skill);
            session.setMentorNotificationSent(true);
            session = sessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to send mentor notification for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }

        log.info("Successfully created session request: {}", session.getId());
        return session;
    }
    
    /**
     * Confirm a session request (mentor action)
     */
    public Session confirmSession(UUID sessionId, String mentorResponse) {
        log.info("Confirming session: {}", sessionId);
        
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        if (session.getStatus() != Session.SessionStatus.PENDING) {
            throw new IllegalStateException("Can only confirm pending sessions");
        }
        
        // Confirm session
        session.confirm();
        session.setMentorResponse(mentorResponse);
        
        try {
            // Create meeting
            MeetingDetails meetingDetails = meetingService.createMeeting(session);
            session.setMeetingUrl(meetingDetails.getMeetingUrl());
            session.setMeetingId(meetingDetails.getMeetingId());
            session.setMeetingPassword(meetingDetails.getPassword());
            
            // Add to Google Calendar
            String calendarEventId = calendarService.createCalendarEvent(session, meetingDetails);
            session.setCalendarEventId(calendarEventId);
            
            session = sessionRepository.save(session);
            
            // Send confirmation to mentee and mentor
            Profile mentor = profileRepository.findById(session.getMentorId()).orElseThrow();
            Profile mentee = profileRepository.findById(session.getMenteeId()).orElseThrow();
            
            // Send confirmation to mentee
            notificationService.sendSessionConfirmationToMentee(session, mentee, mentor, meetingDetails);
            session.setMenteeNotificationSent(true);
            
            // Send confirmation to mentor
            notificationService.sendSessionConfirmationToMentor(session, mentor, mentee, meetingDetails);
            session.setMentorNotificationSent(true);
            
            session = sessionRepository.save(session);
            
            log.info("Successfully confirmed session: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to complete session confirmation for {}: {}", sessionId, e.getMessage(), e);
            // Rollback session status
            session.setStatus(Session.SessionStatus.PENDING);
            sessionRepository.save(session);
            throw new RuntimeException("Failed to confirm session", e);
        }
        
        return session;
    }
    
    /**
     * Cancel a session
     */
    public Session cancelSession(UUID sessionId, Session.CancelledBy cancelledBy, String reason) {
        log.info("Cancelling session: {} by: {}", sessionId, cancelledBy);
        
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        if (!session.canBeModified()) {
            throw new IllegalStateException("Cannot cancel session in status: " + session.getStatus());
        }
        
        // Cancel session
        session.cancel(cancelledBy, reason);
        
        try {
            // Cancel meeting if exists
            if (session.getMeetingId() != null) {
                meetingService.cancelMeeting(session.getMeetingId(), session.getMeetingPlatform());
            }
            
            // Remove from calendar if exists
            if (session.getCalendarEventId() != null) {
                calendarService.cancelCalendarEvent(session.getCalendarEventId());
            }
            
            session = sessionRepository.save(session);
            
            // Send cancellation notifications
            Profile mentor = profileRepository.findById(session.getMentorId()).orElseThrow();
            Profile mentee = profileRepository.findById(session.getMenteeId()).orElseThrow();
            
            notificationService.sendSessionCancellationNotification(session, mentor, mentee, reason);
            
            log.info("Successfully cancelled session: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to complete session cancellation for {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to cancel session", e);
        }
        
        return session;
    }
    
    /**
     * Get sessions for a mentor
     */
    @Transactional(readOnly = true)
    public Page<Session> getMentorSessions(UUID mentorId, Pageable pageable) {
        return sessionRepository.findByMentorIdOrderByScheduledStartDesc(mentorId, pageable);
    }
    
    /**
     * Get sessions for a mentee
     */
    @Transactional(readOnly = true)
    public Page<Session> getMenteeSessions(UUID menteeId, Pageable pageable) {
        return sessionRepository.findByMenteeIdOrderByScheduledStartDesc(menteeId, pageable);
    }
    
    /**
     * Get a session by ID
     */
    @Transactional(readOnly = true)
    public Session getSessionById(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
    }
    
    /**
     * Get sessions by skill/topic
     */
    @Transactional(readOnly = true)
    public Page<Session> getSessionsBySkill(UUID skillId, Pageable pageable) {
        // We need to implement pagination for this in the repository
        List<Session> sessions = sessionRepository.findBySkillId(skillId);
        // For now, convert to Page manually - ideally we'd add a pageable method to repository
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), sessions.size());
        List<Session> pageContent = sessions.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, sessions.size());
    }
    
    /**
     * Update session status (admin/system operation)
     */
    public Session updateSessionStatus(UUID sessionId, Session.SessionStatus newStatus) {
        log.info("Updating session {} status to: {}", sessionId, newStatus);
        
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        // Validate status transition
        if (!isValidStatusTransition(session.getStatus(), newStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + 
                    session.getStatus() + " to " + newStatus);
        }
        
        session.setStatus(newStatus);
        
        // Handle status-specific logic
        switch (newStatus) {
            case IN_PROGRESS -> session.start();
            case COMPLETED -> session.complete();
            case CANCELLED -> session.cancel(Session.CancelledBy.SYSTEM, "Status updated by system");
        }
        
        return sessionRepository.save(session);
    }
    
    /**
     * Get sessions that need reminders
     */
    @Transactional(readOnly = true)
    public List<Session> getSessionsNeedingReminders() {
        ZonedDateTime reminderTime = ZonedDateTime.now().plusHours(24);
        return sessionRepository.findSessionsNeedingReminder(reminderTime);
    }
    
    /**
     * Send reminder for a session
     */
    public void sendSessionReminder(UUID sessionId) {
        log.info("Sending reminder for session: {}", sessionId);
        
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        if (session.getReminderSent() || session.getStatus() != Session.SessionStatus.CONFIRMED) {
            return;
        }
        
        try {
            Profile mentor = profileRepository.findById(session.getMentorId()).orElseThrow();
            Profile mentee = profileRepository.findById(session.getMenteeId()).orElseThrow();
            
            MeetingDetails meetingDetails = MeetingDetails.builder()
                    .meetingUrl(session.getMeetingUrl())
                    .meetingId(session.getMeetingId())
                    .password(session.getMeetingPassword())
                    .build();
            
            notificationService.sendSessionReminder(session, mentor, mentee, meetingDetails);
            
            session.setReminderSent(true);
            sessionRepository.save(session);
            
            log.info("Successfully sent reminder for session: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to send reminder for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Check if user can book a session (checks subscription)
     */
    @Transactional(readOnly = true)
    public boolean canUserBookSession(UUID userId) {
        return subscriptionService.canBookSession(userId);
    }

    /**
     * Get remaining sessions count for a user
     */
    @Transactional(readOnly = true)
    public int getRemainingSessionsCount(UUID userId) {
        return subscriptionService.getRemainingSessionsCount(userId);
    }


    private void validateMentorAvailability(Profile mentor, ZonedDateTime startTime, ZonedDateTime endTime) {
        // Get mentor profile to check availability (since isAvailable is in MentorProfile, not base Profile)
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
        
        // Check if mentor is available
        if (mentorProfile != null && !mentorProfile.getIsAvailable()) {
            throw new IllegalArgumentException("Mentor is not currently available for sessions");
        }
        
        // Check for conflicting sessions
        List<Session> conflictingSessions = sessionRepository.findConflictingSessions(
                mentor.getId(), startTime, endTime);
        
        if (!conflictingSessions.isEmpty()) {
            throw new IllegalArgumentException("Mentor is not available during the requested time slot");
        }
    }
    
    /**
     * Validate status transition
     */
    private boolean isValidStatusTransition(Session.SessionStatus currentStatus, Session.SessionStatus newStatus) {
        // Define valid transitions
        return switch (currentStatus) {
            case PENDING -> newStatus == Session.SessionStatus.CONFIRMED || 
                          newStatus == Session.SessionStatus.CANCELLED;
            case CONFIRMED, SCHEDULED -> newStatus == Session.SessionStatus.IN_PROGRESS || 
                                       newStatus == Session.SessionStatus.CANCELLED ||
                                       newStatus == Session.SessionStatus.NO_SHOW;
            case IN_PROGRESS -> newStatus == Session.SessionStatus.COMPLETED || 
                              newStatus == Session.SessionStatus.CANCELLED;
            case COMPLETED, CANCELLED, NO_SHOW -> false; // Terminal states
        };
    }
}
