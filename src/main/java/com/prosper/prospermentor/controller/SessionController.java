package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.ApiResponse;
import com.prosper.prospermentor.controller.dto.SessionDtos.*;
import com.prosper.prospermentor.dto.CreateSessionRequestDto;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionOutcome;
import com.prosper.prospermentor.entity.SessionOutcomeActionItem;
import com.prosper.prospermentor.exception.SessionBookingException;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import com.prosper.prospermentor.service.SessionBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for session booking and management
 * Handles the complete session lifecycle from booking to completion
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Session booking and management API")
@Slf4j
public class SessionController {

    private final SessionBookingService sessionBookingService;
    private final NautixWhatsAppService nautixWhatsAppService;
    private final SessionOutcomeRepository sessionOutcomeRepository;

    public SessionController(SessionBookingService sessionBookingService,
                             NautixWhatsAppService nautixWhatsAppService,
                             SessionOutcomeRepository sessionOutcomeRepository) {
        this.sessionBookingService = sessionBookingService;
        this.nautixWhatsAppService = nautixWhatsAppService;
        this.sessionOutcomeRepository = sessionOutcomeRepository;
    }

    /**
     *
     * Book a new mentorship session
     */
    @PostMapping("/book")
    @Operation(summary = "Book a new mentorship session", 
               description = "Create a new session booking request. The session will be in PENDING status until confirmed by the mentor.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Session booking created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid booking request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mentor, mentee, or skill not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mentor not available at requested time")
    })
    public ResponseEntity<ApiResponse<SessionResponseDto>> bookSession(
            @RequestBody CreateSessionRequestDto request) {
        
        log.info("Booking session request: mentee {} -> mentor {} for skill {}", 
                request.getMenteeId(), request.getMentorId(), request.getSkillId());
        
        try {
            Session session = sessionBookingService.createSessionRequest(request);
            SessionResponseDto responseData = convertToResponseDto(session);
            
            log.info("Session booked successfully: {}", session.getId());
            
            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData, 
                "Session booking created successfully"
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid booking request: {}", e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (SessionBookingException e) {
            log.error("Session booking failed: {} - Reason: {}", e.getMessage(), e.getEligibility().getReason());

            // Create error details with full eligibility information including recommended plans
            SessionBookingErrorDto errorData = SessionBookingErrorDto.fromEligibility(e.getEligibility());

            ApiResponse<SessionBookingErrorDto> errorResponse = ApiResponse.<SessionBookingErrorDto>builder()
                    .status("error")
                    .message(e.getMessage())
                    .data(errorData)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body((ApiResponse<SessionResponseDto>) (ApiResponse<?>) errorResponse);
        } catch (Exception e) {
            log.error("Error booking session: {}", e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to create session booking");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Confirm a session booking (mentor action)
     */
    @PostMapping("/{sessionId}/confirm")
    @Operation(summary = "Confirm a session booking", 
               description = "Mentor confirms a pending session booking. Creates meeting link and sends notifications.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session confirmed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Session cannot be confirmed (wrong status)")
    })
    public ResponseEntity<ApiResponse<SessionResponseDto>> confirmSession(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId,
            @Valid @RequestBody ConfirmSessionRequestDto request) {
        
        log.info("Confirming session: {}", sessionId);
        
        try {
            Session session = sessionBookingService.confirmSession(
                    sessionId,
                    request.getMentorResponse(),
                    request.getScheduledStart()
            );
            SessionResponseDto responseData = convertToResponseDto(session);
            
            log.info("Session confirmed successfully: {}", sessionId);
            
            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData, 
                "Session confirmed successfully"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Cannot confirm session {}: {}", sessionId, e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error confirming session {}: {}", sessionId, e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to confirm session");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Decline a session request (mentor action)
     */
    @PostMapping("/{sessionId}/decline")
    @Operation(summary = "Decline a session request",
               description = "Mentor declines a pending session request.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session declined successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Session cannot be declined")
    })
    public ResponseEntity<ApiResponse<SessionResponseDto>> declineSession(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId,
            @Valid @RequestBody DeclineSessionRequestDto request) {

        log.info("Declining session: {}", sessionId);

        try {
            Session session = sessionBookingService.cancelSession(
                sessionId,
                Session.CancelledBy.MENTOR,
                request.getReason() != null ? request.getReason() : "Mentor declined the session request"
            );
            SessionResponseDto responseData = convertToResponseDto(session);

            log.info("Session declined successfully: {}", sessionId);

            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData,
                "Session declined successfully"
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Cannot decline session {}: {}", sessionId, e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error declining session {}: {}", sessionId, e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to decline session");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Cancel a session
     */
    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "Cancel a session",
               description = "Cancel a session booking. Can be done by mentor, mentee, or admin.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session cancelled successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Session cannot be cancelled")
    })
    public ResponseEntity<ApiResponse<SessionResponseDto>> cancelSession(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId,
            @Valid @RequestBody CancelSessionRequestDto request) {

        log.info("Cancelling session: {} by {}", sessionId, request.getCancelledBy());

        try {
            Session session = sessionBookingService.cancelSession(sessionId, request.getCancelledBy(), request.getReason());
            SessionResponseDto responseData = convertToResponseDto(session);

            log.info("Session cancelled successfully: {}", sessionId);

            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData,
                "Session cancelled successfully"
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Cannot cancel session {}: {}", sessionId, e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error cancelling session {}: {}", sessionId, e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to cancel session");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Mark a session as completed and trigger mentee feedback collection.
     */
    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Mark a session as completed",
               description = "Marks a completed mentorship session as done and sends the mentee a feedback form prompt.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session marked as completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Session cannot be completed")
    })
    public ResponseEntity<ApiResponse<SessionResponseDto>> completeSession(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId,
            @RequestBody(required = false) CompleteSessionRequestDto request) {

        log.info("Marking session as completed: {}", sessionId);

        try {
            Session session = sessionBookingService.markSessionComplete(sessionId, request);
            SessionResponseDto responseData = convertToResponseDto(session);

            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData,
                "Session marked as completed successfully"
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Cannot complete session {}: {}", sessionId, e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error completing session {}: {}", sessionId, e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to mark session as completed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get session details
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session details", 
               description = "Retrieve detailed information about a specific session.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session details retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<SessionResponseDto> getSession(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId) {
        
        log.info("Getting session details: {}", sessionId);
        
        try {
            Session session = sessionBookingService.getSessionById(sessionId);
            SessionResponseDto response = convertToResponseDto(session);
            response.setMentorName(sessionBookingService.getProfileDisplayName(session.getMentorId()));
            response.setMenteeName(sessionBookingService.getProfileDisplayName(session.getMenteeId()));
            response.setSkillName(session.getSkill() != null ? session.getSkill().getName() : null);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get mentor's sessions
     */
    @GetMapping("/mentor/{mentorId}")
    @Operation(summary = "Get mentor's sessions", 
               description = "Retrieve all sessions for a specific mentor with pagination.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mentor not found")
    })
    @PreAuthorize("hasRole('MENTOR') and #mentorId == authentication.principal.id")
    public ResponseEntity<Page<SessionResponseDto>> getMentorSessions(
            @Parameter(description = "Mentor ID") @PathVariable UUID mentorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting sessions for mentor: {} (page: {}, size: {})", mentorId, page, size);
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Session> sessions = sessionBookingService.getMentorSessions(mentorId, pageable);
            Page<SessionResponseDto> response = sessions.map(this::convertToResponseDto);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting mentor sessions for {}: {}", mentorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get mentee's sessions
     */
    @GetMapping("/mentee/{menteeId}")
    @Operation(summary = "Get mentee's sessions", 
               description = "Retrieve all sessions for a specific mentee with pagination.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mentee not found")
    })
    @PreAuthorize("hasRole('MENTEE') and #menteeId == authentication.principal.id")
    public ResponseEntity<Page<SessionResponseDto>> getMenteeSessions(
            @Parameter(description = "Mentee ID") @PathVariable UUID menteeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting sessions for mentee: {} (page: {}, size: {})", menteeId, page, size);
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Session> sessions = sessionBookingService.getMenteeSessions(menteeId, pageable);
            Page<SessionResponseDto> response = sessions.map(this::convertToResponseDto);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting mentee sessions for {}: {}", menteeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get sessions by skill/topic
     */
    @GetMapping("/skill/{skillId}")
    @Operation(summary = "Get sessions by skill", 
               description = "Retrieve all sessions for a specific skill/topic.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Skill not found")
    })
    public ResponseEntity<Page<SessionResponseDto>> getSessionsBySkill(
            @Parameter(description = "Skill ID") @PathVariable UUID skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting sessions for skill: {} (page: {}, size: {})", skillId, page, size);
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Session> sessions = sessionBookingService.getSessionsBySkill(skillId, pageable);
            Page<SessionResponseDto> response = sessions.map(this::convertToResponseDto);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting sessions by skill {}: {}", skillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update session status (for system/admin use)
     */
    @PutMapping("/{sessionId}/status")
    @Operation(summary = "Update session status", 
               description = "Update session status (admin/system operation).")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session status updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponseDto>> updateSessionStatus(
            @Parameter(description = "Session ID") @PathVariable UUID sessionId,
            @RequestParam Session.SessionStatus status) {
        
        log.info("Updating session {} status to: {}", sessionId, status);
        
        try {
            Session session = sessionBookingService.updateSessionStatus(sessionId, status);
            SessionResponseDto responseData = convertToResponseDto(session);
            
            ApiResponse<SessionResponseDto> response = ApiResponse.success(
                responseData, 
                "Session status updated successfully"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Cannot update session {} status: {}", sessionId, e.getMessage());
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error updating session {} status: {}", sessionId, e.getMessage(), e);
            ApiResponse<SessionResponseDto> errorResponse = ApiResponse.error("Failed to update session status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Test WhatsApp notification endpoint
     */
    @PostMapping("/test/whatsapp")
    @Operation(summary = "Test WhatsApp notification",
               description = "Test endpoint to send WhatsApp notifications via nautix-service. For testing purposes only.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "WhatsApp message sent successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Failed to send WhatsApp message")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> testWhatsApp(@Valid @RequestBody TestWhatsAppRequestDto request) {
        log.info("Testing WhatsApp notification to: {}", request.getPhoneNumber());

        try {
            List<String> bodyParameters = resolveBodyParameters(request);

            // Send WhatsApp message
            nautixWhatsAppService.sendTemplateMessage(
                request.getTemplateName() != null ? request.getTemplateName() : "prosper_mentor_session_request",
                request.getPhoneNumber(),
                bodyParameters
            );

            Map<String, String> responseData = new HashMap<>();
            responseData.put("status", "sent");
            responseData.put("phoneNumber", request.getPhoneNumber());
            responseData.put("templateName", request.getTemplateName() != null ? request.getTemplateName() : "prosper_mentor_session_request");

            log.info("WhatsApp test message sent successfully to: {}", request.getPhoneNumber());

            ApiResponse<Map<String, String>> response = ApiResponse.success(
                responseData,
                "WhatsApp message sent successfully"
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid WhatsApp test request for {}: {}", request.getPhoneNumber(), e.getMessage());
            ApiResponse<Map<String, String>> errorResponse = ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp test message to {}: {}", request.getPhoneNumber(), e.getMessage(), e);
            ApiResponse<Map<String, String>> errorResponse = ApiResponse.error("Failed to send WhatsApp message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private List<String> resolveBodyParameters(TestWhatsAppRequestDto request) {
        if (request.getBodyParameters() != null && !request.getBodyParameters().isEmpty()) {
            return request.getBodyParameters();
        }
        if (request.getTemplateParams() != null && !request.getTemplateParams().isEmpty()) {
            return request.getTemplateParams().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
        }
        return List.of("Test User");
    }

    /**
     * Convert Session entity to response DTO
     */
    private SessionResponseDto convertToResponseDto(Session session) {
        // Determine if payment is required for this session
        // Payment is required if the user cannot book sessions (subscription limit reached)
        boolean paymentRequired = !sessionBookingService.canUserBookSession(session.getMenteeId());
        SessionOutcomeDto outcome = sessionOutcomeRepository.findDetailedBySessionId(session.getId())
                .map(this::toOutcomeDto)
                .orElse(null);

        return SessionResponseDto.builder()
                .id(session.getId())
                .mentorId(session.getMentorId())
                .menteeId(session.getMenteeId())
                .skillId(session.getSkillId())
                .companyProgramId(session.getCompanyProgramId())
                .companyProgramParticipantId(session.getCompanyProgramParticipantId())
                .title(session.getTitle())
                .description(session.getDescription())
                .scheduledStart(session.getScheduledStart())
                .scheduledEnd(session.getScheduledEnd())
                .status(session.getStatus())
                .meetingPlatform(session.getMeetingPlatform())
                .meetingUrl(session.getMeetingUrl())
                .meetingId(session.getMeetingId())
                .price(session.getPrice())
                .currency(session.getCurrency())
                .paymentStatus(session.getPaymentStatus())
                .paymentRequired(paymentRequired)
                .menteeMessage(session.getMenteeMessage())
                .mentorResponse(session.getMentorResponse())
                .calendarEventId(session.getCalendarEventId())
                .confirmedAt(session.getConfirmedAt())
                .cancelledAt(session.getCancelledAt())
                .cancellationReason(session.getCancellationReason())
                .cancelledBy(session.getCancelledBy())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .companyProgramName(session.getCompanyProgram() != null ? session.getCompanyProgram().getName() : null)
                .outcome(outcome)
                .durationMinutes(session.getScheduledStart() != null && session.getScheduledEnd() != null ? session.getDurationMinutes() : 0)
                .canBeModified(session.canBeModified())
                .isFutureBooking(session.getScheduledStart() != null && session.isFutureSession())
                .build();
    }

    private SessionOutcomeDto toOutcomeDto(SessionOutcome outcome) {
        List<SessionActionItemDto> actionItems = outcome.getActionItems() == null
                ? List.of()
                : outcome.getActionItems().stream()
                .sorted(Comparator.comparing(SessionOutcomeActionItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toActionItemDto)
                .toList();

        int openActionItemCount = (int) actionItems.stream()
                .filter(actionItem -> !actionItem.isCompleted())
                .count();

        return SessionOutcomeDto.builder()
                .id(outcome.getId())
                .summary(outcome.getSummary())
                .reflectionPrompt(outcome.getReflectionPrompt())
                .recordedAt(outcome.getRecordedAt())
                .openActionItemCount(openActionItemCount)
                .actionItems(actionItems)
                .build();
    }

    private SessionActionItemDto toActionItemDto(SessionOutcomeActionItem actionItem) {
        return SessionActionItemDto.builder()
                .id(actionItem.getId())
                .description(actionItem.getDescription())
                .ownerType(actionItem.getOwnerType())
                .dueAt(actionItem.getDueAt())
                .completedAt(actionItem.getCompletedAt())
                .completed(actionItem.isCompleted())
                .build();
    }
}
