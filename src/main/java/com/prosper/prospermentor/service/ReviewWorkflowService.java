package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.repository.*;
import com.prosper.prospermentor.util.PhoneNumberUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class ReviewWorkflowService {

    private static final String MENTOR_AFTER_SESSION_TEMPLATE = "prosper_mentor_after_session_form";
    private static final String MENTEE_AFTER_SESSION_TEMPLATE = "prosper_mentee_after_session_form";
    private static final int FIT_REVIEW_TRIGGER_SESSION_COUNT = 3;

    private final ReviewCycleRepository reviewCycleRepository;
    private final ReviewRequestRepository reviewRequestRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    private final CompanyProgramRepository companyProgramRepository;
    private final SessionRepository sessionRepository;
    private final NautixWhatsAppService nautixWhatsAppService;

    @Value("${review.whatsapp.fit-template-name:}")
    private String fitReviewTemplateName;

    @Value("${review.whatsapp.reminder-template-name:}")
    private String reviewReminderTemplateName;

    public ReviewWorkflowService(ReviewCycleRepository reviewCycleRepository,
                                 ReviewRequestRepository reviewRequestRepository,
                                 CompanyProgramParticipantRepository companyProgramParticipantRepository,
                                 CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository,
                                 CompanyProgramRepository companyProgramRepository,
                                 SessionRepository sessionRepository,
                                 NautixWhatsAppService nautixWhatsAppService) {
        this.reviewCycleRepository = reviewCycleRepository;
        this.reviewRequestRepository = reviewRequestRepository;
        this.companyProgramParticipantRepository = companyProgramParticipantRepository;
        this.companyProgramMentorAssignmentRepository = companyProgramMentorAssignmentRepository;
        this.companyProgramRepository = companyProgramRepository;
        this.sessionRepository = sessionRepository;
        this.nautixWhatsAppService = nautixWhatsAppService;
    }

    public ReviewCycle openSessionReviewCycle(Session session, Profile mentor, Profile mentee) {
        return reviewCycleRepository.findBySession_IdAndType(session.getId(), ReviewCycle.ReviewType.SESSION)
                .orElseGet(() -> createAndSendSessionReviewCycle(session, mentor, mentee));
    }

    public ReviewCycle maybeOpenFitReviewCycle(Session session, Profile mentor, Profile mentee) {
        if (session.getCompanyProgramParticipantId() == null) {
            return null;
        }
        if (!StringUtils.hasText(fitReviewTemplateName)) {
            log.debug("Fit review template name is not configured; skipping fit review check for session {}", session.getId());
            return null;
        }

        CompanyProgramParticipant participant = companyProgramParticipantRepository
                .findById(session.getCompanyProgramParticipantId())
                .orElse(null);
        if (participant == null) {
            return null;
        }

        CompanyProgramMentorAssignment assignment = companyProgramMentorAssignmentRepository
                .findFirstByParticipant_IdAndMentor_IdOrderByAssignedAtDesc(participant.getId(), mentor.getId())
                .orElse(null);
        if (assignment == null || assignment.getMentorProfile() == null) {
            return null;
        }

        UUID assignmentMentorId = assignment.getMentorProfile().getId();
        if (assignmentMentorId == null || !assignmentMentorId.equals(mentor.getId())) {
            return null;
        }

        if (reviewCycleRepository.findByMentorAssignment_IdAndType(assignment.getId(), ReviewCycle.ReviewType.FIT).isPresent()) {
            return null;
        }

        long completedSharedSessions = sessionRepository.countByCompanyProgramParticipantIdAndMentorIdAndStatus(
                participant.getId(),
                mentor.getId(),
                Session.SessionStatus.COMPLETED
        );

        if (completedSharedSessions < FIT_REVIEW_TRIGGER_SESSION_COUNT) {
            return null;
        }

        return createAndSendFitReviewCycle(participant, assignment, mentor, mentee);
    }

    public int sendDueReminders() {
        if (!StringUtils.hasText(reviewReminderTemplateName)) {
            log.debug("Review reminder template name is not configured; skipping review reminders");
            return 0;
        }

        List<ReviewRequest> candidates = reviewRequestRepository.findByStatusInAndReviewCycle_StatusInAndReviewCycle_ExpiresAtAfter(
                List.of(
                        ReviewRequest.ReviewRequestStatus.PENDING,
                        ReviewRequest.ReviewRequestStatus.SENT,
                        ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED
                ),
                List.of(
                        ReviewCycle.ReviewCycleStatus.OPEN,
                        ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED
                ),
                LocalDateTime.now()
        );

        int remindersSent = 0;
        for (ReviewRequest request : candidates) {
            ReminderStage stage = resolveReminderStage(request, LocalDateTime.now());
            if (stage == ReminderStage.NONE) {
                continue;
            }

            if (sendReminder(request, stage)) {
                remindersSent++;
            }
        }

        return remindersSent;
    }

    private ReviewCycle createAndSendSessionReviewCycle(Session session, Profile mentor, Profile mentee) {
        LocalDateTime now = LocalDateTime.now();

        ReviewCycle cycle = new ReviewCycle();
        cycle.setType(ReviewCycle.ReviewType.SESSION);
        cycle.setSession(session);
        cycle.setMentorProfile(mentor);
        cycle.setMenteeProfile(mentee);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.OPEN);
        cycle.setOpenedAt(now);
        cycle.setExpiresAt(now.plusHours(48));

        if (session.getCompanyProgramParticipantId() != null) {
            CompanyProgramParticipant participant = companyProgramParticipantRepository
                    .findById(session.getCompanyProgramParticipantId())
                    .orElse(null);
            if (participant != null) {
                cycle.setParticipant(participant);
                cycle.setCompanyProgram(participant.getCompanyProgram());
                CompanyProgramMentorAssignment assignment = companyProgramMentorAssignmentRepository
                        .findFirstByParticipant_IdAndMentor_IdOrderByAssignedAtDesc(participant.getId(), mentor.getId())
                        .orElse(null);
                cycle.setMentorAssignment(assignment);
            }
        } else if (session.getCompanyProgramId() != null) {
            companyProgramRepository.findById(session.getCompanyProgramId())
                    .ifPresent(cycle::setCompanyProgram);
        }

        cycle = reviewCycleRepository.save(cycle);

        ReviewRequest mentorRequest = buildSessionReviewRequest(
                cycle,
                mentor,
                ReviewRequest.ReviewRole.MENTOR,
                mentee,
                ReviewRequest.ReviewRole.MENTEE,
                MENTOR_AFTER_SESSION_TEMPLATE
        );
        ReviewRequest menteeRequest = buildSessionReviewRequest(
                cycle,
                mentee,
                ReviewRequest.ReviewRole.MENTEE,
                mentor,
                ReviewRequest.ReviewRole.MENTOR,
                MENTEE_AFTER_SESSION_TEMPLATE
        );

        mentorRequest = reviewRequestRepository.save(mentorRequest);
        menteeRequest = reviewRequestRepository.save(menteeRequest);

        sendInvite(mentorRequest);
        sendInvite(menteeRequest);

        log.info("Opened session review cycle {} for session {}", cycle.getId(), session.getId());
        return cycle;
    }

    private ReviewCycle createAndSendFitReviewCycle(CompanyProgramParticipant participant,
                                                    CompanyProgramMentorAssignment assignment,
                                                    Profile mentor,
                                                    Profile mentee) {
        LocalDateTime now = LocalDateTime.now();

        ReviewCycle cycle = new ReviewCycle();
        cycle.setType(ReviewCycle.ReviewType.FIT);
        cycle.setMentorAssignment(assignment);
        cycle.setParticipant(participant);
        cycle.setCompanyProgram(participant.getCompanyProgram());
        cycle.setMentorProfile(mentor);
        cycle.setMenteeProfile(mentee);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.OPEN);
        cycle.setOpenedAt(now);
        cycle.setExpiresAt(now.plusHours(48));
        cycle = reviewCycleRepository.save(cycle);

        ReviewRequest mentorRequest = buildReviewRequest(
                cycle,
                mentor,
                ReviewRequest.ReviewRole.MENTOR,
                mentee,
                ReviewRequest.ReviewRole.MENTEE,
                fitReviewTemplateName
        );
        ReviewRequest menteeRequest = buildReviewRequest(
                cycle,
                mentee,
                ReviewRequest.ReviewRole.MENTEE,
                mentor,
                ReviewRequest.ReviewRole.MENTOR,
                fitReviewTemplateName
        );

        mentorRequest = reviewRequestRepository.save(mentorRequest);
        menteeRequest = reviewRequestRepository.save(menteeRequest);

        sendInvite(mentorRequest);
        sendInvite(menteeRequest);

        log.info("Opened fit review cycle {} for assignment {}", cycle.getId(), assignment.getId());
        return cycle;
    }

    private ReviewRequest buildReviewRequest(ReviewCycle cycle,
                                             Profile reviewer,
                                             ReviewRequest.ReviewRole reviewerRole,
                                             Profile target,
                                             ReviewRequest.ReviewRole targetRole,
                                             String templateName) {
        ReviewRequest request = new ReviewRequest();
        request.setReviewCycle(cycle);
        request.setReviewerProfile(reviewer);
        request.setReviewerRole(reviewerRole);
        request.setTargetProfile(target);
        request.setTargetRole(targetRole);
        request.setChannel(ReviewRequest.ReviewChannel.WHATSAPP);
        request.setStatus(ReviewRequest.ReviewRequestStatus.PENDING);
        request.setTemplateName(templateName);
        request.setFlowToken("review-flow-" + UUID.randomUUID());
        request.setSubmissionToken(UUID.randomUUID().toString());
        return request;
    }

    private ReviewRequest buildSessionReviewRequest(ReviewCycle cycle,
                                                    Profile reviewer,
                                                    ReviewRequest.ReviewRole reviewerRole,
                                                    Profile target,
                                                    ReviewRequest.ReviewRole targetRole,
                                                    String templateName) {
        return buildReviewRequest(cycle, reviewer, reviewerRole, target, targetRole, templateName);
    }

    private void sendInvite(ReviewRequest request) {
        String phone = normalizePhoneNumber(request.getReviewerProfile());
        if (phone == null) {
            markDeliveryFailed(request, "Reviewer has no valid phone number");
            return;
        }

        try {
            nautixWhatsAppService.sendTemplateMessage(
                    request.getTemplateName(),
                    phone,
                    List.of(
                            firstName(request.getReviewerProfile()),
                            fullName(request.getTargetProfile())
                    ),
                    request.getFlowToken(),
                    Map.of(
                            "review_request_id", request.getId().toString(),
                            "review_token", request.getSubmissionToken(),
                            "reviewer_role", request.getReviewerRole().name(),
                            "review_cycle_type", request.getReviewCycle().getType().name()
                    )
            );

            LocalDateTime now = LocalDateTime.now();
            request.setStatus(ReviewRequest.ReviewRequestStatus.SENT);
            request.setSentAt(now);
            request.setLastOutboundAt(now);
            request.setLastError(null);
            reviewRequestRepository.save(request);
        } catch (Exception e) {
            markDeliveryFailed(request, e.getMessage());
            log.error("Failed to send review invite {} for cycle {}: {}",
                    request.getTemplateName(),
                    request.getReviewCycle().getId(),
                    e.getMessage(),
                    e);
        }
    }

    private boolean sendReminder(ReviewRequest request, ReminderStage stage) {
        String phone = normalizePhoneNumber(request.getReviewerProfile());
        if (phone == null) {
            markDeliveryFailed(request, "Reviewer has no valid phone number");
            return false;
        }

        try {
            nautixWhatsAppService.sendTemplateMessage(
                    reviewReminderTemplateName,
                    phone,
                    List.of(
                            fullName(request.getTargetProfile()),
                            stage.timeRemainingLabel
                    ),
                    request.getFlowToken(),
                    Map.of(
                            "review_request_id", request.getId().toString(),
                            "review_token", request.getSubmissionToken(),
                            "reviewer_role", request.getReviewerRole().name(),
                            "review_cycle_type", request.getReviewCycle().getType().name()
                    )
            );

            LocalDateTime now = LocalDateTime.now();
            request.setStatus(ReviewRequest.ReviewRequestStatus.SENT);
            request.setLastReminderAt(now);
            request.setLastOutboundAt(now);
            request.setLastError(null);
            reviewRequestRepository.save(request);
            log.info("Sent {} review reminder for request {}", stage.name(), request.getId());
            return true;
        } catch (Exception e) {
            markDeliveryFailed(request, "Reminder send failed: " + e.getMessage());
            log.error("Failed to send {} review reminder for request {}: {}", stage.name(), request.getId(), e.getMessage(), e);
            return false;
        }
    }

    private ReminderStage resolveReminderStage(ReviewRequest request, LocalDateTime now) {
        ReviewCycle cycle = request.getReviewCycle();
        if (cycle == null || cycle.getOpenedAt() == null || cycle.getExpiresAt() == null || !now.isBefore(cycle.getExpiresAt())) {
            return ReminderStage.NONE;
        }

        LocalDateTime firstReminderAt = cycle.getOpenedAt().plusHours(24);
        LocalDateTime finalReminderAt = cycle.getOpenedAt().plusHours(44);
        LocalDateTime lastReminderAt = request.getLastReminderAt();

        if (!now.isBefore(finalReminderAt)
                && (lastReminderAt == null || lastReminderAt.isBefore(finalReminderAt))) {
            return ReminderStage.FINAL;
        }

        if (!now.isBefore(firstReminderAt) && lastReminderAt == null) {
            return ReminderStage.FIRST;
        }

        return ReminderStage.NONE;
    }

    private void markDeliveryFailed(ReviewRequest request, String reason) {
        request.setStatus(ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED);
        request.setLastError(limit(reason));
        request.setLastOutboundAt(LocalDateTime.now());
        reviewRequestRepository.save(request);
    }

    private String normalizePhoneNumber(Profile profile) {
        if (profile == null || profile.getPhone() == null || profile.getPhone().isBlank()) {
            return null;
        }
        return PhoneNumberUtil.normalizeToE164(profile.getPhone());
    }

    private String firstName(Profile profile) {
        if (profile.getFirstName() != null && !profile.getFirstName().isBlank()) {
            return profile.getFirstName().trim();
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername().trim();
        }
        return "there";
    }

    private String fullName(Profile profile) {
        String first = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String last = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String combined = (first + " " + last).trim();
        if (!combined.isBlank()) {
            return combined;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername().trim();
        }
        return "User";
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown delivery failure";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private enum ReminderStage {
        NONE(""),
        FIRST("about 24 hours"),
        FINAL("about 4 hours");

        private final String timeRemainingLabel;

        ReminderStage(String timeRemainingLabel) {
            this.timeRemainingLabel = timeRemainingLabel;
        }
    }
}
