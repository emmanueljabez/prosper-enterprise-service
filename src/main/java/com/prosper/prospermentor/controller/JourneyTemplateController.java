package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.JourneyTemplateDto;
import com.prosper.prospermentor.dto.UpsertJourneyTemplateRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.JourneyTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Journey Templates", description = "Guided journey template catalog APIs")
@RequiredArgsConstructor
@Slf4j
public class JourneyTemplateController {

    private final JourneyTemplateService journeyTemplateService;

    @GetMapping("/journey-templates")
    @Operation(summary = "Get journey templates", description = "List active guided journey templates that can be attached to company programs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJourneyTemplates(Authentication authentication) {
        try {
            requireAuthenticatedUser(authentication);
            List<JourneyTemplateDto> templates = journeyTemplateService.getActiveTemplates();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("templates", templates);
            data.put("count", templates.size());
            return ResponseEntity.ok(ApiResponse.success("Journey templates retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting journey templates: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get journey templates: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/journey-templates")
    @Operation(summary = "Get all journey templates", description = "List all guided journey templates for admin authoring")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminJourneyTemplates(Authentication authentication) {
        try {
            requireTemplateAdmin(authentication);
            List<JourneyTemplateDto> templates = journeyTemplateService.getAllTemplates();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("templates", templates);
            data.put("count", templates.size());
            return ResponseEntity.ok(ApiResponse.success("Journey templates retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting admin journey templates: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get journey templates: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/journey-templates/{journeyTemplateId}")
    @Operation(summary = "Get journey template", description = "Get a single journey template with step and dependency details")
    public ResponseEntity<ApiResponse<JourneyTemplateDto>> getAdminJourneyTemplate(@PathVariable UUID journeyTemplateId,
                                                                                   Authentication authentication) {
        try {
            requireTemplateAdmin(authentication);
            return journeyTemplateService.getTemplateDto(journeyTemplateId)
                    .map(template -> ResponseEntity.ok(ApiResponse.success("Journey template retrieved successfully", template)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Journey template not found")));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting journey template {}: {}", journeyTemplateId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get journey template: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/journey-templates")
    @Operation(summary = "Create journey template", description = "Create a new guided journey template and its ordered step graph")
    public ResponseEntity<ApiResponse<JourneyTemplateDto>> createJourneyTemplate(@Valid @RequestBody UpsertJourneyTemplateRequest request,
                                                                                 Authentication authentication) {
        try {
            requireTemplateAdmin(authentication);
            JourneyTemplateDto template = journeyTemplateService.createTemplate(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Journey template created successfully", template));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating journey template: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create journey template: " + e.getMessage()));
        }
    }

    @PatchMapping("/admin/journey-templates/{journeyTemplateId}")
    @Operation(summary = "Update journey template", description = "Update a guided journey template, including steps and dependencies")
    public ResponseEntity<ApiResponse<JourneyTemplateDto>> updateJourneyTemplate(@PathVariable UUID journeyTemplateId,
                                                                                 @Valid @RequestBody UpsertJourneyTemplateRequest request,
                                                                                 Authentication authentication) {
        try {
            requireTemplateAdmin(authentication);
            JourneyTemplateDto template = journeyTemplateService.updateTemplate(journeyTemplateId, request);
            return ResponseEntity.ok(ApiResponse.success("Journey template updated successfully", template));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating journey template {}: {}", journeyTemplateId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update journey template: " + e.getMessage()));
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

    private SupabaseUserDetails requireTemplateAdmin(Authentication authentication) {
        SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
        if (!userDetails.isAdmin() && !userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }
        return userDetails;
    }
}
