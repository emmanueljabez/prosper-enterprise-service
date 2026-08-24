package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyProgramCohortDto;
import com.prosper.prospermentor.dto.CreateCompanyProgramCohortRequest;
import com.prosper.prospermentor.dto.UpdateCompanyProgramCohortRequest;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
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
}
