package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyParticipantPulseSummaryDto;
import com.prosper.prospermentor.dto.ParticipantPulseDto;
import com.prosper.prospermentor.dto.ParticipantPulseSummaryDto;
import com.prosper.prospermentor.dto.SubmitParticipantPulseResponseRequest;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyProgramParticipantService;
import com.prosper.prospermentor.service.ParticipantPulseService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Participant Pulses", description = "WhatsApp-first baseline and program-end pulse APIs")
@RequiredArgsConstructor
@Slf4j
public class ParticipantPulseController {

    private final ParticipantPulseService participantPulseService;
    private final CompanyProgramParticipantService companyProgramParticipantService;
    private final ProfileService profileService;

    @GetMapping("/me/participant-pulses")
    @Operation(summary = "Get my participant pulses", description = "Get the authenticated employee's pulse status workspace")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyParticipantPulses(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<ParticipantPulseDto> pulses = participantPulseService.getPulsesForProfile(userDetails.getUserIdAsUuid());
            ParticipantPulseSummaryDto summary = participantPulseService.summarizePulses(pulses);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", summary);
            data.put("pulses", pulses);
            data.put("count", pulses.size());

            return ResponseEntity.ok(ApiResponse.success("Participant pulses retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting participant pulses: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get participant pulses: " + e.getMessage()));
        }
    }

    @GetMapping("/participants/{participantId}/pulses")
    @Operation(summary = "Get employee pulses", description = "Get pulse history for a company-program employee")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getParticipantPulses(@PathVariable UUID participantId,
                                                                                 Authentication authentication) {
        try {
            CompanyProgramParticipant participant = companyProgramParticipantService.getParticipant(participantId)
                    .orElseThrow(() -> new NoSuchElementException("Company program employee not found"));
            authorizeCompanyAccess(authentication, participant);

            List<ParticipantPulseDto> pulses = participantPulseService.getPulsesForParticipant(participantId);
            ParticipantPulseSummaryDto summary = participantPulseService.summarizePulses(pulses);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("participantId", participantId);
            data.put("summary", summary);
            data.put("pulses", pulses);
            data.put("count", pulses.size());

            return ResponseEntity.ok(ApiResponse.success("Employee pulses retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting employee pulses {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get employee pulses: " + e.getMessage()));
        }
    }

    @PostMapping("/participant-pulses/{pulseId}/responses")
    @Operation(summary = "Submit participant pulse response", description = "Store a completed pulse response")
    public ResponseEntity<ApiResponse<ParticipantPulseDto>> submitPulseResponse(@PathVariable UUID pulseId,
                                                                                @Valid @RequestBody SubmitParticipantPulseResponseRequest request,
                                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            ParticipantPulse pulse = participantPulseService.getPulse(pulseId);
            authorizePulseSubmission(userDetails, pulse);

            ParticipantPulseDto response = participantPulseService.submitPulseResponse(pulseId, request);
            return ResponseEntity.ok(ApiResponse.success("Participant pulse recorded successfully", response));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error recording participant pulse {}: {}", pulseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to record participant pulse: " + e.getMessage()));
        }
    }

    @GetMapping("/companies/{companyId}/participant-pulses/summary")
    @Operation(summary = "Get company pulse summary", description = "Get aggregate pulse completion and score metrics for a company")
    public ResponseEntity<ApiResponse<CompanyParticipantPulseSummaryDto>> getCompanyPulseSummary(@PathVariable UUID companyId,
                                                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                                                                 Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId);
            validateDateRange(startDate, endDate);
            CompanyParticipantPulseSummaryDto summary = participantPulseService.getCompanySummary(companyId, startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success("Company pulse summary retrieved successfully", summary));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company pulse summary {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company pulse summary: " + e.getMessage()));
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

    private void authorizePulseSubmission(SupabaseUserDetails userDetails, ParticipantPulse pulse) {
        CompanyProgramParticipant participant = pulse.getParticipant();
        if (participant == null || participant.getProfile() == null || participant.getCompanyProgram() == null
                || participant.getCompanyProgram().getCompany() == null) {
            throw new SecurityException("Pulse is not linked to a valid employee record");
        }

        UUID actorId = userDetails.getUserIdAsUuid();
        UUID employeeId = participant.getProfile().getId();
        UUID companyId = participant.getCompanyProgram().getCompany().getId();

        if (Objects.equals(actorId, employeeId)) {
            return;
        }

        authorizeCompanyAccess(userDetails, companyId);
    }

    private void authorizeCompanyAccess(Authentication authentication, CompanyProgramParticipant participant) {
        if (participant == null || participant.getCompanyProgram() == null || participant.getCompanyProgram().getCompany() == null) {
            throw new SecurityException("Company context is missing");
        }
        authorizeCompanyAccess(requireAuthenticatedUser(authentication), participant.getCompanyProgram().getCompany().getId());
    }

    private void authorizeCompanyAccess(Authentication authentication, UUID companyId) {
        authorizeCompanyAccess(requireAuthenticatedUser(authentication), companyId);
    }

    private void authorizeCompanyAccess(SupabaseUserDetails userDetails, UUID companyId) {
        var profile = profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                .orElseThrow(() -> new SecurityException("Profile not found"));

        if (profile.getCompany() == null || !companyId.equals(profile.getCompany().getId())) {
            throw new SecurityException("You do not have access to this company");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }
}
