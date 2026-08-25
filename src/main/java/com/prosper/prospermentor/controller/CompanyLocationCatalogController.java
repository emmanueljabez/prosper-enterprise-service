package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyChapterDto;
import com.prosper.prospermentor.dto.CompanyRegionDto;
import com.prosper.prospermentor.dto.CreateCompanyChapterRequest;
import com.prosper.prospermentor.dto.CreateCompanyRegionRequest;
import com.prosper.prospermentor.dto.UpdateCompanyChapterRequest;
import com.prosper.prospermentor.dto.UpdateCompanyRegionRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyLocationCatalogService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Company Location Catalog", description = "Region and chapter catalog APIs for corporate admin settings")
@RequiredArgsConstructor
@Slf4j
public class CompanyLocationCatalogController {

    private final CompanyLocationCatalogService locationCatalogService;
    private final ProfileService profileService;

    @GetMapping("/companies/{companyId}/regions")
    @Operation(summary = "Get company regions", description = "List configured regions for a company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyRegions(@PathVariable UUID companyId,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size,
                                                                              @RequestParam(required = false) String search,
                                                                              Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, false);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
            Page<CompanyRegionDto> regions = locationCatalogService.getRegions(companyId, search, pageable);
            return ResponseEntity.ok(ApiResponse.success("Company regions retrieved successfully", pageData(companyId, "regions", regions, search)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting regions for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company regions: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/regions")
    @Operation(summary = "Create company region", description = "Create a company-scoped delivery region")
    public ResponseEntity<ApiResponse<CompanyRegionDto>> createCompanyRegion(@PathVariable UUID companyId,
                                                                             @Valid @RequestBody CreateCompanyRegionRequest request,
                                                                             Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            CompanyRegionDto created = locationCatalogService.createRegion(companyId, request, userDetails.getUserIdAsUuid());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Region created successfully", created));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating region for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create region: " + e.getMessage()));
        }
    }

    @PutMapping("/companies/{companyId}/regions/{regionId}")
    @Operation(summary = "Update company region", description = "Update company region metadata")
    public ResponseEntity<ApiResponse<CompanyRegionDto>> updateCompanyRegion(@PathVariable UUID companyId,
                                                                             @PathVariable UUID regionId,
                                                                             @Valid @RequestBody UpdateCompanyRegionRequest request,
                                                                             Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            CompanyRegionDto updated = locationCatalogService.updateRegion(companyId, regionId, request);
            return ResponseEntity.ok(ApiResponse.success("Region updated successfully", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating region {} for company {}: {}", regionId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update region: " + e.getMessage()));
        }
    }

    @DeleteMapping("/companies/{companyId}/regions/{regionId}")
    @Operation(summary = "Delete company region", description = "Delete a company region")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCompanyRegion(@PathVariable UUID companyId,
                                                                                @PathVariable UUID regionId,
                                                                                Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            locationCatalogService.deleteRegion(companyId, regionId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("regionId", regionId);
            return ResponseEntity.ok(ApiResponse.success("Region deleted successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting region {} for company {}: {}", regionId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete region: " + e.getMessage()));
        }
    }

    @GetMapping("/companies/{companyId}/chapters")
    @Operation(summary = "Get company chapters", description = "List configured chapters for a company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyChapters(@PathVariable UUID companyId,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size,
                                                                               @RequestParam(required = false) UUID regionId,
                                                                               @RequestParam(required = false) String search,
                                                                               Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, false);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
            Page<CompanyChapterDto> chapters = locationCatalogService.getChapters(companyId, regionId, search, pageable);
            Map<String, Object> data = pageData(companyId, "chapters", chapters, search);
            data.put("regionId", regionId);
            return ResponseEntity.ok(ApiResponse.success("Company chapters retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting chapters for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company chapters: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/chapters")
    @Operation(summary = "Create company chapter", description = "Create a company-scoped chapter")
    public ResponseEntity<ApiResponse<CompanyChapterDto>> createCompanyChapter(@PathVariable UUID companyId,
                                                                               @Valid @RequestBody CreateCompanyChapterRequest request,
                                                                               Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            CompanyChapterDto created = locationCatalogService.createChapter(companyId, request, userDetails.getUserIdAsUuid());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Chapter created successfully", created));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating chapter for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create chapter: " + e.getMessage()));
        }
    }

    @PutMapping("/companies/{companyId}/chapters/{chapterId}")
    @Operation(summary = "Update company chapter", description = "Update company chapter metadata")
    public ResponseEntity<ApiResponse<CompanyChapterDto>> updateCompanyChapter(@PathVariable UUID companyId,
                                                                               @PathVariable UUID chapterId,
                                                                               @Valid @RequestBody UpdateCompanyChapterRequest request,
                                                                               Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            CompanyChapterDto updated = locationCatalogService.updateChapter(companyId, chapterId, request);
            return ResponseEntity.ok(ApiResponse.success("Chapter updated successfully", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating chapter {} for company {}: {}", chapterId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update chapter: " + e.getMessage()));
        }
    }

    @DeleteMapping("/companies/{companyId}/chapters/{chapterId}")
    @Operation(summary = "Delete company chapter", description = "Delete a company chapter")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCompanyChapter(@PathVariable UUID companyId,
                                                                                 @PathVariable UUID chapterId,
                                                                                 Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            locationCatalogService.deleteChapter(companyId, chapterId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chapterId", chapterId);
            return ResponseEntity.ok(ApiResponse.success("Chapter deleted successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting chapter {} for company {}: {}", chapterId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete chapter: " + e.getMessage()));
        }
    }

    private Map<String, Object> pageData(UUID companyId, String key, Page<?> page, String search) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", companyId);
        data.put(key, page.getContent());
        data.put("count", page.getNumberOfElements());
        data.put("currentPage", page.getNumber());
        data.put("pageSize", page.getSize());
        data.put("totalPages", page.getTotalPages());
        data.put("totalItems", page.getTotalElements());
        data.put("hasNext", page.hasNext());
        data.put("hasPrevious", page.hasPrevious());
        data.put("search", search != null ? search.trim() : "");
        return data;
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

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
