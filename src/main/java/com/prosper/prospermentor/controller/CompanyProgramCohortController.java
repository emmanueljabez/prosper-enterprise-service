package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyProgramCohortDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortWorkspaceDto;
import com.prosper.prospermentor.dto.CohortSelfJoinRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinResponseDto;
import com.prosper.prospermentor.dto.CohortPlenaryAttendanceDto;
import com.prosper.prospermentor.dto.CircleSuggestionResultDto;
import com.prosper.prospermentor.dto.CommonInterestCircleDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.dto.ConfirmCohortJoinRequest;
import com.prosper.prospermentor.dto.CreateCommonInterestCircleRequest;
import com.prosper.prospermentor.dto.CreateCompanyProgramCohortRequest;
import com.prosper.prospermentor.dto.EmployeeCircleRequestRequest;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramCohortDto;
import com.prosper.prospermentor.dto.MoveCircleMembershipRequest;
import com.prosper.prospermentor.dto.PlenaryAttendanceImportRow;
import com.prosper.prospermentor.dto.PlaceCircleParticipantRequest;
import com.prosper.prospermentor.dto.RecordPlenaryAttendanceRequest;
import com.prosper.prospermentor.dto.ResolveCohortDuplicateRequest;
import com.prosper.prospermentor.dto.UpdateCommonInterestCircleRequest;
import com.prosper.prospermentor.dto.UpdateCompanyProgramCohortRequest;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CommonInterestCircleService;
import com.prosper.prospermentor.service.CompanyProgramCohortIntakeService;
import com.prosper.prospermentor.service.CompanyProgramCohortService;
import com.prosper.prospermentor.service.CompanyProgramService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Company Program Cohorts", description = "Cohort and circle delivery for company mentorship programs")
@RequiredArgsConstructor
@Slf4j
public class CompanyProgramCohortController {

    private final CompanyProgramCohortService cohortService;
    private final CompanyProgramCohortIntakeService intakeService;
    private final CommonInterestCircleService circleService;
    private final CompanyProgramService companyProgramService;
    private final ProfileService profileService;

    @GetMapping("/company-programs/{companyProgramId}/cohorts")
    @Operation(summary = "Get company program cohorts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCohorts(@PathVariable UUID companyProgramId,
                                                                       Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);

            List<CompanyProgramCohortDto> cohorts = cohortService.getCohorts(companyProgramId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyProgramId", companyProgramId);
            data.put("cohorts", cohorts);
            data.put("count", cohorts.size());

            return ResponseEntity.ok(ApiResponse.success("Company program cohorts retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohorts for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program cohorts: " + e.getMessage()));
        }
    }

    @PostMapping("/company-programs/{companyProgramId}/cohorts")
    @Operation(summary = "Create company program cohort")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> createCohort(@PathVariable UUID companyProgramId,
                                                                             @Valid @RequestBody CreateCompanyProgramCohortRequest request,
                                                                             Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);

            CompanyProgramCohortDto cohort = cohortService.createCohort(companyProgramId, request, userDetails.getUserIdAsUuid());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Company program cohort created successfully", cohort));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating cohort for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create company program cohort: " + e.getMessage()));
        }
    }

    @GetMapping("/company-program-cohorts/{cohortId}")
    @Operation(summary = "Get company program cohort")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> getCohort(@PathVariable UUID cohortId,
                                                                          Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            return ResponseEntity.ok(ApiResponse.success("Company program cohort retrieved successfully", cohort));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program cohort: " + e.getMessage()));
        }
    }

    @PatchMapping("/company-program-cohorts/{cohortId}")
    @Operation(summary = "Update company program cohort")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> updateCohort(@PathVariable UUID cohortId,
                                                                             @RequestBody UpdateCompanyProgramCohortRequest request,
                                                                             Authentication authentication) {
        try {
            CompanyProgramCohortDto existing = cohortService.getCohort(cohortId);
            if (existing.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, existing.getCompanyId(), true);
            }
            CompanyProgramCohortDto updated = cohortService.updateCohort(cohortId, request);
            return ResponseEntity.ok(ApiResponse.success("Company program cohort updated successfully", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company program cohort: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/open-intake")
    @Operation(summary = "Open company program cohort intake")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> openIntake(@PathVariable UUID cohortId,
                                                                           Authentication authentication) {
        return runCohortAction(cohortId, authentication, true);
    }

    @PostMapping("/company-program-cohorts/{cohortId}/close-intake")
    @Operation(summary = "Close company program cohort intake")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> closeIntake(@PathVariable UUID cohortId,
                                                                            Authentication authentication) {
        return runCohortAction(cohortId, authentication, false);
    }

    @GetMapping("/company-program-cohorts/join/{joinCode}")
    @Operation(summary = "Preview a cohort self-join code")
    public ResponseEntity<ApiResponse<CohortSelfJoinResponseDto>> getJoinPreview(@PathVariable String joinCode) {
        try {
            CohortSelfJoinResponseDto preview = intakeService.getSelfJoinPreview(joinCode);
            return ResponseEntity.ok(ApiResponse.success("Cohort join preview retrieved successfully", preview));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error previewing cohort join code: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to preview cohort join code: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/join/{joinCode}")
    @Operation(summary = "Submit a cohort self-join request")
    public ResponseEntity<ApiResponse<CohortSelfJoinResponseDto>> submitSelfJoin(@PathVariable String joinCode,
                                                                                 @Valid @RequestBody CohortSelfJoinRequest request) {
        try {
            CohortSelfJoinResponseDto response = intakeService.submitSelfJoin(joinCode, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Cohort join request submitted successfully", response));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting cohort join request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to submit cohort join request: " + e.getMessage()));
        }
    }

    @GetMapping("/company-program-cohorts/{cohortId}/participants")
    @Operation(summary = "Get cohort participants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCohortParticipants(@PathVariable UUID cohortId,
                                                                                  Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            List<CompanyProgramCohortParticipantDto> participants = intakeService.getParticipants(cohortId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohortId", cohortId);
            data.put("participants", participants);
            data.put("count", participants.size());
            return ResponseEntity.ok(ApiResponse.success("Cohort participants retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohort participants {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get cohort participants: " + e.getMessage()));
        }
    }

    @GetMapping("/company-program-cohorts/{cohortId}/plenary")
    @Operation(summary = "Get cohort plenary attendance workspace")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlenaryWorkspace(@PathVariable UUID cohortId,
                                                                                Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            List<CompanyProgramCohortParticipantDto> participants = intakeService.getParticipants(cohortId);
            List<CohortPlenaryAttendanceDto> attendance = intakeService.getPlenaryAttendance(cohortId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohort", cohort);
            data.put("participants", participants);
            data.put("attendance", attendance);
            data.put("participantCount", participants.size());
            data.put("attendedCount", attendance.stream()
                    .filter(row -> row.getStatus() == CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED)
                    .count());
            return ResponseEntity.ok(ApiResponse.success("Cohort plenary workspace retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohort plenary workspace {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get cohort plenary workspace: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/plenary/link-event")
    @Operation(summary = "Link cohort plenary event metadata")
    public ResponseEntity<ApiResponse<CompanyProgramCohortDto>> linkPlenaryEvent(@PathVariable UUID cohortId,
                                                                                 @RequestBody UpdateCompanyProgramCohortRequest request,
                                                                                 Authentication authentication) {
        try {
            CompanyProgramCohortDto existing = cohortService.getCohort(cohortId);
            if (existing.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, existing.getCompanyId(), true);
            }
            CompanyProgramCohortDto updated = cohortService.updateCohort(cohortId, request);
            return ResponseEntity.ok(ApiResponse.success("Cohort plenary event linked successfully", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error linking plenary event for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to link cohort plenary event: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/plenary/attendance/import")
    @Operation(summary = "Import cohort plenary attendance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importPlenaryAttendance(
            @PathVariable UUID cohortId,
            @RequestBody List<PlenaryAttendanceImportRow> rows,
            Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            List<CompanyProgramCohortParticipantDto> updated = intakeService.importPlenaryAttendance(
                    cohortId,
                    rows,
                    userDetails.getUserIdAsUuid()
            );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohortId", cohortId);
            data.put("participants", updated);
            data.put("updatedCount", updated.size());
            return ResponseEntity.ok(ApiResponse.success("Cohort plenary attendance imported successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error importing plenary attendance for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to import cohort plenary attendance: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-participants/{participantId}/plenary-attendance")
    @Operation(summary = "Record cohort participant plenary attendance")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> recordPlenaryAttendance(
            @PathVariable UUID participantId,
            @RequestBody(required = false) RecordPlenaryAttendanceRequest request,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.recordPlenaryAttendance(
                    participantId,
                    request != null ? request.getStatus() : null,
                    request != null ? request.getAttendanceSource() : null,
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Cohort participant plenary attendance recorded successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error recording plenary attendance for cohort participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to record cohort participant plenary attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/company-program-cohorts/{cohortId}/circles")
    @Operation(summary = "Get cohort common interest circles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCircles(@PathVariable UUID cohortId,
                                                                       Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            List<CommonInterestCircleDto> circles = circleService.getCircles(cohortId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohortId", cohortId);
            data.put("circles", circles);
            data.put("count", circles.size());
            return ResponseEntity.ok(ApiResponse.success("Cohort circles retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohort circles {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get cohort circles: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/circles")
    @Operation(summary = "Create cohort common interest circle")
    public ResponseEntity<ApiResponse<CommonInterestCircleDto>> createCircle(@PathVariable UUID cohortId,
                                                                            @Valid @RequestBody CreateCommonInterestCircleRequest request,
                                                                            Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            CommonInterestCircleDto circle = circleService.createCircle(cohortId, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Cohort circle created successfully", circle));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating cohort circle for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create cohort circle: " + e.getMessage()));
        }
    }

    @PatchMapping("/common-interest-circles/{circleId}")
    @Operation(summary = "Update common interest circle")
    public ResponseEntity<ApiResponse<CommonInterestCircleDto>> updateCircle(@PathVariable UUID circleId,
                                                                            @RequestBody UpdateCommonInterestCircleRequest request,
                                                                            Authentication authentication) {
        try {
            authorizeCohortOperatorRole(authentication);
            CommonInterestCircleDto circle = circleService.updateCircle(circleId, request);
            return ResponseEntity.ok(ApiResponse.success("Common interest circle updated successfully", circle));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating common interest circle {}: {}", circleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update common interest circle: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/circle-suggestions")
    @Operation(summary = "Suggest cohort common interest circles")
    public ResponseEntity<ApiResponse<CircleSuggestionResultDto>> suggestCircles(@PathVariable UUID cohortId,
                                                                                Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            CircleSuggestionResultDto result = circleService.suggestCircles(cohortId);
            return ResponseEntity.ok(ApiResponse.success("Cohort circle suggestions generated successfully", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error suggesting circles for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to suggest cohort circles: " + e.getMessage()));
        }
    }

    @PostMapping("/common-interest-circles/{circleId}/members")
    @Operation(summary = "Place participant in common interest circle")
    public ResponseEntity<ApiResponse<CommonInterestCircleDto>> placeParticipant(@PathVariable UUID circleId,
                                                                                @RequestBody PlaceCircleParticipantRequest request,
                                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CommonInterestCircleDto circle = circleService.placeParticipant(
                    circleId,
                    request.getCohortParticipantId(),
                    request.getPlacementSource(),
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Participant placed in circle successfully", circle));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error placing participant in circle {}: {}", circleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to place participant in circle: " + e.getMessage()));
        }
    }

    @DeleteMapping("/common-interest-circle-memberships/{membershipId}")
    @Operation(summary = "Remove common interest circle membership")
    public ResponseEntity<ApiResponse<CommonInterestCircleDto>> removeMembership(@PathVariable UUID membershipId,
                                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CommonInterestCircleDto circle = circleService.removeMembership(membershipId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Circle membership removed successfully", circle));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing circle membership {}: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove circle membership: " + e.getMessage()));
        }
    }

    @PostMapping("/common-interest-circle-memberships/{membershipId}/move")
    @Operation(summary = "Move common interest circle membership")
    public ResponseEntity<ApiResponse<CommonInterestCircleDto>> moveMembership(@PathVariable UUID membershipId,
                                                                              @RequestBody MoveCircleMembershipRequest request,
                                                                              Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CommonInterestCircleDto circle = circleService.moveMembership(
                    membershipId,
                    request.getTargetCircleId(),
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Circle membership moved successfully", circle));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error moving circle membership {}: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to move circle membership: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohorts/{cohortId}/circles/finalize")
    @Operation(summary = "Finalize cohort common interest circles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> finalizeCircles(@PathVariable UUID cohortId,
                                                                           Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            List<CommonInterestCircleDto> circles = circleService.finalizeCircles(cohortId, userDetails.getUserIdAsUuid());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohortId", cohortId);
            data.put("circles", circles);
            data.put("count", circles.size());
            return ResponseEntity.ok(ApiResponse.success("Cohort circles finalized successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error finalizing circles for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to finalize cohort circles: " + e.getMessage()));
        }
    }

    @GetMapping("/company-program-cohorts/{cohortId}/dashboard")
    @Operation(summary = "Get cohort dashboard metrics")
    public ResponseEntity<ApiResponse<CompanyProgramCohortWorkspaceDto>> getCohortDashboard(@PathVariable UUID cohortId,
                                                                                            Authentication authentication) {
        try {
            CompanyProgramCohortDto cohort = cohortService.getCohort(cohortId);
            if (cohort.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, cohort.getCompanyId(), true);
            }
            CompanyProgramCohortWorkspaceDto dashboard = cohortService.getCohortDashboard(cohortId);
            return ResponseEntity.ok(ApiResponse.success("Cohort dashboard retrieved successfully", dashboard));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting cohort dashboard {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get cohort dashboard: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-cohorts")
    @Operation(summary = "Get my company program cohorts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyProgramCohorts(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            List<EmployeeCompanyProgramCohortDto> cohorts = cohortService.getEmployeeCohorts(userDetails.getUserIdAsUuid());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohorts", cohorts);
            data.put("count", cohorts.size());
            return ResponseEntity.ok(ApiResponse.success("Employee company program cohorts retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting employee company program cohorts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get employee company program cohorts: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-cohorts/{cohortId}")
    @Operation(summary = "Get my company program cohort")
    public ResponseEntity<ApiResponse<EmployeeCompanyProgramCohortDto>> getMyCompanyProgramCohort(
            @PathVariable UUID cohortId,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            EmployeeCompanyProgramCohortDto cohort = cohortService.getEmployeeCohort(cohortId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Employee company program cohort retrieved successfully", cohort));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting employee company program cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get employee company program cohort: " + e.getMessage()));
        }
    }

    @GetMapping("/me/company-program-cohorts/{cohortId}/circles")
    @Operation(summary = "Get my cohort circles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyCompanyProgramCohortCircles(
            @PathVariable UUID cohortId,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            cohortService.getEmployeeCohort(cohortId, userDetails.getUserIdAsUuid());
            List<CommonInterestCircleDto> circles = circleService.getCircles(cohortId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cohortId", cohortId);
            data.put("circles", circles);
            data.put("count", circles.size());
            return ResponseEntity.ok(ApiResponse.success("Employee cohort circles retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting employee cohort circles {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get employee cohort circles: " + e.getMessage()));
        }
    }

    @PostMapping("/me/company-program-cohorts/{cohortId}/circle-requests")
    @Operation(summary = "Request a cohort circle")
    public ResponseEntity<ApiResponse<EmployeeCompanyProgramCohortDto>> requestMyCompanyProgramCohortCircle(
            @PathVariable UUID cohortId,
            @RequestBody EmployeeCircleRequestRequest request,
            Authentication authentication) {
        try {
            if (request == null || request.getCircleId() == null) {
                throw new IllegalArgumentException("circleId is required");
            }
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            EmployeeCompanyProgramCohortDto cohort = cohortService.requestEmployeeCircle(
                    cohortId,
                    userDetails.getUserIdAsUuid(),
                    request.getCircleId()
            );
            return ResponseEntity.ok(ApiResponse.success("Employee cohort circle request submitted successfully", cohort));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error requesting employee cohort circle {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to request employee cohort circle: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-join-requests/{joinRequestId}/confirm")
    @Operation(summary = "Confirm a cohort join request")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> confirmJoinRequest(
            @PathVariable UUID joinRequestId,
            @Valid @RequestBody ConfirmCohortJoinRequest request,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.confirmJoinRequest(
                    joinRequestId,
                    request.getProfileId(),
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Cohort join request confirmed successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming cohort join request {}: {}", joinRequestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to confirm cohort join request: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-join-requests/{joinRequestId}/reject")
    @Operation(summary = "Reject a cohort join request")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> rejectJoinRequest(@PathVariable UUID joinRequestId,
                                                                                             Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.rejectJoinRequest(joinRequestId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Cohort join request rejected successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error rejecting cohort join request {}: {}", joinRequestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reject cohort join request: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-participants/{participantId}/confirm")
    @Operation(summary = "Confirm a cohort participant")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> confirmParticipant(@PathVariable UUID participantId,
                                                                                              Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.confirmParticipant(participantId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Cohort participant confirmed successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming cohort participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to confirm cohort participant: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-participants/{participantId}/reject")
    @Operation(summary = "Reject a cohort participant")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> rejectParticipant(@PathVariable UUID participantId,
                                                                                             Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.rejectParticipant(participantId, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Cohort participant rejected successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error rejecting cohort participant {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reject cohort participant: " + e.getMessage()));
        }
    }

    @PostMapping("/company-program-cohort-participants/{participantId}/resolve-duplicate")
    @Operation(summary = "Resolve a cohort participant duplicate")
    public ResponseEntity<ApiResponse<CompanyProgramCohortParticipantDto>> resolveDuplicate(
            @PathVariable UUID participantId,
            @Valid @RequestBody ResolveCohortDuplicateRequest request,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCohortOperatorRole(authentication);
            CompanyProgramCohortParticipantDto participant = intakeService.resolveDuplicate(participantId, request, userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Cohort participant duplicate resolved successfully", participant));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error resolving cohort duplicate {}: {}", participantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resolve cohort duplicate: " + e.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<CompanyProgramCohortDto>> runCohortAction(UUID cohortId,
                                                                                 Authentication authentication,
                                                                                 boolean open) {
        try {
            CompanyProgramCohortDto existing = cohortService.getCohort(cohortId);
            if (existing.getCompanyId() != null) {
                authorizeCompanyAccess(authentication, existing.getCompanyId(), true);
            }
            CompanyProgramCohortDto updated = open ? cohortService.openIntake(cohortId) : cohortService.closeIntake(cohortId);
            String message = open ? "Company program cohort intake opened" : "Company program cohort intake closed";
            return ResponseEntity.ok(ApiResponse.success(message, updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating intake for cohort {}: {}", cohortId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company program cohort intake: " + e.getMessage()));
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

    private SupabaseUserDetails authorizeCohortOperatorRole(Authentication authentication) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
        if (!userDetails.isAdmin() && !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }
        return userDetails;
    }
}
