package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyProgramParticipantDto;
import com.prosper.prospermentor.dto.CompanyProgramParticipantEnrollmentResultDto;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramJourneyDto;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramDto;
import com.prosper.prospermentor.dto.EnrollCompanyProgramParticipantsRequest;
import com.prosper.prospermentor.dto.ParticipantConsentWorkspaceDto;
import com.prosper.prospermentor.dto.UpdateParticipantConsentRequest;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.AccessAuditService;
import com.prosper.prospermentor.service.CompanyProgramParticipantService;
import com.prosper.prospermentor.service.CompanyProgramJourneyService;
import com.prosper.prospermentor.service.CompanyProgramService;
import com.prosper.prospermentor.service.ParticipantConsentService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Company Program Employees", description = "Enrollment and employee management for company mentorship programs")
@RequiredArgsConstructor
@Slf4j
public class CompanyProgramParticipantController {

    private final CompanyProgramParticipantService companyProgramParticipantService;
    private final CompanyProgramJourneyService companyProgramJourneyService;
    private final CompanyProgramService companyProgramService;
    private final ParticipantConsentService participantConsentService;
    private final AccessAuditService accessAuditService;
    private final ProfileService profileService;

    @GetMapping("/company-programs/{companyProgramId}/participants")
    @Operation(summary = "Get company program employees", description = "List enrolled employees for a company program")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getParticipants(@PathVariable UUID companyProgramId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size,
                                                                            @RequestParam(required = false) String search,
                                                                            @RequestParam(required = false) CompanyProgramParticipant.ParticipantStatus status,
                                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                                            Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);
            validatePageRequest(page, size);
            validateDateRange(startDate, endDate);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt"));
            Page<CompanyProgramParticipantDto> participants = companyProgramParticipantService.getParticipants(
                    companyProgramId,
                    search,
                    status,
                    startDate,
                    endDate,
                    pageable
            );

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.COMPANY_PROGRAM_PARTICIPANT_ROSTER,
                    AccessAuditLog.ActionType.VIEW,
                    "PARTICIPANT_ROSTER_VIEW",
                    companyProgramId,
                    companyProgram.getCompany(),
                    companyProgram,
                    null
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyProgramId", companyProgramId);
            data.put("participants", participants.getContent());
            data.put("count", participants.getNumberOfElements());
            data.put("currentPage", participants.getNumber());
            data.put("pageSize", participants.getSize());
            data.put("totalPages", participants.getTotalPages());
            data.put("totalItems", participants.getTotalElements());
            data.put("hasNext", participants.hasNext());
            data.put("hasPrevious", participants.hasPrevious());
            data.put("search", search != null ? search.trim() : "");
            data.put("status", status != null ? status.name() : null);

            return ResponseEntity.ok(ApiResponse.success("Company program employees retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting participants for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program employees: " + e.getMessage()));
        }
    }

    @PostMapping("/company-programs/{companyProgramId}/participants")
    @Operation(summary = "Enroll company program employees", description = "Enroll one or more company employees into a company program")
    public ResponseEntity<ApiResponse<CompanyProgramParticipantEnrollmentResultDto>> enrollParticipants(@PathVariable UUID companyProgramId,
                                                                                                        @Valid @RequestBody EnrollCompanyProgramParticipantsRequest request,
                                                                                                        Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);

            ApiResponse<CompanyProgramParticipantEnrollmentResultDto> response =
                    companyProgramParticipantService.enrollParticipants(companyProgramId, request, userDetails.getUserIdAsUuid());

            return response.isSuccess()
                    ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error enrolling employees for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to enroll employees: " + e.getMessage()));
        }
    }

    @DeleteMapping("/company-program-participants/{participantId}")
    @Operation(summary = "Remove company program employee", description = "Remove an employee from a company program")
    public ResponseEntity<ApiResponse<Void>> removeParticipant(@PathVariable UUID participantId,
                                                               Authentication authentication) {
        try {
            CompanyProgramParticipant participant = companyProgramParticipantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            authorizeCompanyAccess(authentication, participant.getCompanyProgram().getCompany().getId(), true);

            return ResponseEntity.ok(companyProgramParticipantService.removeParticipant(participantId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing company program employee {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove employee: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-programs")
    @Operation(summary = "Get my company programs", description = "Get company programs the authenticated employee is enrolled in")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyPrograms(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<EmployeeCompanyProgramDto> programs =
                    companyProgramParticipantService.getEnrolledProgramsForProfile(userDetails.getUserIdAsUuid());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("programs", programs);
            data.put("count", programs.size());

            return ResponseEntity.ok(ApiResponse.success("Enrolled company programs retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting enrolled company programs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get enrolled company programs: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-journeys")
    @Operation(summary = "Get my company program journeys", description = "Get employee journey progress, sessions, and action items across enrolled company programs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyProgramJourneys(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<EmployeeCompanyProgramJourneyDto> journeys =
                    companyProgramJourneyService.getJourneysForProfile(userDetails.getUserIdAsUuid());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("journeys", journeys);
            data.put("count", journeys.size());

            return ResponseEntity.ok(ApiResponse.success("Company program journeys retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company program journeys: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program journeys: " + e.getMessage()));
        }
    }

    @PostMapping("/me/company-program-journey-steps/{journeyInstanceStepId}/complete")
    @Operation(summary = "Complete my journey step", description = "Mark an employee-owned journey milestone as completed")
    public ResponseEntity<ApiResponse<EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto>> completeMyJourneyStep(
            @PathVariable UUID journeyInstanceStepId,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            EmployeeCompanyProgramJourneyDto.JourneyStepProgressDto step =
                    companyProgramJourneyService.completeJourneyStep(journeyInstanceStepId, userDetails.getUserIdAsUuid());

            return ResponseEntity.ok(ApiResponse.success("Journey step completed successfully", step));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error completing journey step {}: {}", journeyInstanceStepId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete journey step: " + e.getMessage()));
        }
    }

    @GetMapping("/participants/{participantId}/consents")
    @Operation(summary = "Get employee consents", description = "Get current consent decisions for a company-program employee")
    public ResponseEntity<ApiResponse<ParticipantConsentWorkspaceDto>> getParticipantConsents(@PathVariable UUID participantId,
                                                                                              Authentication authentication) {
        try {
            CompanyProgramParticipant participant = companyProgramParticipantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            SupabaseUserDetails userDetails = authorizeParticipantConsentAccess(authentication, participant, true);

            ParticipantConsentWorkspaceDto workspace = participantConsentService.getConsentWorkspace(participantId);

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.PARTICIPANT_CONSENTS,
                    AccessAuditLog.ActionType.VIEW,
                    "PARTICIPANT_CONSENTS_VIEW",
                    participantId,
                    participant.getCompanyProgram().getCompany(),
                    participant.getCompanyProgram(),
                    participant
            );

            return ResponseEntity.ok(ApiResponse.success("Employee consents retrieved successfully", workspace));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting employee consents {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get employee consents: " + e.getMessage()));
        }
    }

    @PostMapping("/participants/{participantId}/consents")
    @Operation(summary = "Record employee consent", description = "Capture or revoke an employee consent decision")
    public ResponseEntity<ApiResponse<ParticipantConsentWorkspaceDto>> updateParticipantConsent(@PathVariable UUID participantId,
                                                                                                @Valid @RequestBody UpdateParticipantConsentRequest request,
                                                                                                Authentication authentication) {
        try {
            CompanyProgramParticipant participant = companyProgramParticipantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            SupabaseUserDetails userDetails = authorizeParticipantConsentAccess(authentication, participant, true);

            ParticipantConsentWorkspaceDto workspace = participantConsentService.recordConsent(
                    participantId,
                    request,
                    userDetails.getUserIdAsUuid()
            );

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.PARTICIPANT_CONSENTS,
                    AccessAuditLog.ActionType.UPDATE,
                    "PARTICIPANT_CONSENT_UPDATE",
                    participantId,
                    participant.getCompanyProgram().getCompany(),
                    participant.getCompanyProgram(),
                    participant
            );

            return ResponseEntity.ok(ApiResponse.success("Employee consent updated successfully", workspace));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating employee consent {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update employee consent: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-consents")
    @Operation(summary = "Get my company program consents", description = "Get the authenticated employee's consent workspaces across enrolled company programs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyProgramConsents(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<ParticipantConsentWorkspaceDto> workspaces =
                    participantConsentService.getConsentWorkspacesForProfile(userDetails.getUserIdAsUuid());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("consents", workspaces);
            data.put("count", workspaces.size());

            return ResponseEntity.ok(ApiResponse.success("Company program consents retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company program consents: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program consents: " + e.getMessage()));
        }
    }

    @PatchMapping("/me/company-program-action-items/{actionItemId}")
    @Operation(summary = "Update my company program action item", description = "Mark an employee-owned journey action item as complete or reopen it")
    public ResponseEntity<ApiResponse<EmployeeCompanyProgramJourneyDto.JourneyActionItemDto>> updateMyJourneyActionItem(
            @PathVariable UUID actionItemId,
            @RequestBody EmployeeCompanyProgramJourneyDto.UpdateJourneyActionItemRequest request,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            boolean completed = request != null && Boolean.TRUE.equals(request.getCompleted());

            EmployeeCompanyProgramJourneyDto.JourneyActionItemDto actionItem =
                    companyProgramJourneyService.updateActionItemCompletion(actionItemId, userDetails.getUserIdAsUuid(), completed);

            String message = completed
                    ? "Journey action item marked as complete"
                    : "Journey action item reopened";

            return ResponseEntity.ok(ApiResponse.success(message, actionItem));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating journey action item {}: {}", actionItemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update journey action item: " + e.getMessage()));
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }

    private SupabaseUserDetails requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        if (userDetails.getUserIdAsUuid() == null) {
            throw new SecurityException("Invalid authenticated user");
        }
        return userDetails;
    }

    private SupabaseUserDetails authorizeCompanyAccess(Authentication authentication,
                                                       UUID companyId,
                                                       boolean companyAdminRequired) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);

        if (userDetails.isAdmin()) {
            return userDetails;
        }

        if (companyAdminRequired && !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID profileCompanyId = profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);

        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access this company");
        }

        return userDetails;
    }

    private SupabaseUserDetails authorizeParticipantConsentAccess(Authentication authentication,
                                                                  CompanyProgramParticipant participant,
                                                                  boolean companyAdminAllowed) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
        UUID actorId = userDetails.getUserIdAsUuid();

        if (userDetails.isAdmin()) {
            return userDetails;
        }

        UUID participantProfileId = participant.getProfile() != null ? participant.getProfile().getId() : null;
        if (participantProfileId != null && participantProfileId.equals(actorId)) {
            return userDetails;
        }

        if (!companyAdminAllowed || !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Not authorized to access this employee consent workspace");
        }

        UUID companyId = participant.getCompanyProgram() != null && participant.getCompanyProgram().getCompany() != null
                ? participant.getCompanyProgram().getCompany().getId()
                : null;
        if (companyId == null) {
            throw new SecurityException("Employee is not linked to a company");
        }

        authorizeCompanyAccess(authentication, companyId, true);
        return userDetails;
    }
}
