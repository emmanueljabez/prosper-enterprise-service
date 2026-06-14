package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard endpoints for authenticated users.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyDashboard(
            Authentication authentication,
            @RequestParam(defaultValue = "last_30_days") String period
    ) {
        String userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }

        try {
            UUID userUuid = UUID.fromString(userId);
            Map<String, Object> dashboardData = dashboardService.buildMenteeDashboard(userUuid, period);
            return ResponseEntity.ok(ApiResponse.success("Dashboard retrieved successfully", dashboardData));
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to load dashboard for user {}: {}", userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error while loading dashboard for user {}", userId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to load dashboard"));
        }
    }

    @GetMapping("/company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyDashboard(
            Authentication authentication,
            @RequestParam UUID companyId,
            @RequestParam(defaultValue = "last_30_days") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        String userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }

        try {
            validateDateRange(startDate, endDate);
            Map<String, Object> dashboardData = dashboardService.buildCompanyDashboard(companyId, period, startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success("Company dashboard retrieved successfully", dashboardData));
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to load company dashboard {} for user {}: {}", companyId, userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error while loading company dashboard {} for user {}", companyId, userId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to load company dashboard"));
        }
    }

    private String extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            return userDetails.getUserId();
        }

        if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
            return principal.getUserId();
        }

        return null;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }
}
