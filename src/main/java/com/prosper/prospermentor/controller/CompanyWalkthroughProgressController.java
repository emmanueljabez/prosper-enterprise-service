package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyWalkthroughProgressDto;
import com.prosper.prospermentor.dto.UpdateCompanyWalkthroughProgressRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyWalkthroughProgressService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/walkthrough-progress")
@Tag(name = "Company Walkthrough Progress", description = "Account-aware company onboarding walkthrough progress APIs")
@Slf4j
@RequiredArgsConstructor
public class CompanyWalkthroughProgressController {

    private final CompanyWalkthroughProgressService walkthroughProgressService;
    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get company walkthrough progress", description = "Get walkthrough completion state for the authenticated company admin.")
    public ResponseEntity<ApiResponse<CompanyWalkthroughProgressDto>> getProgress(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String version,
            Authentication authentication
    ) {
        try {
            UUID profileId = authorizeCompanyAdminAccess(authentication, companyId);
            ApiResponse<CompanyWalkthroughProgressDto> response =
                    walkthroughProgressService.getProgress(companyId, profileId, version);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = "Company not found".equals(e.getMessage()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting walkthrough progress for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get walkthrough progress: " + e.getMessage()));
        }
    }

    @PutMapping
    @Operation(summary = "Save company walkthrough progress", description = "Save walkthrough completion state for the authenticated company admin.")
    public ResponseEntity<ApiResponse<CompanyWalkthroughProgressDto>> updateProgress(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyWalkthroughProgressRequest request,
            Authentication authentication
    ) {
        try {
            UUID profileId = authorizeCompanyAdminAccess(authentication, companyId);
            ApiResponse<CompanyWalkthroughProgressDto> response =
                    walkthroughProgressService.updateProgress(companyId, profileId, request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = "Company not found".equals(e.getMessage()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving walkthrough progress for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to save walkthrough progress: " + e.getMessage()));
        }
    }

    private UUID authorizeCompanyAdminAccess(Authentication authentication, UUID companyId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }

        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            throw new SecurityException("Invalid authenticated user");
        }

        if (userDetails.isAdmin()) {
            return userId;
        }

        if (!userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID profileCompanyId = profileService.getProfileWithCompany(userId)
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);

        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access this company");
        }

        return userId;
    }
}
