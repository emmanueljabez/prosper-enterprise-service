package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled task service for handling booking-related background tasks
 * Handles reminders, cleanup, and other periodic operations
 */
@Service
@Slf4j
public class ScheduledTaskService {
    
    private final SessionBookingService sessionBookingService;
    private final ReviewLifecycleService reviewLifecycleService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final CompanyProgramMatchWorkspaceService matchWorkspaceService;
    
    public ScheduledTaskService(SessionBookingService sessionBookingService,
                                ReviewLifecycleService reviewLifecycleService,
                                ReviewWorkflowService reviewWorkflowService,
                                CompanyProgramMatchWorkspaceService matchWorkspaceService) {
        this.sessionBookingService = sessionBookingService;
        this.reviewLifecycleService = reviewLifecycleService;
        this.reviewWorkflowService = reviewWorkflowService;
        this.matchWorkspaceService = matchWorkspaceService;
    }
    
    /**
     * Send reminders for upcoming sessions (runs every hour)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3,600,000 ms
    public void sendSessionReminders() {
        log.info("Running session reminder task");
        
        try {
            List<Session> sessionsNeedingReminders = sessionBookingService.getSessionsNeedingReminders();
            
            log.info("Found {} sessions needing reminders", sessionsNeedingReminders.size());
            
            for (Session session : sessionsNeedingReminders) {
                try {
                    sessionBookingService.sendSessionReminder(session.getId());
                    log.info("Sent reminder for session: {}", session.getId());
                } catch (Exception e) {
                    log.error("Failed to send reminder for session {}: {}", 
                            session.getId(), e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in session reminder task: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Cleanup expired pending sessions (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredSessions() {
        log.info("Running expired sessions cleanup task");
        
        try {
            // TODO: Implement cleanup logic for expired pending sessions
            // Sessions that are pending for more than 24 hours should be auto-cancelled
            
        } catch (Exception e) {
            log.error("Error in expired sessions cleanup task: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Generate session statistics and reports (runs daily at 1 AM)
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void generateDailyReports() {
        log.info("Running daily reports generation task");
        
        try {
            // TODO: Implement daily statistics generation
            // - Session completion rates
            // - Revenue statistics
            // - Popular topics/mentors
            
        } catch (Exception e) {
            log.error("Error in daily reports generation task: {}", e.getMessage(), e);
        }
    }

    /**
     * Expire review cycles whose 48-hour response window has elapsed (runs every 15 minutes).
     */
    @Scheduled(fixedRate = 900000)
    public void expireDueReviewCycles() {
        log.info("Running review cycle expiry task");

        try {
            int expiredCycles = reviewLifecycleService.expireOverdueCycles();
            log.info("Expired {} review cycles", expiredCycles);
        } catch (Exception e) {
            log.error("Error in review cycle expiry task: {}", e.getMessage(), e);
        }
    }

    /**
     * Send review reminders for pending WhatsApp review requests (runs every 15 minutes).
     */
    @Scheduled(fixedRate = 900000, initialDelay = 120000)
    public void sendReviewReminders() {
        log.info("Running review reminder task");

        try {
            int remindersSent = reviewWorkflowService.sendDueReminders();
            log.info("Sent {} review reminders", remindersSent);
        } catch (Exception e) {
            log.error("Error in review reminder task: {}", e.getMessage(), e);
        }
    }

    /**
     * Auto-assign mentors after employee selection windows expire (runs every 5 minutes).
     */
    @Scheduled(cron = "${company-programs.employee-select.expiry-cron:0 */5 * * * *}")
    public void processExpiredEmployeeMentorSelections() {
        log.info("Running employee mentor selection expiry task");

        try {
            int autoAssigned = matchWorkspaceService.processExpiredEmployeeSelections();
            log.info("Auto-assigned mentors for {} expired employee selections", autoAssigned);
        } catch (Exception e) {
            log.error("Error in employee mentor selection expiry task: {}", e.getMessage(), e);
        }
    }
}
