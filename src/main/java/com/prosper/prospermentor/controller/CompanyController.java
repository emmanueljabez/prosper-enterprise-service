package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyEmployeeWhitelist;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing companies
 */
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Company management APIs")
@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final ProfileService profileService;

    public CompanyController(CompanyService companyService, ProfileService profileService) {
        this.companyService = companyService;
        this.profileService = profileService;
    }

    /**
     * Get all companies
     */
    @GetMapping
    @Operation(summary = "Get all companies", description = "Get list of all companies")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllCompanies(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        log.info("Getting all companies (activeOnly: {})", activeOnly);

        try {
            List<Company> companies = activeOnly ?
                    companyService.getActiveCompanies() :
                    companyService.getAllCompanies();

            Map<String, Object> data = new HashMap<>();
            data.put("companies", companies);
            data.put("count", companies.size());

            return ResponseEntity.ok(ApiResponse.success("Companies retrieved successfully", data));

        } catch (Exception e) {
            log.error("Error getting companies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get companies: " + e.getMessage()));
        }
    }

    /**
     * Get companies with pagination and optional search.
     */
    @GetMapping("/paginated")
    @Operation(summary = "Get companies (paginated)", description = "Get companies with pagination, optional search, and active-only filtering")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompaniesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "") String search) {
        log.info("Getting paginated companies (page: {}, size: {}, activeOnly: {}, search: {})",
                page, size, activeOnly, search);

        try {
            if (page < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("page must be greater than or equal to 0"));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("size must be between 1 and 100"));
            }

            Page<Company> companies = companyService.getCompaniesPaginated(activeOnly, search, page, size);

            Map<String, Object> data = new HashMap<>();
            data.put("companies", companies.getContent());
            data.put("count", companies.getNumberOfElements());
            data.put("currentPage", companies.getNumber());
            data.put("pageSize", companies.getSize());
            data.put("totalPages", companies.getTotalPages());
            data.put("totalItems", companies.getTotalElements());
            data.put("hasNext", companies.hasNext());
            data.put("hasPrevious", companies.hasPrevious());
            data.put("search", search);
            data.put("activeOnly", activeOnly);

            return ResponseEntity.ok(ApiResponse.success("Companies retrieved successfully", data));

        } catch (Exception e) {
            log.error("Error getting paginated companies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get companies: " + e.getMessage()));
        }
    }

    /**
     * Get company by ID
     */
    @GetMapping("/{companyId}")
    @Operation(summary = "Get company by ID", description = "Get details of a specific company")
    public ResponseEntity<ApiResponse<Company>> getCompanyById(@PathVariable UUID companyId,
                                                               Authentication authentication) {
        log.info("Getting company: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, false);
            return companyService.getCompanyById(companyId)
                    .map(company -> ResponseEntity.ok(ApiResponse.success("Company retrieved successfully", company)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Company not found")));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company: " + e.getMessage()));
        }
    }

    @GetMapping("/{companyId}/onboarding")
    @Operation(summary = "Get company onboarding status", description = "Get required setup status before company activation.")
    public ResponseEntity<ApiResponse<CompanyOnboardingStatusDto>> getCompanyOnboardingStatus(
            @PathVariable UUID companyId,
            Authentication authentication) {
        log.info("Getting company onboarding status: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<CompanyOnboardingStatusDto> response = companyService.getCompanyOnboardingStatus(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting company onboarding status {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company onboarding status: " + e.getMessage()));
        }
    }

    @PutMapping("/{companyId}/onboarding")
    @Operation(summary = "Update company onboarding", description = "Save required company setup before company activation.")
    public ResponseEntity<ApiResponse<CompanyOnboardingStatusDto>> updateCompanyOnboarding(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyOnboardingRequest request,
            Authentication authentication) {
        log.info("Updating company onboarding: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<CompanyOnboardingStatusDto> response = companyService.updateCompanyOnboarding(companyId, request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            HttpStatus status = "Company not found".equals(response.getMessage())
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company onboarding {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company onboarding: " + e.getMessage()));
        }
    }

    @GetMapping("/{companyId}/recommended-programs")
    @Operation(summary = "Get company recommended programs", description = "Get programs recommended by a company for its employees.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyRecommendedPrograms(@PathVariable UUID companyId,
                                                                                          Authentication authentication) {
        log.info("Getting recommended programs for company: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, false);
            ApiResponse<Map<String, Object>> response = companyService.getRecommendedPrograms(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting recommended programs for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get recommended programs: " + e.getMessage()));
        }
    }

    @PutMapping("/{companyId}/recommended-programs")
    @Operation(summary = "Update company recommended programs", description = "Set the programs a company wants its employees to pursue.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCompanyRecommendedPrograms(
            @PathVariable UUID companyId,
            @RequestBody(required = false) UpdateCompanyRecommendedProgramsRequest request,
            Authentication authentication) {
        log.info("Updating recommended programs for company: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, true);

            ApiResponse<Map<String, Object>> response = companyService.updateRecommendedPrograms(
                    companyId,
                    request != null ? request.getProgramIds() : List.of()
            );

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            HttpStatus status = "Company not found".equals(response.getMessage())
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating recommended programs for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update recommended programs: " + e.getMessage()));
        }
    }

    @GetMapping("/{companyId}/sessions")
    @Operation(summary = "Get company employee sessions", description = "Get mentorship sessions for employees linked to a specific company.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanySessions(@PathVariable UUID companyId,
                                                                               @RequestParam(required = false) List<String> statuses,
                                                                               @RequestParam(required = false) List<String> departments,
                                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
                                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
                                                                               @RequestParam(required = false) String search,
                                                                               @RequestParam(required = false) Integer page,
                                                                               @RequestParam(required = false) Integer size,
                                                                               Authentication authentication) {
        log.info("Getting employee sessions for company: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<Map<String, Object>> response = companyService.getCompanySessions(
                    companyId,
                    statuses,
                    departments,
                    startDate,
                    endDate,
                    search,
                    page,
                    size
            );

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting sessions for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company sessions: " + e.getMessage()));
        }
    }

    /**
     * Get company by email
     */
    @GetMapping("/email/{emailAddress}")
    @Operation(summary = "Get company by email", description = "Get company by email address")
    public ResponseEntity<ApiResponse<Company>> getCompanyByEmail(@PathVariable String emailAddress) {
        log.info("Getting company by email: {}", emailAddress);

        try {
            return companyService.getCompanyByEmail(emailAddress)
                    .map(company -> ResponseEntity.ok(ApiResponse.success("Company retrieved successfully", company)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Company not found")));

        } catch (Exception e) {
            log.error("Error getting company by email: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company: " + e.getMessage()));
        }
    }

    /**
     * Search companies by name
     */
    @GetMapping("/search")
    @Operation(summary = "Search companies", description = "Search companies by name (case insensitive)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchCompanies(
            @RequestParam String name) {
        log.info("Searching companies by name: {}", name);

        try {
            List<Company> companies = companyService.searchCompaniesByName(name);

            Map<String, Object> data = new HashMap<>();
            data.put("companies", companies);
            data.put("count", companies.size());

            return ResponseEntity.ok(ApiResponse.success("Search completed successfully", data));

        } catch (Exception e) {
            log.error("Error searching companies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to search companies: " + e.getMessage()));
        }
    }

    /**
     * Create a new company
     */
    @PostMapping
    @Operation(summary = "Create company", description = "Create a new company")
    public ResponseEntity<ApiResponse<Company>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());

        try {
            ApiResponse<Company> response = companyService.createCompany(request);

            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

        } catch (Exception e) {
            log.error("Error creating company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create company: " + e.getMessage()));
        }
    }

    /**
     * Update an existing company
     */
    @PutMapping("/{companyId}")
    @Operation(summary = "Update company", description = "Update an existing company")
    public ResponseEntity<ApiResponse<Company>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request,
            Authentication authentication) {
        log.info("Updating company: {}", companyId);

        try {
            authorizeCompanyAccess(authentication, companyId, true);
            ApiResponse<Company> response = companyService.updateCompany(companyId, request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company: " + e.getMessage()));
        }
    }

    /**
     * Delete a company (soft delete)
     */
    @DeleteMapping("/{companyId}")
    @Operation(summary = "Delete company", description = "Deactivate a company (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(@PathVariable UUID companyId) {
        log.info("Deleting company: {}", companyId);

        try {
            ApiResponse<Void> response = companyService.deleteCompany(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error deleting company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete company: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete a company
     */
    @DeleteMapping("/{companyId}/permanent")
    @Operation(summary = "Permanently delete company", description = "Permanently delete a company from the database")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteCompany(@PathVariable UUID companyId) {
        log.info("Permanently deleting company: {}", companyId);

        try {
            ApiResponse<Void> response = companyService.permanentlyDeleteCompany(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error permanently deleting company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to permanently delete company: " + e.getMessage()));
        }
    }

    /**
     * Toggle company active status
     */
    @PatchMapping("/{companyId}/toggle-status")
    @Operation(summary = "Toggle company status", description = "Toggle company active/inactive status")
    public ResponseEntity<ApiResponse<Company>> toggleCompanyStatus(@PathVariable UUID companyId) {
        log.info("Toggling status for company: {}", companyId);

        try {
            ApiResponse<Company> response = companyService.toggleCompanyActiveStatus(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error toggling company status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to toggle company status: " + e.getMessage()));
        }
    }

    /**
     * Link a profile to a company
     */
    @PostMapping("/{companyId}/profiles")
    @Operation(summary = "Link profile to company", description = "Associate a profile with a company")
    public ResponseEntity<ApiResponse<Profile>> linkProfileToCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody LinkProfileToCompanyRequest request) {
        log.info("Linking profile {} to company {}", request.getProfileId(), companyId);

        try {
            ApiResponse<Profile> response = companyService.linkProfileToCompany(companyId, request.getProfileId());

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error linking profile to company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to link profile to company: " + e.getMessage()));
        }
    }

    /**
     * Unlink a profile from a company
     */
    @DeleteMapping("/{companyId}/profiles/{profileId}")
    @Operation(summary = "Unlink profile from company", description = "Remove association between a profile and company")
    public ResponseEntity<ApiResponse<Profile>> unlinkProfileFromCompany(
            @PathVariable UUID companyId,
            @PathVariable UUID profileId) {
        log.info("Unlinking profile {} from company {}", profileId, companyId);

        try {
            ApiResponse<Profile> response = companyService.unlinkProfileFromCompany(profileId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error unlinking profile from company: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to unlink profile from company: " + e.getMessage()));
        }
    }

    /**
     * Get all profiles linked to a company (paginated with search)
     */
    @GetMapping("/{companyId}/profiles")
    @Operation(summary = "Get company profiles", description = "Search company profiles by name or email with pagination")
    public ResponseEntity<ApiResponse<Page<Profile>>> getCompanyProfiles(
            @PathVariable String companyId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting profiles for company: {} with search: {}, page: {}, size: {}",
                companyId, search, page, size);

        try {
            ApiResponse<Page<Profile>> response =
                    companyService.searchCompanyProfiles(companyId, search, page, size);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting company profiles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company profiles: " + e.getMessage()));
        }
    }

    /**
     * Complete company registration using token
     */
    @PostMapping("/register")
    @Operation(summary = "Complete registration", description = "Complete company registration using token from email")
    public ResponseEntity<ApiResponse<Company>> completeRegistration(@RequestParam String token) {
        log.info("Processing registration completion");

        try {
            ApiResponse<Company> response = companyService.completeRegistration(token);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error completing registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete registration: " + e.getMessage()));
        }
    }

    /**
     * Get company details by registration token
     */
    @GetMapping("/register")
    @Operation(summary = "Get company by token", description = "Get company details using registration token")
    public ResponseEntity<ApiResponse<Company>> getCompanyByToken(@RequestParam String token) {
        log.info("Getting company by registration token");

        try {
            ApiResponse<Company> response = companyService.getCompanyByToken(token);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting company by token: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company: " + e.getMessage()));
        }
    }

    /**
     * Resend welcome email to company
     */
    @PostMapping("/{companyId}/resend-welcome")
    @Operation(summary = "Resend welcome email", description = "Resend welcome email with new registration link")
    public ResponseEntity<ApiResponse<Void>> resendWelcomeEmail(@PathVariable UUID companyId) {
        log.info("Resending welcome email for company: {}", companyId);

        try {
            ApiResponse<Void> response = companyService.resendWelcomeEmail(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error resending welcome email: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resend welcome email: " + e.getMessage()));
        }
    }

    private void authorizeCompanyAccess(Authentication authentication, UUID companyId, boolean companyAdminRequired) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }

        if (userDetails.isAdmin()) {
            return;
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
    }

    // ==================== Employee Whitelist Endpoints ====================

    /**
     * Add a single employee to whitelist
     */
    @PostMapping("/{companyId}/whitelist")
    @Operation(summary = "Add employee to whitelist", description = "Add a single employee email to company whitelist")
    public ResponseEntity<ApiResponse<CompanyEmployeeWhitelistDto>> addEmployeeToWhitelist(
            @PathVariable String companyId,
            @Valid @RequestBody AddEmployeeToWhitelistRequest request) {
        log.info("Adding employee to whitelist for company: {}", companyId);

        try {
            // TODO: Get current user from authentication context
            String addedBy = "system"; // Replace with actual user info

            ApiResponse<CompanyEmployeeWhitelistDto> response = companyService.addEmployeeToWhitelist(companyId, request, addedBy);

            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error adding employee to whitelist: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add employee to whitelist: " + e.getMessage()));
        }
    }

    /**
     * Download Excel template for bulk whitelist upload
     */
    @GetMapping("/whitelist/template")
    @Operation(summary = "Download whitelist template", description = "Download Excel template for bulk employee whitelist upload")
    public ResponseEntity<byte[]> downloadWhitelistTemplate() {
        log.info("Downloading whitelist upload template");

        try {
            Workbook workbook = companyService.generateWhitelistTemplate();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] excelBytes = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "employee-whitelist-template.xlsx");
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            log.error("Error generating template: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Bulk upload employees to whitelist from Excel file
     */
    @PostMapping(value = "/{companyId}/whitelist/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk upload whitelist", description = "Upload Excel file with employee emails to whitelist")
    public ResponseEntity<ApiResponse<BulkWhitelistUploadResponse>> bulkUploadWhitelist(
            @PathVariable UUID companyId,
            @RequestParam("file") MultipartFile file) {
        log.info("Processing bulk whitelist upload for company: {}", companyId);

        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("File is empty"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.endsWith(".xlsx")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Invalid file format. Please upload an Excel (.xlsx) file"));
            }

            // TODO: Get current user from authentication context
            String addedBy = "system"; // Replace with actual user info

            ApiResponse<BulkWhitelistUploadResponse> response = companyService.bulkUploadWhitelist(companyId, file, addedBy);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error processing bulk upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process bulk upload: " + e.getMessage()));
        }
    }

    /**
     * Get a specific whitelist entry by ID
     */
    @GetMapping("/{companyId}/whitelist/{whitelistId}")
    @Operation(summary = "Get whitelist entry", description = "Get a specific whitelist entry by ID")
    public ResponseEntity<ApiResponse<CompanyEmployeeWhitelistDto>> getWhitelistEntry(
            @PathVariable UUID companyId,
            @PathVariable UUID whitelistId) {
        log.info("Getting whitelist entry {} for company: {}", whitelistId, companyId);

        try {
            ApiResponse<CompanyEmployeeWhitelistDto> response = companyService.getWhitelistEntryById(whitelistId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting whitelist entry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get whitelist entry: " + e.getMessage()));
        }
    }

    /**
     * Check if email is whitelisted
     */
    @GetMapping("/{companyId}/whitelist/check")
    @Operation(summary = "Check if email is whitelisted", description = "Check if an email is in company whitelist")
    public ResponseEntity<ApiResponse<Boolean>> checkEmailWhitelisted(
            @PathVariable UUID companyId,
            @RequestParam String email) {
        log.info("Checking if email {} is whitelisted for company {}", email, companyId);

        try {
            ApiResponse<Boolean> response = companyService.checkEmailWhitelisted(companyId, email);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error checking whitelist: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check whitelist: " + e.getMessage()));
        }
    }

    /**
     * Remove employee from whitelist
     */
    @DeleteMapping("/{companyId}/whitelist/{whitelistId}")
    @Operation(summary = "Remove from whitelist", description = "Remove employee from company whitelist (soft delete)")
    public ResponseEntity<ApiResponse<Void>> removeFromWhitelist(
            @PathVariable String whitelistId) {
        log.info("Removing employee from whitelist: {}", whitelistId);

        try {
            ApiResponse<Void> response = companyService.removeFromWhitelist(whitelistId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error removing from whitelist: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove from whitelist: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete whitelist entry
     */
    @DeleteMapping("/{companyId}/whitelist/{whitelistId}/permanent")
    @Operation(summary = "Permanently delete whitelist entry", description = "Permanently delete employee from whitelist")
    public ResponseEntity<ApiResponse<Void>> deleteWhitelistEntry(
            @PathVariable UUID companyId,
            @PathVariable UUID whitelistId) {
        log.info("Permanently deleting whitelist entry: {}", whitelistId);

        try {
            ApiResponse<Void> response = companyService.deleteWhitelistEntry(whitelistId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error deleting whitelist entry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete whitelist entry: " + e.getMessage()));
        }
    }

    /**
     * Get whitelist statistics
     */
    @GetMapping("/{companyId}/whitelist/stats")
    @Operation(summary = "Get whitelist statistics", description = "Get statistics about company whitelist")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWhitelistStats(@PathVariable UUID companyId) {
        log.info("Getting whitelist statistics for company: {}", companyId);

        try {
            ApiResponse<Map<String, Object>> response = companyService.getWhitelistStats(companyId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting whitelist statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get whitelist statistics: " + e.getMessage()));
        }
    }

    /**
     * Verify invitation token
     */
    @GetMapping("/whitelist/verify-invitation")
    @Operation(summary = "Verify invitation token", description = "Verify employee invitation token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyInvitation(@RequestParam String token) {
        log.info("Verifying invitation token");

        try {
            ApiResponse<Map<String, Object>> response = companyService.verifyInvitationToken(token);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error verifying invitation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to verify invitation: " + e.getMessage()));
        }
    }

    /**
     * Complete invitation signup
     */
    @PostMapping("/whitelist/complete-signup")
    @Operation(summary = "Complete invitation signup", description = "Complete employee signup after accepting invitation")
    public ResponseEntity<ApiResponse<Profile>> completeInvitationSignup(
            @RequestParam String token,
            @RequestParam UUID profileId) {
        log.info("Completing invitation signup for profile: {}", profileId);

        try {
            ApiResponse<Profile> response = companyService.completeInvitationSignup(token, profileId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error completing invitation signup: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete signup: " + e.getMessage()));
        }
    }

    /**
     * Resend invitation email to employee
     */
    @GetMapping("/{companyId}/whitelist/{whitelistId}/resend-invitation")
    @Operation(summary = "Resend invitation email", description = "Resend invitation email to whitelisted employee with new token")
    public ResponseEntity<ApiResponse<CompanyEmployeeWhitelistDto>> resendInvitationEmail(
            @PathVariable String companyId,
            @PathVariable String whitelistId) {
        log.info("Resending invitation email for whitelist entry: {} in company: {}", whitelistId, companyId);

        try {
            ApiResponse<CompanyEmployeeWhitelistDto> response = companyService.resendInvitationEmail(whitelistId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error resending invitation email: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resend invitation: " + e.getMessage()));
        }
    }

    /**
     * Pull/search whitelisted employees by name or email (paginated)
     */
    @GetMapping("/{companyId}/whitelist")
    @Operation(summary = "Pull whitelisted employees", description = "Search whitelisted employees by name or email with pagination")
    public ResponseEntity<ApiResponse<Page<CompanyEmployeeWhitelist>>> pullWhitelistedEmployees(
            @PathVariable String companyId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Pulling whitelisted employees for company: {} with search: {}, page: {}, size: {}",
                companyId, search, page, size);

        try {
            ApiResponse<Page<CompanyEmployeeWhitelist>> response =
                    companyService.searchWhitelistedEmployees(companyId, search, page, size);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error pulling whitelisted employees: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to pull whitelisted employees: " + e.getMessage()));
        }
    }
}
