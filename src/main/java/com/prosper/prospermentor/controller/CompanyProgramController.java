package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyProgramDto;
import com.prosper.prospermentor.dto.CreateCompanyProgramRequest;
import com.prosper.prospermentor.dto.UpdateCompanyProgramRequest;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyProgramService;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Company Programs", description = "Company mentorship program runtime APIs")
@RequiredArgsConstructor
@Slf4j
public class CompanyProgramController {

    private final CompanyProgramService companyProgramService;
    private final ProfileService profileService;

    @GetMapping("/companies/{companyId}/programs")
    @Operation(summary = "Get company programs", description = "List launched or draft mentorship programs for a company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyPrograms(@PathVariable UUID companyId,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size,
                                                                               @RequestParam(required = false) String search,
                                                                               @RequestParam(required = false) CompanyProgram.CompanyProgramStatus status,
                                                                               @RequestParam(defaultValue = "false") boolean liveOnly,
                                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                                               Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, false);
            validatePageRequest(page, size);
            validateDateRange(startDate, endDate);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<CompanyProgramDto> companyPrograms = companyProgramService.getCompanyPrograms(
                    companyId,
                    search,
                    status,
                    liveOnly,
                    startDate,
                    endDate,
                    pageable
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyId", companyId);
            data.put("programs", companyPrograms.getContent());
            data.put("count", companyPrograms.getNumberOfElements());
            data.put("currentPage", companyPrograms.getNumber());
            data.put("pageSize", companyPrograms.getSize());
            data.put("totalPages", companyPrograms.getTotalPages());
            data.put("totalItems", companyPrograms.getTotalElements());
            data.put("hasNext", companyPrograms.hasNext());
            data.put("hasPrevious", companyPrograms.hasPrevious());
            data.put("search", search != null ? search.trim() : "");
            data.put("status", status != null ? status.name() : null);
            data.put("liveOnly", liveOnly);

            return ResponseEntity.ok(ApiResponse.success("Company programs retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company programs for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company programs: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/programs")
    @Operation(summary = "Create company program", description = "Create a new mentorship program runtime record for a company")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> createCompanyProgram(@PathVariable UUID companyId,
                                                                               @Valid @RequestBody CreateCompanyProgramRequest request,
                                                                               Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<CompanyProgramDto> response = companyProgramService.createCompanyProgram(
                    companyId,
                    request,
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating company program for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create company program: " + e.getMessage()));
        }
    }

    @GetMapping("/company-programs/{companyProgramId}")
    @Operation(summary = "Get company program", description = "Get a single company program by ID")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> getCompanyProgram(@PathVariable UUID companyProgramId,
                                                                            Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));

            authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), false);

            return companyProgramService.getCompanyProgramDto(companyProgramId)
                    .map(dto -> ResponseEntity.ok(ApiResponse.success("Company program retrieved successfully", dto)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Company program not found")));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company program: " + e.getMessage()));
        }
    }

    @PatchMapping("/company-programs/{companyProgramId}")
    @Operation(summary = "Update company program", description = "Update editable fields of a company mentorship program")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> updateCompanyProgram(@PathVariable UUID companyProgramId,
                                                                               @Valid @RequestBody UpdateCompanyProgramRequest request,
                                                                               Authentication authentication) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));

            authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);

            ApiResponse<CompanyProgramDto> response = companyProgramService.updateCompanyProgram(companyProgramId, request);
            return response.isSuccess()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company program: " + e.getMessage()));
        }
    }

    @PostMapping("/company-programs/{companyProgramId}/launch")
    @Operation(summary = "Launch company program", description = "Move a draft or paused company program to LIVE")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> launchCompanyProgram(@PathVariable UUID companyProgramId,
                                                                               Authentication authentication) {
        return handleStatusChange(companyProgramId, authentication, companyProgramService::launchProgram);
    }

    @PostMapping("/company-programs/{companyProgramId}/pause")
    @Operation(summary = "Pause company program", description = "Pause a live company program")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> pauseCompanyProgram(@PathVariable UUID companyProgramId,
                                                                              Authentication authentication) {
        return handleStatusChange(companyProgramId, authentication, companyProgramService::pauseProgram);
    }

    @PostMapping("/company-programs/{companyProgramId}/complete")
    @Operation(summary = "Complete company program", description = "Mark a company program as completed")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> completeCompanyProgram(@PathVariable UUID companyProgramId,
                                                                                 Authentication authentication) {
        return handleStatusChange(companyProgramId, authentication, companyProgramService::completeProgram);
    }

    @PostMapping("/company-programs/{companyProgramId}/cancel")
    @Operation(summary = "Cancel company program", description = "Cancel a company program and stop future execution")
    public ResponseEntity<ApiResponse<CompanyProgramDto>> cancelCompanyProgram(@PathVariable UUID companyProgramId,
                                                                               Authentication authentication) {
        return handleStatusChange(companyProgramId, authentication, companyProgramService::cancelProgram);
    }

    private ResponseEntity<ApiResponse<CompanyProgramDto>> handleStatusChange(UUID companyProgramId,
                                                                              Authentication authentication,
                                                                              java.util.function.Function<UUID, ApiResponse<CompanyProgramDto>> action) {
        try {
            CompanyProgram companyProgram = companyProgramService.getCompanyProgram(companyProgramId)
                    .orElseThrow(() -> new NoSuchElementException("Company program not found"));
            authorizeCompanyAccess(authentication, companyProgram.getCompany().getId(), true);
            ApiResponse<CompanyProgramDto> response = action.apply(companyProgramId);

            return response.isSuccess()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error changing status for company program {}: {}", companyProgramId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to change company program status: " + e.getMessage()));
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
