package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.ReviewAlertAdminDto;
import com.prosper.prospermentor.dto.ReviewAlertRematchResultDto;
import com.prosper.prospermentor.dto.ReviewAlertSummaryDto;
import com.prosper.prospermentor.dto.UpdateReviewAlertStatusRequest;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.AccessAuditService;
import com.prosper.prospermentor.service.ProfileService;
import com.prosper.prospermentor.service.ReviewAlertAdminService;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Review Alerts", description = "Company admin review alert operations and analytics")
@RequiredArgsConstructor
@Slf4j
public class ReviewAlertAdminController {

    private final ReviewAlertAdminService reviewAlertAdminService;
    private final ProfileService profileService;
    private final AccessAuditService accessAuditService;

    @GetMapping("/companies/{companyId}/review-alerts")
    @Operation(summary = "Get company review alerts", description = "List review alerts for a company with optional filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReviewAlerts(@PathVariable UUID companyId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size,
                                                                            @RequestParam(required = false) UUID companyProgramId,
                                                                            @RequestParam(required = false) ReviewAlert.ReviewAlertStatus status,
                                                                            @RequestParam(required = false) ReviewAlert.Severity severity,
                                                                            @RequestParam(required = false) ReviewAlert.ReviewAlertType alertType,
                                                                            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ReviewAlertAdminDto> alerts = reviewAlertAdminService.getCompanyAlerts(
                    companyId,
                    companyProgramId,
                    status,
                    severity,
                    alertType,
                    pageable
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyId", companyId);
            data.put("companyProgramId", companyProgramId);
            data.put("alerts", alerts.getContent());
            data.put("count", alerts.getNumberOfElements());
            data.put("currentPage", alerts.getNumber());
            data.put("pageSize", alerts.getSize());
            data.put("totalPages", alerts.getTotalPages());
            data.put("totalItems", alerts.getTotalElements());
            data.put("hasNext", alerts.hasNext());
            data.put("hasPrevious", alerts.hasPrevious());
            data.put("status", status != null ? status.name() : null);
            data.put("severity", severity != null ? severity.name() : null);
            data.put("alertType", alertType != null ? alertType.name() : null);

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.REVIEW_ALERT_QUEUE,
                    AccessAuditLog.ActionType.VIEW,
                    "REVIEW_ALERT_QUEUE_VIEW",
                    companyProgramId != null ? companyProgramId : companyId,
                    profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                            .map(com.prosper.prospermentor.entity.Profile::getCompany)
                            .orElse(null),
                    null,
                    null
            );

            return ResponseEntity.ok(ApiResponse.success("Review alerts retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting review alerts for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get review alerts: " + e.getMessage()));
        }
    }

    @GetMapping("/companies/{companyId}/review-alerts/summary")
    @Operation(summary = "Get review alert summary", description = "Get company-level review operations summary metrics")
    public ResponseEntity<ApiResponse<ReviewAlertSummaryDto>> getReviewAlertSummary(@PathVariable UUID companyId,
                                                                                    @RequestParam(required = false) UUID companyProgramId,
                                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                                                    Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            validateDateRange(startDate, endDate);
            ReviewAlertSummaryDto summary = reviewAlertAdminService.getCompanyAlertSummary(companyId, companyProgramId, startDate, endDate);

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.REVIEW_ALERT_SUMMARY,
                    AccessAuditLog.ActionType.VIEW,
                    "REVIEW_ALERT_SUMMARY_VIEW",
                    companyProgramId != null ? companyProgramId : companyId,
                    profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                            .map(com.prosper.prospermentor.entity.Profile::getCompany)
                            .orElse(null),
                    null,
                    null
            );

            return ResponseEntity.ok(ApiResponse.success("Review summary retrieved successfully", summary));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting review summary for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get review summary: " + e.getMessage()));
        }
    }

    @PatchMapping("/companies/{companyId}/review-alerts/{alertId}")
    @Operation(summary = "Update review alert status", description = "Acknowledge or resolve a review alert")
    public ResponseEntity<ApiResponse<ReviewAlertAdminDto>> updateReviewAlertStatus(@PathVariable UUID companyId,
                                                                                     @PathVariable UUID alertId,
                                                                                     @Valid @RequestBody UpdateReviewAlertStatusRequest request,
                                                                                     Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<ReviewAlertAdminDto> response = reviewAlertAdminService.updateAlertStatus(
                    companyId,
                    alertId,
                    request.getStatus()
            );

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.REVIEW_ALERT_QUEUE,
                    AccessAuditLog.ActionType.UPDATE,
                    "REVIEW_ALERT_STATUS_UPDATE",
                    alertId,
                    profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                            .map(com.prosper.prospermentor.entity.Profile::getCompany)
                            .orElse(null),
                    null,
                    null
            );
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating review alert {} for company {}: {}", alertId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update review alert: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/review-alerts/{alertId}/rematch")
    @Operation(summary = "Trigger rematch workflow", description = "Remove the active mentor assignment for an alert and mark related alerts resolved")
    public ResponseEntity<ApiResponse<ReviewAlertRematchResultDto>> triggerRematch(@PathVariable UUID companyId,
                                                                                   @PathVariable UUID alertId,
                                                                                   Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<ReviewAlertRematchResultDto> response = reviewAlertAdminService.triggerRematch(
                    companyId,
                    alertId,
                    userDetails.getUserIdAsUuid()
            );

            accessAuditService.record(
                    userDetails,
                    AccessAuditLog.ResourceType.REVIEW_ALERT_QUEUE,
                    AccessAuditLog.ActionType.REMATCH,
                    "REVIEW_ALERT_REMATCH_TRIGGERED",
                    alertId,
                    profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                            .map(com.prosper.prospermentor.entity.Profile::getCompany)
                            .orElse(null),
                    null,
                    null
            );
            return response.isSuccess()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error triggering rematch from alert {} for company {}: {}", alertId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to trigger rematch: " + e.getMessage()));
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

    private SupabaseUserDetails authorizeCompanyAccess(Authentication authentication,
                                                       UUID companyId,
                                                       boolean companyAdminRequired) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }

        if (userDetails.isAdmin()) {
            return userDetails;
        }

        if (companyAdminRequired && !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            throw new SecurityException("Invalid authenticated user");
        }

        UUID profileCompanyId = profileService.getProfileWithCompany(userId)
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);

        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access this company");
        }

        return userDetails;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }
}
