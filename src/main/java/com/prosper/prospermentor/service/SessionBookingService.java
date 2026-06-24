package com.prosper.prospermentor.service;

import com.prosper.prospermentor.controller.dto.SessionDtos.CompleteSessionRequestDto;
import com.prosper.prospermentor.controller.dto.SessionDtos.OutcomeActionItemRequestDto;
import com.prosper.prospermentor.dto.CreateSessionRequestDto;
import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.exception.SessionBookingException;
import com.prosper.prospermentor.repository.*;
import com.prosper.prospermentor.service.meeting.MeetingDetails;
import com.prosper.prospermentor.service.meeting.MeetingService;
import com.prosper.prospermentor.service.notification.SessionNotificationService;
import com.prosper.prospermentor.util.PhoneNumberUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Session booking service handling the complete booking workflow using Session entity
 * Replaces the previous BookingService to use the unified Session approach
 */
@Service
@Slf4j
@Transactional
public class SessionBookingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MENTEE_ALTERNATIVE_PROPOSED_TEMPLATE = "prosper_mentee_session_alternative_proposed";
    private static final String MENTOR_ALTERNATIVE_ACCEPTED_TEMPLATE = "prosper_mentor_alternative_accepted";
    private static final String MENTOR_ALTERNATIVE_DECLINED_TEMPLATE = "prosper_mentor_alternative_declined";
    private static final String MENTOR_SUPPORT_CONTACT_REQUESTED_TEMPLATE = "prosper_mentor_support_contact_requested";
    private static final String MENTEE_SUPPORT_CONTACT_REQUESTED_TEMPLATE = "prosper_mentee_support_contact_requested";
    
    private final SessionRepository sessionRepository;
    private final ProfileRepository profileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MenteeProfileRepository menteeProfileRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    private final SessionOutcomeRepository sessionOutcomeRepository;
    private final SkillRepository skillRepository;
    private final MeetingService meetingService;
    private final SessionNotificationService notificationService;
    private final CalendarService calendarService;
    private final SubscriptionService subscriptionService;
    private final MpesaService mpesaService;
    private final CurrencyService currencyService;
    private final NautixWhatsAppService nautixWhatsAppService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final ParticipantConsentService participantConsentService;
    private final JourneyInstanceService journeyInstanceService;
    private final EmployeeSessionAllocationService employeeSessionAllocationService;
    private final PersonalSessionCreditService personalSessionCreditService;
    private final SessionProposalRepository sessionProposalRepository;
    private final SessionSupportRequestRepository sessionSupportRequestRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${support.mentor-experience.whatsapp:}")
    private String mentorExperienceWhatsApp;

    @Value("${support.mentee-experience.whatsapp:}")
    private String menteeExperienceWhatsApp;

    public SessionBookingService(SessionRepository sessionRepository,
                                ProfileRepository profileRepository,
                                MentorProfileRepository mentorProfileRepository,
                                MenteeProfileRepository menteeProfileRepository,
                                SkillRepository skillRepository,
                                MeetingService meetingService,
                                SessionNotificationService notificationService,
                                CalendarService calendarService,
                                SubscriptionService subscriptionService,
                                MpesaService mpesaService,
                                CurrencyService currencyService,
                                CompanyProgramParticipantRepository companyProgramParticipantRepository,
                                CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository,
                                SessionOutcomeRepository sessionOutcomeRepository,
                                NautixWhatsAppService nautixWhatsAppService,
                                ReviewWorkflowService reviewWorkflowService,
                                ParticipantConsentService participantConsentService,
                                JourneyInstanceService journeyInstanceService,
                                EmployeeSessionAllocationService employeeSessionAllocationService,
                                PersonalSessionCreditService personalSessionCreditService,
                                SessionProposalRepository sessionProposalRepository,
                                SessionSupportRequestRepository sessionSupportRequestRepository) {
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
        this.currencyService = currencyService;
        this.companyProgramParticipantRepository = companyProgramParticipantRepository;
        this.companyProgramMentorAssignmentRepository = companyProgramMentorAssignmentRepository;
        this.sessionOutcomeRepository = sessionOutcomeRepository;
        this.nautixWhatsAppService = nautixWhatsAppService;
        this.reviewWorkflowService = reviewWorkflowService;
        this.participantConsentService = participantConsentService;
        this.journeyInstanceService = journeyInstanceService;
        this.employeeSessionAllocationService = employeeSessionAllocationService;
        this.personalSessionCreditService = personalSessionCreditService;
        this.sessionProposalRepository = sessionProposalRepository;
        this.sessionSupportRequestRepository = sessionSupportRequestRepository;
    }
    
    /**
     * Create a new session request (booking)
     */
    public Session createSessionRequest(CreateSessionRequestDto request) {
        log.info("Creating session request from mentee: {} to mentor: {}",
                request.getMenteeId(), request.getMentorId());

        // Check subscription eligibility with detailed information
        UUID menteeId = UUID.fromString(request.getMenteeId());
        SessionBookingEligibility eligibility = subscriptionService.checkSessionBookingEligibility(menteeId);

        if (!eligibility.isCanBook()) {
            log.warn("Mentee {} cannot book session. Reason: {} - {}",
                    menteeId, eligibility.getReason(), eligibility.getMessage());
            throw new SessionBookingException(eligibility);
        }

        log.info("Mentee {} is eligible to book. {}", menteeId, eligibility.getMessage());

        // Validate session request
        //validateSessionRequest(request);

        // Get entities
        Profile mentor = profileRepository.findById(UUID.fromString(request.getMentorId()))
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));

        Profile mentee = profileRepository.findById(UUID.fromString(request.getMenteeId()))
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        // Validate roles
        if (mentor.getRole().trim().compareTo("mentor") != 0) {
            throw new IllegalArgumentException("User is not a mentor");
        }

        if (mentee.getRole().trim().compareTo("mentee") != 0) {
            throw new IllegalArgumentException("User is not a mentee");
        }

        Skill skill = skillRepository.findById(UUID.fromString(request.getSkillId()))
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));

        CompanyProgramParticipant participantContext = resolveParticipantContext(request, mentor, mentee);
        
        // Check mentor availability
        //validateMentorAvailability(mentor, request.getScheduledStart(), request.getScheduledEnd());
        
        // Create session
        Session session = new Session();
        session.setMentorId(UUID.fromString((request.getMentorId())));
        session.setMenteeId(UUID.fromString(request.getMenteeId()));
        session.setSkillId(UUID.fromString(request.getSkillId()));
        session.setCompanyProgramId(participantContext != null ? participantContext.getCompanyProgram().getId() : parseOptionalUuid(request.getCompanyProgramId()));
        session.setCompanyProgramParticipantId(participantContext != null ? participantContext.getId() : parseOptionalUuid(request.getCompanyProgramParticipantId()));
        session.setTitle( skill.getName());
        session.setDescription("Session requested by mentee");
        session.setScheduledStart(request.getScheduledStart());
        session.setScheduledEnd(request.getScheduledStart().plusMinutes(resolveSessionDurationMinutes(eligibility)));
        session.setMeetingPlatform(request.getMeetingPlatform());
        session.setMenteeMessage(request.getMenteeMessage());
        applyQuestionnaireResponses(session, request.getQuestionnaireResponses());

        // Get mentor profile for hourly rate (since it's not in base Profile entity)
        MentorProfile mentorProfile = mentorProfileRepository.findById(UUID.fromString(request.getMentorId()))
                .orElse(null);

        // Handle currency conversion
        String targetCurrency = (request.getCurrency() != null && !request.getCurrency().trim().isEmpty())
                               ? request.getCurrency().toUpperCase()
                               : currencyService.getDefaultCurrency();

        if (mentorProfile != null && mentorProfile.getHourlyRate() != null) {
            try {
                // Assume mentor's hourly rate is stored in USD, convert to target currency
                java.math.BigDecimal convertedPrice = currencyService.convertToUserCurrency(
                    mentorProfile.getHourlyRate(),
                    targetCurrency
                );
                session.setPrice(convertedPrice);
                session.setCurrency(targetCurrency);

                log.info("Converted session price from USD {} to {} {}",
                        mentorProfile.getHourlyRate(), convertedPrice, targetCurrency);
            } catch (Exception e) {
                log.error("Failed to convert currency, using default: {}", e.getMessage());
                // Fallback: use default currency
                java.math.BigDecimal convertedPrice = currencyService.convertFromUSDToDefault(
                    mentorProfile.getHourlyRate()
                );
                session.setPrice(convertedPrice);
                session.setCurrency(currencyService.getDefaultCurrency());
            }
        } else {
            session.setPrice(null);
            session.setCurrency(targetCurrency);
        }

        session.setStatus(Session.SessionStatus.PENDING);
        session.setPaymentStatus(Session.PaymentStatus.PENDING);
        EmployeeSessionAllocation activeAllocation = employeeSessionAllocationService.findActiveAllocationForProfile(menteeId)
                .orElse(null);
        if (activeAllocation != null) {
            session.setCorporateAllocationId(activeAllocation.getId());
            session.setEntitlementSource(Session.EntitlementSource.CORPORATE_ALLOCATION);
        }
        
        session = sessionRepository.save(session);

        if (session.getCorporateAllocationId() != null) {
            try {
                employeeSessionAllocationService.consumeBooking(session.getCorporateAllocationId(), menteeId);
                session.setCorporateAllocationConsumedAt(LocalDateTime.now());
                session = sessionRepository.save(session);
                log.info("Consumed company-funded session allocation for session {}", session.getId());
            } catch (Exception e) {
                log.error("Failed to consume company-funded session allocation: {}", e.getMessage(), e);
                sessionRepository.delete(session);
                throw new IllegalStateException("Failed to consume company-funded session allocation", e);
            }
        } else if (eligibility.getSubscriptionSource() == SessionBookingEligibility.SubscriptionSource.PERSONAL_CREDIT) {
            try {
                personalSessionCreditService.consumeNextCredit(menteeId, session.getId());
                session.setEntitlementSource(Session.EntitlementSource.PERSONAL_CREDIT);
                session.setPaid(true);
                session.setPaymentStatus(Session.PaymentStatus.PAID);
                session = sessionRepository.save(session);
                log.info("Consumed personal session credit for mentee {} and session {}", menteeId, session.getId());
            } catch (Exception e) {
                log.error("Failed to consume personal session credit: {}", e.getMessage(), e);
                sessionRepository.delete(session);
                throw new IllegalStateException("Failed to consume personal session credit", e);
            }
        } else if (eligibility.getSubscriptionSource() == SessionBookingEligibility.SubscriptionSource.CORPORATE) {
            try {
                subscriptionService.consumeSession(menteeId);
                log.info("Consumed corporate subscription session for mentee {}", menteeId);
            } catch (Exception e) {
                log.error("Failed to consume corporate subscription session: {}", e.getMessage(), e);
                sessionRepository.delete(session);
                throw new IllegalStateException("Failed to consume corporate subscription session", e);
            }
        } else {
            // Consume a session from subscription immediately only for non-corporate-allocation funding.
            try {
                SubscriptionService.SessionConsumptionResult consumption = subscriptionService.consumeSessionForBooking(menteeId);
                session.setEntitlementSource(toSessionEntitlementSource(consumption));
                session.setConsumedSubscriptionId(consumption.subscriptionId());
                session.setConsumedSubscriptionAddonId(consumption.addonId());
                session = sessionRepository.save(session);
                log.info("Consumed session from subscription for mentee {} using {}", menteeId, consumption.source());
            } catch (Exception e) {
                log.error("Failed to consume session from subscription: {}", e.getMessage(), e);
                // Rollback session creation
                sessionRepository.delete(session);
                throw new IllegalStateException("Failed to consume session from subscription", e);
            }
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

        // Send WhatsApp notifications (non-blocking)
        sendBookingRequestWhatsAppNotifications(session, mentor, mentee);

        log.info("Successfully created session request: {}", session.getId());
        return session;
    }

    private void applyQuestionnaireResponses(Session session, Map<String, Object> questionnaireResponses) {
        if (questionnaireResponses == null || questionnaireResponses.isEmpty()) {
            return;
        }

        session.setBookingPrimaryGoal(textFromMap(questionnaireResponses, "primaryGoal"));
        session.setBookingAlreadyTried(textFromMap(questionnaireResponses, "alreadyTried"));
        session.setBookingSuccessLooksLike(textFromMap(questionnaireResponses, "successLooksLike"));

        Object contextDocument = questionnaireResponses.get("contextDocument");
        if (contextDocument != null) {
            session.setBookingContextDocument(serializeQuestionnaireValue(contextDocument));
        }
    }

    private String textFromMap(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof String stringValue ? normalizeNullableText(stringValue) : null;
    }

    private String serializeQuestionnaireValue(Object value) {
        if (value instanceof String stringValue) {
            return normalizeNullableText(stringValue);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize booking questionnaire value: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private Session.EntitlementSource toSessionEntitlementSource(SubscriptionService.SessionConsumptionResult consumption) {
        if (consumption == null || consumption.source() == null) {
            return null;
        }

        return switch (consumption.source()) {
            case INDIVIDUAL_SUBSCRIPTION -> Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION;
            case SUBSCRIPTION_ADDON -> Session.EntitlementSource.SUBSCRIPTION_ADDON;
            case PERSONAL_CREDIT -> Session.EntitlementSource.PERSONAL_CREDIT;
            case CORPORATE_ALLOCATION -> Session.EntitlementSource.CORPORATE_ALLOCATION;
            case CORPORATE_SUBSCRIPTION -> null;
        };
    }

    private CompanyProgramParticipant resolveParticipantContext(CreateSessionRequestDto request,
                                                                Profile mentor,
                                                                Profile mentee) {
        UUID participantId = parseOptionalUuid(request.getCompanyProgramParticipantId());
        UUID requestedProgramId = parseOptionalUuid(request.getCompanyProgramId());
        UUID journeyInstanceStepId = parseOptionalUuid(request.getJourneyInstanceStepId());

        if (participantId == null && requestedProgramId == null) {
            return null;
        }

        if (participantId == null) {
            throw new IllegalArgumentException("companyProgramParticipantId is required when company program booking context is provided");
        }

        CompanyProgramParticipant participant = companyProgramParticipantRepository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Company program participant not found"));

        if (requestedProgramId != null && !requestedProgramId.equals(participant.getCompanyProgram().getId())) {
            throw new IllegalArgumentException("Company program context does not match the selected participant");
        }

        if (!participant.getProfile().getId().equals(mentee.getId())) {
            throw new IllegalArgumentException("Selected company program participant does not belong to the requesting mentee");
        }

        if (participant.getCompanyProgram().getStatus() != CompanyProgram.CompanyProgramStatus.LIVE) {
            throw new IllegalArgumentException("Sessions can only be booked for LIVE company programs");
        }

        if (participant.getStatus() != CompanyProgramParticipant.ParticipantStatus.ENROLLED
                && participant.getStatus() != CompanyProgramParticipant.ParticipantStatus.ACTIVE) {
            throw new IllegalArgumentException("Sessions can only be booked for enrolled or active participants");
        }

        if (!participantConsentService.hasGrantedConsent(participantId, ConsentRecord.ConsentType.PROGRAM_PARTICIPATION)) {
            throw new IllegalArgumentException("Program participation consent must be granted before booking a company-program session");
        }

        CompanyProgramMentorAssignment assignment = resolveMentorAssignmentForBooking(
                participantId,
                mentor.getId(),
                journeyInstanceStepId
        );

        if (!assignment.getMentor().getId().equals(mentor.getId())) {
            throw new IllegalArgumentException("Sessions must be booked with the assigned company-program mentor");
        }

        return participant;
    }

    private CompanyProgramMentorAssignment resolveMentorAssignmentForBooking(UUID participantId,
                                                                             UUID mentorId,
                                                                             UUID journeyInstanceStepId) {
        if (journeyInstanceStepId != null) {
            return companyProgramMentorAssignmentRepository
                    .findByParticipant_IdAndJourneyInstanceStep_Id(participantId, journeyInstanceStepId)
                    .or(() -> companyProgramMentorAssignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId))
                    .orElseThrow(() -> new IllegalArgumentException("No mentor assignment exists for this company program journey step"));
        }

        return companyProgramMentorAssignmentRepository
                .findByParticipant_IdAndJourneyInstanceStepIsNull(participantId)
                .or(() -> companyProgramMentorAssignmentRepository.findFirstByParticipant_IdAndMentor_IdOrderByAssignedAtDesc(participantId, mentorId))
                .orElseThrow(() -> new IllegalArgumentException("No mentor assignment exists for this company program participant"));
    }

    private UUID parseOptionalUuid(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID value provided");
        }
    }

    private int resolveSessionDurationMinutes(SessionBookingEligibility eligibility) {
        Integer configuredDuration = eligibility != null ? eligibility.getSessionDurationMinutes() : null;
        if (configuredDuration != null && configuredDuration > 0 && configuredDuration <= 24 * 60) {
            return configuredDuration;
        }
        return SubscriptionService.DEFAULT_SESSION_DURATION_MINUTES;
    }
    
    /**
     * Confirm a session request (mentor action)
     */
    public Session confirmSession(UUID sessionId, String mentorResponse) {
        return confirmSession(sessionId, mentorResponse, null);
    }

    /**
     * Confirm a session request (mentor action) with an optional mentor-finalized start time.
     */
    public Session confirmSession(UUID sessionId, String mentorResponse, ZonedDateTime scheduledStart) {
        return confirmSession(sessionId, mentorResponse, scheduledStart, null);
    }

    private Session confirmSession(UUID sessionId,
                                   String mentorResponse,
                                   ZonedDateTime scheduledStart,
                                   ZonedDateTime scheduledEnd) {
        log.info("Confirming session: {}", sessionId);
        
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        if (session.getStatus() != Session.SessionStatus.PENDING) {
            throw new IllegalStateException("Can only confirm pending sessions");
        }

        Profile mentor = profileRepository.findById(session.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));

        Profile mentee = profileRepository.findById(session.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        ZonedDateTime confirmedStart = resolveConfirmedStart(session, scheduledStart);
        ZonedDateTime confirmedEnd = resolveConfirmedEnd(session, confirmedStart, scheduledEnd);
        validateConfirmedSchedule(confirmedStart, confirmedEnd);
        validateMentorAvailability(mentor, confirmedStart, confirmedEnd, session.getId());

        var originalStatus = session.getStatus();
        var originalConfirmedAt = session.getConfirmedAt();
        var originalScheduledStart = session.getScheduledStart();
        var originalScheduledEnd = session.getScheduledEnd();
        var originalMentorResponse = session.getMentorResponse();
        var originalMeetingUrl = session.getMeetingUrl();
        var originalMeetingId = session.getMeetingId();
        var originalMeetingPassword = session.getMeetingPassword();
        var originalCalendarEventId = session.getCalendarEventId();
        
        // Confirm session
        session.setScheduledStart(confirmedStart);
        session.setScheduledEnd(confirmedEnd);
        session.confirm();
        session.setMentorResponse(mentorResponse);
        
        try {
            if (session.getCorporateAllocationId() != null && session.getCorporateAllocationConsumedAt() == null) {
                employeeSessionAllocationService.consumeBooking(session.getCorporateAllocationId(), session.getMenteeId());
                session.setCorporateAllocationConsumedAt(LocalDateTime.now());
            }

            // Create meeting
            MeetingDetails meetingDetails = meetingService.createMeeting(session);
            session.setMeetingUrl(meetingDetails.getMeetingUrl());
            session.setMeetingId(meetingDetails.getMeetingId());
            session.setMeetingPassword(meetingDetails.getPassword());
            
            // Add to Google Calendar
            String calendarEventId = calendarService.createCalendarEvent(session, meetingDetails);
            session.setCalendarEventId(calendarEventId);
            
            session = sessionRepository.save(session);
            
            // Send confirmation to mentee
            notificationService.sendSessionConfirmationToMentee(session, mentee, mentor, meetingDetails);
            session.setMenteeNotificationSent(true);
            
            // Send confirmation to mentor
            notificationService.sendSessionConfirmationToMentor(session, mentor, mentee, meetingDetails);
            session.setMentorNotificationSent(true);

            // Send WhatsApp confirmations (non-blocking)
            sendBookingConfirmedWhatsAppNotifications(session, mentor, mentee);
            
            session = sessionRepository.save(session);
            
            log.info("Successfully confirmed session: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to complete session confirmation for {}: {}", sessionId, e.getMessage(), e);
            // Roll back local session state before persisting.
            session.setStatus(originalStatus);
            session.setConfirmedAt(originalConfirmedAt);
            session.setScheduledStart(originalScheduledStart);
            session.setScheduledEnd(originalScheduledEnd);
            session.setMentorResponse(originalMentorResponse);
            session.setMeetingUrl(originalMeetingUrl);
            session.setMeetingId(originalMeetingId);
            session.setMeetingPassword(originalMeetingPassword);
            session.setCalendarEventId(originalCalendarEventId);
            sessionRepository.save(session);
            throw new RuntimeException("Failed to confirm session", e);
        }

        activateCompanyProgramParticipantIfNeeded(session.getCompanyProgramParticipantId());
        
        return session;
    }

    public SessionProposal proposeAlternative(UUID sessionId,
                                              String mentorMessage,
                                              List<SessionProposalSlotRequest> requestedSlots) {
        log.info("Proposing alternative slots for session: {}", sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (session.getStatus() != Session.SessionStatus.PENDING) {
            throw new IllegalStateException("Can only propose alternatives for pending sessions");
        }

        List<SessionProposalSlotRequest> slots = requestedSlots == null ? List.of() : requestedSlots;
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("At least one alternative slot is required");
        }

        Profile mentor = profileRepository.findById(session.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));
        Profile mentee = profileRepository.findById(session.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        sessionProposalRepository.findFirstBySessionIdAndStatusOrderByProposedAtDesc(
                sessionId,
                SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE
        ).ifPresent(existing -> {
            existing.setStatus(SessionProposal.ProposalStatus.CANCELLED);
            existing.setRespondedAt(LocalDateTime.now());
            sessionProposalRepository.saveAndFlush(existing);
        });

        SessionProposal proposal = new SessionProposal();
        proposal.setSessionId(sessionId);
        proposal.setMentorMessage(normalizeNullableText(mentorMessage));
        proposal.setStatus(SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE);
        proposal.setProposalType(slots.size() > 1
                ? SessionProposal.ProposalType.MULTIPLE_SLOTS
                : SessionProposal.ProposalType.SINGLE_SLOT);

        int sortOrder = 0;
        for (SessionProposalSlotRequest requestedSlot : slots) {
            if (requestedSlot == null || requestedSlot.getScheduledStart() == null) {
                throw new IllegalArgumentException("Each alternative slot must include scheduledStart");
            }

            ZonedDateTime slotStart = requestedSlot.getScheduledStart();
            ZonedDateTime slotEnd = resolveProposalSlotEnd(session, slotStart, requestedSlot.getScheduledEnd());
            validateConfirmedSchedule(slotStart, slotEnd);
            validateMentorAvailability(mentor, slotStart, slotEnd, session.getId());

            SessionProposalSlot slot = new SessionProposalSlot();
            slot.setProposal(proposal);
            slot.setScheduledStart(slotStart);
            slot.setScheduledEnd(slotEnd);
            slot.setSortOrder(sortOrder++);
            proposal.getSlots().add(slot);
        }

        SessionProposal saved = sessionProposalRepository.save(proposal);
        sendAlternativeProposedEmailNotification(saved, session, mentor, mentee);
        sendAlternativeProposedWhatsAppNotification(saved, session, mentor, mentee);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<SessionProposal> getActiveProposal(UUID sessionId) {
        return sessionProposalRepository.findFirstBySessionIdAndStatusOrderByProposedAtDesc(
                sessionId,
                SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE
        );
    }

    public Session acceptProposal(UUID sessionId, UUID proposalId, UUID slotId, String response) {
        SessionProposal proposal = loadPendingProposal(sessionId, proposalId);
        SessionProposalSlot acceptedSlot = resolveAcceptedSlot(proposal, slotId);

        proposal.setStatus(SessionProposal.ProposalStatus.ACCEPTED);
        proposal.setAcceptedSlotId(acceptedSlot.getId());
        proposal.setMenteeResponse(normalizeNullableText(response));
        proposal.setRespondedAt(LocalDateTime.now());
        sessionProposalRepository.save(proposal);

        String confirmationMessage = defaultString(
                proposal.getMentorMessage(),
                "Your selected alternative time has been confirmed."
        );
        Session confirmed = confirmSession(
                sessionId,
                confirmationMessage,
                acceptedSlot.getScheduledStart(),
                acceptedSlot.getScheduledEnd()
        );

        Profile mentor = profileRepository.findById(confirmed.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));
        Profile mentee = profileRepository.findById(confirmed.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));
        sendProposalResponseEmailNotification(proposal, confirmed, mentor, mentee, "accepted");
        sendProposalResponseWhatsAppNotification(proposal, confirmed, mentor, mentee, "accepted");

        return confirmed;
    }

    public SessionProposal declineProposal(UUID sessionId, UUID proposalId, String response) {
        SessionProposal proposal = loadPendingProposal(sessionId, proposalId);
        proposal.setStatus(SessionProposal.ProposalStatus.DECLINED);
        proposal.setMenteeResponse(normalizeNullableText(response));
        proposal.setRespondedAt(LocalDateTime.now());
        SessionProposal saved = sessionProposalRepository.save(proposal);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        Profile mentor = profileRepository.findById(session.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));
        Profile mentee = profileRepository.findById(session.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));
        sendProposalResponseEmailNotification(saved, session, mentor, mentee, "declined");
        sendProposalResponseWhatsAppNotification(saved, session, mentor, mentee, "declined");

        return saved;
    }

    public SessionSupportRequest contactSupport(UUID sessionId,
                                                SessionSupportRequest.RequesterType requesterType,
                                                String message) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (requesterType == null) {
            throw new IllegalArgumentException("requesterType is required");
        }

        Profile mentor = profileRepository.findById(session.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));
        Profile mentee = profileRepository.findById(session.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        Profile requester = requesterType == SessionSupportRequest.RequesterType.MENTOR ? mentor : mentee;

        SessionSupportRequest request = new SessionSupportRequest();
        request.setSessionId(sessionId);
        request.setRequesterType(requesterType);
        request.setRequesterId(requester.getId());
        request.setMessage(normalizeNullableText(message));
        request.setStatus(SessionSupportRequest.SupportStatus.OPEN);

        SessionSupportRequest saved = sessionSupportRequestRepository.save(request);
        sendSupportContactWhatsAppNotifications(saved, session, mentor, mentee, requester);
        return saved;
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
            returnCorporateAllocationIfNeeded(session);
            returnIndividualEntitlementIfNeeded(session, cancelledBy);
            issuePersonalCreditIfNeeded(session, cancelledBy);

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

    private void issuePersonalCreditIfNeeded(Session session, Session.CancelledBy cancelledBy) {
        if (cancelledBy != Session.CancelledBy.MENTOR) {
            return;
        }
        if (session.getCorporateAllocationId() != null) {
            return;
        }
        if (!isPaidSession(session)) {
            return;
        }

        personalSessionCreditService.issueMentorDeclineCredit(session);
        log.info("Issued personal session credit for mentor-declined paid session {}", session.getId());
    }

    private void returnIndividualEntitlementIfNeeded(Session session, Session.CancelledBy cancelledBy) {
        if (cancelledBy != Session.CancelledBy.MENTOR) {
            return;
        }
        if (session.getCorporateAllocationId() != null) {
            return;
        }
        if (session.getEntitlementReturnedAt() != null) {
            return;
        }
        if (isPaidSession(session)) {
            return;
        }
        if (!isIndividualSubscriptionEntitlement(session.getEntitlementSource())) {
            return;
        }

        subscriptionService.returnConsumedSessionForDeclinedBooking(
                session.getMenteeId(),
                session.getConsumedSubscriptionId(),
                session.getConsumedSubscriptionAddonId()
        );
        session.setEntitlementReturnedAt(LocalDateTime.now());
        log.info("Returned individual entitlement for mentor-declined session {}", session.getId());
    }

    private boolean isIndividualSubscriptionEntitlement(Session.EntitlementSource source) {
        return source == null
                || source == Session.EntitlementSource.INDIVIDUAL_SUBSCRIPTION
                || source == Session.EntitlementSource.SUBSCRIPTION_ADDON;
    }

    private boolean isPaidSession(Session session) {
        return Boolean.TRUE.equals(session.getPaid()) || session.getPaymentStatus() == Session.PaymentStatus.PAID;
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
     * Get sessions for a mentee with filter (today, upcoming, past, or all)
     */
    @Transactional(readOnly = true)
    public Page<Session> getMenteeSessionsWithFilter(UUID menteeId, String filter, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();

        if (filter == null || filter.equalsIgnoreCase("all")) {
            return sessionRepository.findByMenteeIdOrderByScheduledStartDesc(menteeId, pageable);
        }

        switch (filter.toLowerCase()) {
            case "today":
                ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(now.getZone());
                ZonedDateTime endOfDay = startOfDay.plusDays(1);
                return sessionRepository.findTodaySessionsForMentee(menteeId, startOfDay, endOfDay, pageable);

            case "upcoming":
                return sessionRepository.findUpcomingSessionsForMentee(menteeId, now, pageable);

            case "past":
                return sessionRepository.findPastSessionsForMentee(menteeId, now, pageable);

            default:
                throw new IllegalArgumentException("Invalid filter. Valid options are: today, upcoming, past, all");
        }
    }
    
    /**
     * Get a session by ID
     */
    @Transactional(readOnly = true)
    public Session getSessionById(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
    }

    @Transactional(readOnly = true)
    public String getProfileDisplayName(UUID profileId) {
        return profileRepository.findById(profileId)
                .map(this::fullName)
                .orElse(null);
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
            case CANCELLED -> {
                session.cancel(Session.CancelledBy.SYSTEM, "Status updated by system");
                returnCorporateAllocationIfNeeded(session);
            }
        }
        
        return sessionRepository.save(session);
    }

    private void returnCorporateAllocationIfNeeded(Session session) {
        if (session.getCorporateAllocationId() == null
                || session.getCorporateAllocationConsumedAt() == null
                || session.getCorporateAllocationReturnedAt() != null) {
            return;
        }

        employeeSessionAllocationService.returnConsumedBooking(
                session.getCorporateAllocationId(),
                session.getId(),
                session.getMenteeId()
        );
        session.setCorporateAllocationReturnedAt(LocalDateTime.now());
    }

    /**
     * Mark a session as completed and notify the mentee to submit feedback.
     */
    public Session markSessionComplete(UUID sessionId) {
        return markSessionComplete(sessionId, null);
    }

    /**
     * Mark a session as completed, optionally capturing structured outcome details.
     */
    public Session markSessionComplete(UUID sessionId, CompleteSessionRequestDto outcomeRequest) {
        log.info("Marking session {} as completed", sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!canBeMarkedComplete(session.getStatus())) {
            throw new IllegalStateException("Only confirmed or in-progress sessions can be completed");
        }

        if (session.getScheduledEnd() == null) {
            throw new IllegalStateException("Session does not have a scheduled end time");
        }

        if (ZonedDateTime.now().isBefore(session.getScheduledEnd())) {
            throw new IllegalStateException("Session can only be completed after the scheduled end time");
        }

        Profile mentor = profileRepository.findById(session.getMentorId())
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found"));

        Profile mentee = profileRepository.findById(session.getMenteeId())
                .orElseThrow(() -> new IllegalArgumentException("Mentee not found"));

        if (hasOutcomePayload(outcomeRequest)) {
            upsertSessionOutcome(session, outcomeRequest);
        }

        transitionSessionToCompleted(session);
        session = sessionRepository.save(session);
        activateCompanyProgramParticipantIfNeeded(session.getCompanyProgramParticipantId());
        journeyInstanceService.advanceAfterSessionCompletion(session);

        reviewWorkflowService.openSessionReviewCycle(session, mentor, mentee);
        reviewWorkflowService.maybeOpenFitReviewCycle(session, mentor, mentee);

        log.info("Successfully marked session {} as completed", sessionId);
        return session;
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

    private void sendBookingRequestWhatsAppNotifications(Session session, Profile mentor, Profile mentee) {
        String sessionDate = formatSessionDate(session.getScheduledStart());
        String mentorPhone = formatPhoneNumberToE164(mentor.getPhone());
        String menteePhone = formatPhoneNumberToE164(mentee.getPhone());

        if (mentorPhone != null && !mentorPhone.isBlank()) {
            try {
                List<String> mentorBodyParams = List.of(
                        firstName(mentor),
                        fullName(mentee),
                        defaultString(mentee.getLinkedinUrl(), "Not provided"),
                        defaultString(session.getTitle(), "Mentorship Session"),
                        sessionDate,
                        defaultString(session.getMenteeMessage(), "No notes provided"),
                        buildMentorReviewButtonValue(session.getId())
                );

                nautixWhatsAppService.sendTemplateMessage(
                        "prosper_mentor_session_request",
                        mentorPhone,
                        mentorBodyParams
                );
            } catch (Exception e) {
                log.error("Failed to send mentor booking request WhatsApp for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("Mentor {} has no valid phone for booking request WhatsApp", mentor.getId());
        }

        if (menteePhone != null && !menteePhone.isBlank()) {
            try {
                List<String> menteeBodyParams = List.of(
                        firstName(mentee),
                        fullName(mentor),
                        defaultString(session.getTitle(), "Mentorship Session"),
                        sessionDate
                );

                nautixWhatsAppService.sendTemplateMessage(
                        "prosper_mentee_session_request_notification",
                        menteePhone,
                        menteeBodyParams
                );
            } catch (Exception e) {
                log.error("Failed to send mentee booking request WhatsApp for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("Mentee {} has no valid phone for booking request WhatsApp", mentee.getId());
        }
    }

    private void sendBookingConfirmedWhatsAppNotifications(Session session, Profile mentor, Profile mentee) {
        String sessionDate = formatSessionDate(session.getScheduledStart());
        String sessionTime = formatSessionTime(session.getScheduledStart());
        String mentorPhone = formatPhoneNumberToE164(mentor.getPhone());
        String menteePhone = formatPhoneNumberToE164(mentee.getPhone());

        if (mentorPhone != null && !mentorPhone.isBlank()) {
            try {
                List<String> mentorBodyParams = List.of(
                        firstName(mentor),
                        fullName(mentee),
                        defaultString(session.getTitle(), "Mentorship Session"),
                        sessionDate,
                        sessionTime,
                        defaultString(session.getMenteeMessage(), "No message provided"),
                        buildSessionButtonValue(session.getId())
                );

                nautixWhatsAppService.sendTemplateMessage(
                        "prosper_mentor_session_confirmed",
                        mentorPhone,
                        mentorBodyParams
                );
            } catch (Exception e) {
                log.error("Failed to send mentor confirmation WhatsApp for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("Mentor {} has no valid phone for confirmation WhatsApp", mentor.getId());
        }

        if (menteePhone != null && !menteePhone.isBlank()) {
            try {
                List<String> menteeBodyParams = List.of(
                        firstName(mentee),
                        fullName(mentor),
                        defaultString(session.getTitle(), "Mentorship Session"),
                        sessionDate,
                        sessionTime,
                        defaultString(session.getMentorResponse(), "Your session has been confirmed.")
                );

                nautixWhatsAppService.sendTemplateMessage(
                        "prosper_mentee_session_confirmed",
                        menteePhone,
                        menteeBodyParams
                );
            } catch (Exception e) {
                log.error("Failed to send mentee confirmation WhatsApp for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("Mentee {} has no valid phone for confirmation WhatsApp", mentee.getId());
        }
    }

    private void sendAlternativeProposedWhatsAppNotification(SessionProposal proposal,
                                                             Session session,
                                                             Profile mentor,
                                                             Profile mentee) {
        String menteePhone = formatPhoneNumberToE164(mentee.getPhone());
        if (menteePhone == null || menteePhone.isBlank()) {
            log.warn("Mentee {} has no valid phone for alternative proposal WhatsApp", mentee.getId());
            return;
        }

        try {
            List<String> bodyParams = List.of(
                    firstName(mentee),
                    fullName(mentor),
                    defaultString(session.getTitle(), "Mentorship Session"),
                    formatProposalSlots(proposal),
                    defaultString(proposal.getMentorMessage(), "Please review the proposed alternative time."),
                    buildSessionButtonValue(session.getId())
            );

            nautixWhatsAppService.sendTemplateMessage(
                    MENTEE_ALTERNATIVE_PROPOSED_TEMPLATE,
                    menteePhone,
                    bodyParams
            );
        } catch (Exception e) {
            log.error("Failed to send alternative proposal WhatsApp for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private void sendAlternativeProposedEmailNotification(SessionProposal proposal,
                                                          Session session,
                                                          Profile mentor,
                                                          Profile mentee) {
        try {
            notificationService.sendAlternativeProposalToMentee(proposal, session, mentor, mentee);
        } catch (Exception e) {
            log.error("Failed to send alternative proposal email for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private void sendProposalResponseEmailNotification(SessionProposal proposal,
                                                       Session session,
                                                       Profile mentor,
                                                       Profile mentee,
                                                       String responseStatus) {
        try {
            notificationService.sendProposalResponseToMentor(proposal, session, mentor, mentee, responseStatus);
        } catch (Exception e) {
            log.error("Failed to send proposal response email for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private void sendProposalResponseWhatsAppNotification(SessionProposal proposal,
                                                          Session session,
                                                          Profile mentor,
                                                          Profile mentee,
                                                          String responseStatus) {
        String mentorPhone = formatPhoneNumberToE164(mentor.getPhone());
        if (mentorPhone == null || mentorPhone.isBlank()) {
            log.warn("Mentor {} has no valid phone for proposal response WhatsApp", mentor.getId());
            return;
        }

        try {
            boolean accepted = "accepted".equalsIgnoreCase(responseStatus);
            String templateName = accepted
                    ? MENTOR_ALTERNATIVE_ACCEPTED_TEMPLATE
                    : MENTOR_ALTERNATIVE_DECLINED_TEMPLATE;
            String responseText = accepted
                    ? formatAcceptedProposalSlot(proposal)
                    : defaultString(proposal.getMenteeResponse(), "The proposed time was declined.");

            List<String> bodyParams = List.of(
                    firstName(mentor),
                    fullName(mentee),
                    defaultString(session.getTitle(), "Mentorship Session"),
                    responseText,
                    buildSessionButtonValue(session.getId())
            );

            nautixWhatsAppService.sendTemplateMessage(
                    templateName,
                    mentorPhone,
                    bodyParams
            );
        } catch (Exception e) {
            log.error("Failed to send proposal response WhatsApp for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private void sendSupportContactWhatsAppNotifications(SessionSupportRequest supportRequest,
                                                         Session session,
                                                         Profile mentor,
                                                         Profile mentee,
                                                         Profile requester) {
        String requesterPhone = formatPhoneNumberToE164(requester.getPhone());
        if (requesterPhone == null || requesterPhone.isBlank()) {
            log.warn("Requester {} has no valid phone for support acknowledgement WhatsApp", requester.getId());
            return;
        }

        try {
            Profile counterpart = supportRequest.getRequesterType() == SessionSupportRequest.RequesterType.MENTOR
                    ? mentee
                    : mentor;
            String templateName = supportRequest.getRequesterType() == SessionSupportRequest.RequesterType.MENTOR
                    ? MENTOR_SUPPORT_CONTACT_REQUESTED_TEMPLATE
                    : MENTEE_SUPPORT_CONTACT_REQUESTED_TEMPLATE;
            List<String> acknowledgementParams = List.of(
                    firstName(requester),
                    defaultString(session.getTitle(), "Mentorship Session"),
                    fullName(counterpart),
                    buildSessionButtonValue(session.getId())
            );

            nautixWhatsAppService.sendTemplateMessage(
                    templateName,
                    requesterPhone,
                    acknowledgementParams
            );
        } catch (Exception e) {
            log.error("Failed to send support acknowledgement WhatsApp for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    private String formatProposalSlots(SessionProposal proposal) {
        if (proposal.getSlots() == null || proposal.getSlots().isEmpty()) {
            return "No slots provided";
        }

        return proposal.getSlots().stream()
                .map(slot -> formatSessionDate(slot.getScheduledStart()) + " at " + formatSessionTime(slot.getScheduledStart()))
                .toList()
                .toString()
                .replace("[", "")
                .replace("]", "");
    }

    private String formatAcceptedProposalSlot(SessionProposal proposal) {
        if (proposal.getSlots() == null || proposal.getSlots().isEmpty()) {
            return "Accepted proposed time";
        }

        return proposal.getSlots().stream()
                .filter(slot -> proposal.getAcceptedSlotId() != null
                        && proposal.getAcceptedSlotId().equals(slot.getId()))
                .findFirst()
                .map(slot -> formatSessionDate(slot.getScheduledStart()) + " at " + formatSessionTime(slot.getScheduledStart()))
                .orElseGet(() -> formatProposalSlots(proposal));
    }

    private String formatSessionDate(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));
    }

    private String formatSessionTime(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH));
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

    private String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String buildMentorReviewLink(UUID sessionId) {
        return frontendUrl + "/app/sessions/review/" + sessionId;
    }

    private String buildSessionDetailsLink(UUID sessionId) {
        return frontendUrl + "/app/sessions/" + sessionId;
    }

    private String buildMentorReviewButtonValue(UUID sessionId) {
        return "review/" + sessionId;
    }

    private String buildSessionButtonValue(UUID sessionId) {
        return sessionId.toString();
    }


    private ZonedDateTime resolveConfirmedStart(Session session, ZonedDateTime requestedStart) {
        return requestedStart != null ? requestedStart : session.getScheduledStart();
    }

    private ZonedDateTime resolveConfirmedEnd(Session session, ZonedDateTime confirmedStart, ZonedDateTime requestedEnd) {
        if (requestedEnd != null) {
            return requestedEnd;
        }

        long durationMinutes = session.getScheduledStart() != null && session.getScheduledEnd() != null
                ? Duration.between(session.getScheduledStart(), session.getScheduledEnd()).toMinutes()
                : SubscriptionService.DEFAULT_SESSION_DURATION_MINUTES;

        if (durationMinutes <= 0 || durationMinutes > 24 * 60) {
            durationMinutes = SubscriptionService.DEFAULT_SESSION_DURATION_MINUTES;
        }

        return confirmedStart.plusMinutes(durationMinutes);
    }

    private ZonedDateTime resolveProposalSlotEnd(Session session, ZonedDateTime slotStart, ZonedDateTime requestedEnd) {
        return resolveConfirmedEnd(session, slotStart, requestedEnd);
    }

    private SessionProposal loadPendingProposal(UUID sessionId, UUID proposalId) {
        SessionProposal proposal = sessionProposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Session proposal not found"));

        if (!sessionId.equals(proposal.getSessionId())) {
            throw new IllegalArgumentException("Session proposal does not belong to the session");
        }

        if (proposal.getStatus() != SessionProposal.ProposalStatus.PENDING_MENTEE_RESPONSE) {
            throw new IllegalStateException("Session proposal is no longer pending");
        }

        return proposal;
    }

    private SessionProposalSlot resolveAcceptedSlot(SessionProposal proposal, UUID slotId) {
        List<SessionProposalSlot> slots = proposal.getSlots() == null ? List.of() : proposal.getSlots();
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Session proposal has no slots");
        }

        if (slotId == null && slots.size() == 1) {
            return slots.get(0);
        }

        return slots.stream()
                .filter(slot -> slot.getId() != null && slot.getId().equals(slotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected proposal slot was not found"));
    }

    private void validateConfirmedSchedule(ZonedDateTime startTime, ZonedDateTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("scheduledStart is required");
        }

        if (!startTime.isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("scheduledStart must be in the future");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("scheduledEnd must be after scheduledStart");
        }
    }

    private void validateMentorAvailability(Profile mentor, ZonedDateTime startTime, ZonedDateTime endTime, UUID excludedSessionId) {
        // Get mentor profile to check availability (since isAvailable is in MentorProfile, not base Profile)
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentor.getId()).orElse(null);
        
        // Check if mentor is available
        if (mentorProfile != null && !mentorProfile.getIsAvailable()) {
            throw new IllegalArgumentException("Mentor is not currently available for sessions");
        }
        
        // Check for conflicting sessions
        List<Session> conflictingSessions = sessionRepository.findBlockingSessions(
                mentor.getId(), startTime, endTime, excludedSessionId);
        
        if (!conflictingSessions.isEmpty()) {
            throw new IllegalArgumentException("Mentor is not available during the requested time slot");
        }
    }

    private boolean canBeMarkedComplete(Session.SessionStatus status) {
        return status == Session.SessionStatus.CONFIRMED
                || status == Session.SessionStatus.SCHEDULED
                || status == Session.SessionStatus.IN_PROGRESS;
    }

    private void transitionSessionToCompleted(Session session) {
        if (session.getStatus() == Session.SessionStatus.CONFIRMED) {
            session.start();
        } else if (session.getStatus() == Session.SessionStatus.SCHEDULED) {
            session.setStatus(Session.SessionStatus.IN_PROGRESS);
        }

        session.complete();
    }

    private boolean hasOutcomePayload(CompleteSessionRequestDto request) {
        if (request == null) {
            return false;
        }

        if (hasText(request.getOutcomeSummary())
                || hasText(request.getReflectionPrompt())
                || hasText(request.getMentorPrivateNotes())) {
            return true;
        }

        return request.getActionItems() != null
                && request.getActionItems().stream()
                .anyMatch(item -> item != null && hasText(item.getDescription()));
    }

    private void upsertSessionOutcome(Session session, CompleteSessionRequestDto request) {
        SessionOutcome outcome = sessionOutcomeRepository.findDetailedBySessionId(session.getId())
                .orElseGet(() -> {
                    SessionOutcome created = new SessionOutcome();
                    created.setSession(session);
                    created.setActionItems(new ArrayList<>());
                    return created;
                });

        outcome.setSummary(normalizeNullableText(request.getOutcomeSummary()));
        outcome.setReflectionPrompt(normalizeNullableText(request.getReflectionPrompt()));
        outcome.setMentorPrivateNotes(normalizeNullableText(request.getMentorPrivateNotes()));
        outcome.setRecordedByUserId(session.getMentorId());
        outcome.setRecordedAt(LocalDateTime.now());

        List<SessionOutcomeActionItem> actionItems = outcome.getActionItems();
        actionItems.clear();

        int sortOrder = 0;
        List<OutcomeActionItemRequestDto> requestedItems = request.getActionItems() != null
                ? request.getActionItems()
                : List.of();

        for (OutcomeActionItemRequestDto requestedItem : requestedItems) {
            if (requestedItem == null || !hasText(requestedItem.getDescription())) {
                continue;
            }

            SessionOutcomeActionItem actionItem = new SessionOutcomeActionItem();
            actionItem.setSessionOutcome(outcome);
            actionItem.setDescription(requestedItem.getDescription().trim());
            actionItem.setOwnerType(requestedItem.getOwnerType() != null
                    ? requestedItem.getOwnerType()
                    : SessionOutcomeActionItem.ActionItemOwnerType.MENTEE);
            actionItem.setDueAt(requestedItem.getDueAt());
            actionItem.setSortOrder(sortOrder++);
            actionItems.add(actionItem);
        }

        sessionOutcomeRepository.save(outcome);
    }

    private void activateCompanyProgramParticipantIfNeeded(UUID participantId) {
        if (participantId == null) {
            return;
        }

        companyProgramParticipantRepository.findById(participantId).ifPresent(participant -> {
            if (participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ENROLLED) {
                participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
                companyProgramParticipantRepository.save(participant);
            }
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String normalizeNullableText(String value) {
        return hasText(value) ? value.trim() : null;
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

    /**
     * Format phone number to E.164 format required by WhatsApp
     * E.164 format: +[country code][number] (e.g., +254712345678)
     */
    private String formatPhoneNumberToE164(String phoneNumber) {
        return PhoneNumberUtil.normalizeToE164(phoneNumber);
    }
}
