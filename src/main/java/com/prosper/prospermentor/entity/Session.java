package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Session entity representing mentoring sessions
 * Maps to the sessions table in Supabase
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    /**
     * Primary key - UUID with auto-generation
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Mentor ID - foreign key to mentor_profiles
     */
    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    /**
     * Mentee ID - foreign key to mentee_profiles  
     */
    @Column(name = "mentee_id", nullable = false)
    private UUID menteeId;

    /**
     * Skill/Topic ID - foreign key to skills table
     */
    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    /**
     * Optional company program linkage for corporate bookings
     */
    @Column(name = "company_program_id")
    private UUID companyProgramId;

    /**
     * Optional company program participant linkage for corporate bookings
     */
    @Column(name = "company_program_participant_id")
    private UUID companyProgramParticipantId;

    /**
     * Optional employee session allocation linkage for company-funded sessions
     */
    @Column(name = "corporate_allocation_id")
    private UUID corporateAllocationId;

    /**
     * Session title
     */
    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Session description
     */
    @Column(name = "description")
    private String description;

    /**
     * Scheduled start time
     */
    @Column(name = "scheduled_start", nullable = false)
    private ZonedDateTime scheduledStart;

    /**
     * Scheduled end time
     */
    @Column(name = "scheduled_end", nullable = false)
    private ZonedDateTime scheduledEnd;

    /**
     * Session booking and execution status
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.PENDING;

    /**
     * Meeting platform preference (ZOOM, GOOGLE_MEET)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform")
    private MeetingPlatform meetingPlatform = MeetingPlatform.GOOGLE_MEET;

    /**
     * Meeting URL (Zoom, Teams, etc.)
     */
    @Column(name = "meeting_url")
    private String meetingUrl;

    /**
     * Meeting ID for reference
     */
    @Column(name = "meeting_id", length = 100)
    private String meetingId;

    /**
     * Meeting password if required
     */
    @Column(name = "meeting_password", length = 50)
    private String meetingPassword;

    /**
     * Session notes
     */
    @Column(name = "notes")
    private String notes;

    /**
     * Session rating (1-5)
     */
    @Column(name = "rating")
    private Integer rating;

    /**
     * Session feedback
     */
    @Column(name = "feedback")
    private String feedback;

    /**
     * Session price
     */
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Currency code (USD, EUR, etc.)
     */
    @Column(name = "currency", length = 3)
    private String currency = "USD";

    /**
     * Payment status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /**
     * Whether the session has been paid for
     */
    @Column(name = "paid")
    private Boolean paid = false;

    /**
     * Whether reminder has been sent
     */
    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;

    /**
     * Message from mentee when requesting the session
     */
    @Column(name = "mentee_message", length = 1000)
    private String menteeMessage;

    @Column(name = "booking_primary_goal", columnDefinition = "TEXT")
    private String bookingPrimaryGoal;

    @Column(name = "booking_already_tried", columnDefinition = "TEXT")
    private String bookingAlreadyTried;

    @Column(name = "booking_success_looks_like", columnDefinition = "TEXT")
    private String bookingSuccessLooksLike;

    @Column(name = "booking_context_document", columnDefinition = "TEXT")
    private String bookingContextDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", length = 30)
    private BookingSource bookingSource = BookingSource.ENTERPRISE;

    /**
     * Mentor's response to the session request
     */
    @Column(name = "mentor_response", length = 1000)
    private String mentorResponse;

    /**
     * Google Calendar event ID
     */
    @Column(name = "calendar_event_id", length = 100)
    private String calendarEventId;

    /**
     * Workflow timestamps
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "corporate_allocation_consumed_at")
    private LocalDateTime corporateAllocationConsumedAt;

    @Column(name = "corporate_allocation_returned_at")
    private LocalDateTime corporateAllocationReturnedAt;

    /**
     * Individual entitlement consumed for this booking when it is not company-funded.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_source", length = 40)
    private EntitlementSource entitlementSource;

    @Column(name = "consumed_subscription_id")
    private UUID consumedSubscriptionId;

    @Column(name = "consumed_subscription_addon_id")
    private UUID consumedSubscriptionAddonId;

    @Column(name = "entitlement_returned_at")
    private LocalDateTime entitlementReturnedAt;

    /**
     * Cancellation reason
     */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * Who cancelled the session
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;

    /**
     * Email notification tracking
     */
    @Column(name = "mentee_notification_sent")
    private Boolean menteeNotificationSent = false;

    @Column(name = "mentor_notification_sent")
    private Boolean mentorNotificationSent = false;

    /**
     * Reference to mentor profile
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mentor_id", insertable = false, updatable = false)
    private MentorProfile mentor;

    /**
     * Reference to mentee profile
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mentee_id", insertable = false, updatable = false)
    private MenteeProfile mentee;

    /**
     * Reference to skill/topic
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", insertable = false, updatable = false)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id", insertable = false, updatable = false)
    private CompanyProgram companyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_participant_id", insertable = false, updatable = false)
    private CompanyProgramParticipant companyProgramParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_allocation_id", insertable = false, updatable = false)
    private EmployeeSessionAllocation corporateAllocation;

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Business Logic Methods

    /**
     * Confirm the session request (mentor action)
     */
    public void confirm() {
        if (this.status != SessionStatus.PENDING) {
            throw new IllegalStateException("Can only confirm pending sessions");
        }
        this.status = SessionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Cancel the session
     */
    public void cancel(CancelledBy cancelledBy, String reason) {
        if (this.status == SessionStatus.CANCELLED || this.status == SessionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel session in status: " + this.status);
        }
        this.status = SessionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
    }

    /**
     * Start the session (mark as in progress)
     */
    public void start() {
        if (this.status != SessionStatus.CONFIRMED) {
            throw new IllegalStateException("Can only start confirmed sessions");
        }
        this.status = SessionStatus.IN_PROGRESS;
    }

    /**
     * Complete the session
     */
    public void complete() {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only complete sessions that are in progress");
        }
        this.status = SessionStatus.COMPLETED;
    }

    /**
     * Check if session can be modified
     */
    public boolean canBeModified() {
        return this.status == SessionStatus.PENDING || this.status == SessionStatus.CONFIRMED;
    }

    /**
     * Check if session is in the future
     */
    public boolean isFutureSession() {
        return this.scheduledStart.isAfter(ZonedDateTime.now());
    }

    /**
     * Get session duration in minutes
     */
    public long getDurationMinutes() {
        return java.time.Duration.between(this.scheduledStart, this.scheduledEnd).toMinutes();
    }

    // Enums

    /**
     * Session status enumeration - covers both booking workflow and session execution
     */
    public enum SessionStatus {
        PENDING,        // Waiting for mentor confirmation (booking phase)
        CONFIRMED,      // Mentor confirmed, meeting scheduled (booking phase)
        SCHEDULED,      // Alternative name for CONFIRMED (legacy support)
        IN_PROGRESS,    // Session is currently happening (execution phase)
        COMPLETED,      // Session finished successfully (execution phase)
        CANCELLED,      // Session was cancelled (any phase)
        NO_SHOW        // Mentee didn't show up (execution phase)
    }

    /**
     * Meeting platform enumeration
     */
    public enum MeetingPlatform {
        GOOGLE_MEET("Google Meet"),
        ZOOM("Zoom");
        
        private final String displayName;
        
        MeetingPlatform(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Payment status enumeration
     */
    public enum PaymentStatus {
        PENDING,
        PAID,
        REFUNDED,
        FAILED
    }

    /**
     * Who cancelled the session
     */
    public enum CancelledBy {
        MENTEE,
        MENTOR,
        SYSTEM,
        ADMIN
    }

    public enum EntitlementSource {
        CORPORATE_ALLOCATION,
        PERSONAL_CREDIT,
        INDIVIDUAL_SUBSCRIPTION,
        SUBSCRIPTION_ADDON
    }

    public enum BookingSource {
        ENTERPRISE,
        B2C
    }
}
