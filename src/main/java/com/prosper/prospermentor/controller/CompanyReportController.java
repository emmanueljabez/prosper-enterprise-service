package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyReportDtos;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyReportService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports")
@Tag(name = "Company Reports", description = "Dedicated row-based company reporting APIs")
@RequiredArgsConstructor
@Slf4j
public class CompanyReportController {

    private final CompanyReportService companyReportService;
    private final ProfileService profileService;

    @GetMapping("/programs")
    @Operation(summary = "Get company program report", description = "Row-based report of company programs")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.ProgramReportRowDto>>> getProgramReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CompanyProgram.CompanyProgramStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Program report retrieved successfully", () ->
                companyReportService.getProgramReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/participants")
    @Operation(summary = "Get participant report", description = "Row-based report of company program participants")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.ParticipantReportRowDto>>> getParticipantReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CompanyProgramParticipant.ParticipantStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Participant report retrieved successfully", () ->
                companyReportService.getParticipantReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/matches")
    @Operation(summary = "Get mentor match report", description = "Row-based report of company mentor matching status")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.MentorMatchReportRowDto>>> getMatchReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CompanyProgramMatchWorkspace.MatchStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Mentor match report retrieved successfully", () ->
                companyReportService.getMatchReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Get session report", description = "Row-based report of corporate mentorship sessions")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.SessionReportRowDto>>> getSessionReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Session.SessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Session report retrieved successfully", () ->
                companyReportService.getSessionReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/pulses")
    @Operation(summary = "Get pulse coverage report", description = "Row-based report of pulse completion grouped by company program")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.PulseCoverageReportRowDto>>> getPulseCoverageReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParticipantPulse.PulseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Pulse coverage report retrieved successfully", () ->
                companyReportService.getPulseCoverageReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/risk-signals")
    @Operation(summary = "Get risk signals report", description = "Row-based report of open and historical review risk signals")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.RiskSignalReportRowDto>>> getRiskSignalsReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ReviewAlert.ReviewAlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Risk signals report retrieved successfully", () ->
                companyReportService.getRiskSignalsReport(companyId, page, size, search, status, startDate, endDate));
    }

    @GetMapping("/billing-transactions")
    @Operation(summary = "Get billing transactions report", description = "Row-based report of corporate billing transactions")
    public ResponseEntity<ApiResponse<CompanyReportDtos.ReportListDto<CompanyReportDtos.BillingTransactionReportRowDto>>> getBillingTransactionsReport(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return reportResponse(companyId, authentication, "Billing transactions report retrieved successfully", () ->
                companyReportService.getBillingTransactionsReport(companyId, page, size, search, status, startDate, endDate));
    }

    private <T> ResponseEntity<ApiResponse<T>> reportResponse(UUID companyId,
                                                              Authentication authentication,
                                                              String successMessage,
                                                              Supplier<T> supplier) {
        try {
            authorizeCompanyAccess(authentication, companyId);
            return ResponseEntity.ok(ApiResponse.success(successMessage, supplier.get()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error building report for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to build report: " + e.getMessage()));
        }
    }

    private void authorizeCompanyAccess(Authentication authentication, UUID companyId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }

        if (userDetails.isAdmin()) {
            return;
        }

        if (!userDetails.isCompanyAdmin()) {
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
    }
}
