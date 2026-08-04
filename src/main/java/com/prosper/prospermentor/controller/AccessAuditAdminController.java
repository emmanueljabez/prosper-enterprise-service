package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.AccessAuditLogDto;
import com.prosper.prospermentor.dto.AccessAuditSummaryDto;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.AccessAuditService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Access Audit", description = "Company admin access audit operations")
@RequiredArgsConstructor
@Slf4j
public class AccessAuditAdminController {

    private final AccessAuditService accessAuditService;
    private final ProfileService profileService;

    @GetMapping("/companies/{companyId}/access-audit-logs")
    @Operation(summary = "Get company access audit logs", description = "List auditable access events for a company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccessAuditLogs(@PathVariable UUID companyId,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size,
                                                                               @RequestParam(required = false) UUID companyProgramId,
                                                                               @RequestParam(required = false) AccessAuditLog.ResourceType resourceType,
                                                                               @RequestParam(required = false) AccessAuditLog.ActionType action,
                                                                               Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<AccessAuditLogDto> auditPage = accessAuditService.getCompanyAuditLogs(
                    companyId,
                    companyProgramId,
                    resourceType,
                    action,
                    pageable
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyId", companyId);
            data.put("companyProgramId", companyProgramId);
            data.put("logs", auditPage.getContent());
            data.put("count", auditPage.getNumberOfElements());
            data.put("currentPage", auditPage.getNumber());
            data.put("pageSize", auditPage.getSize());
            data.put("totalPages", auditPage.getTotalPages());
            data.put("totalItems", auditPage.getTotalElements());
            data.put("hasNext", auditPage.hasNext());
            data.put("hasPrevious", auditPage.hasPrevious());
            data.put("resourceType", resourceType != null ? resourceType.name() : null);
            data.put("action", action != null ? action.name() : null);

            return ResponseEntity.ok(ApiResponse.success("Access audit logs retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting access audit logs for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get access audit logs: " + e.getMessage()));
        }
    }

    @GetMapping("/companies/{companyId}/access-audit-logs/summary")
    @Operation(summary = "Get company access audit summary", description = "Get summary metrics for sensitive resource access")
    public ResponseEntity<ApiResponse<AccessAuditSummaryDto>> getAccessAuditSummary(@PathVariable UUID companyId,
                                                                                    Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            AccessAuditSummaryDto summary = accessAuditService.getCompanyAuditSummary(companyId);
            return ResponseEntity.ok(ApiResponse.success("Access audit summary retrieved successfully", summary));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting access audit summary for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get access audit summary: " + e.getMessage()));
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
}
