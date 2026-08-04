package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.AddCompanyDepartmentMembersRequest;
import com.prosper.prospermentor.dto.CompanyDepartmentDto;
import com.prosper.prospermentor.dto.CompanyDepartmentMemberAssignmentResultDto;
import com.prosper.prospermentor.dto.CompanyDepartmentMemberDto;
import com.prosper.prospermentor.dto.CreateCompanyDepartmentRequest;
import com.prosper.prospermentor.dto.UpdateCompanyDepartmentRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyDepartmentService;
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
@Tag(name = "Company Departments", description = "Department management APIs for corporate admin settings")
@RequiredArgsConstructor
@Slf4j
public class CompanyDepartmentController {

    private final CompanyDepartmentService companyDepartmentService;
    private final ProfileService profileService;

    @GetMapping("/companies/{companyId}/departments")
    @Operation(summary = "Get company departments", description = "List departments for a company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyDepartments(@PathVariable UUID companyId,
                                                                                  @RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "20") int size,
                                                                                  @RequestParam(required = false) String search,
                                                                                  Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, false);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<CompanyDepartmentDto> departments = companyDepartmentService.getDepartments(companyId, search, pageable);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyId", companyId);
            data.put("departments", departments.getContent());
            data.put("count", departments.getNumberOfElements());
            data.put("currentPage", departments.getNumber());
            data.put("pageSize", departments.getSize());
            data.put("totalPages", departments.getTotalPages());
            data.put("totalItems", departments.getTotalElements());
            data.put("hasNext", departments.hasNext());
            data.put("hasPrevious", departments.hasPrevious());
            data.put("search", search != null ? search.trim() : "");

            return ResponseEntity.ok(ApiResponse.success("Company departments retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting departments for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get company departments: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/departments")
    @Operation(summary = "Create company department", description = "Create a new department for a company")
    public ResponseEntity<ApiResponse<CompanyDepartmentDto>> createCompanyDepartment(@PathVariable UUID companyId,
                                                                                     @Valid @RequestBody CreateCompanyDepartmentRequest request,
                                                                                     Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            CompanyDepartmentDto created = companyDepartmentService.createDepartment(
                    companyId,
                    request,
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Department created successfully", created));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating department for company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create department: " + e.getMessage()));
        }
    }

    @PutMapping("/companies/{companyId}/departments/{departmentId}")
    @Operation(summary = "Update company department", description = "Update department metadata")
    public ResponseEntity<ApiResponse<CompanyDepartmentDto>> updateCompanyDepartment(@PathVariable UUID companyId,
                                                                                     @PathVariable UUID departmentId,
                                                                                     @Valid @RequestBody UpdateCompanyDepartmentRequest request,
                                                                                     Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            CompanyDepartmentDto updated = companyDepartmentService.updateDepartment(companyId, departmentId, request);
            return ResponseEntity.ok(ApiResponse.success("Department updated successfully", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating department {} for company {}: {}", departmentId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update department: " + e.getMessage()));
        }
    }

    @DeleteMapping("/companies/{companyId}/departments/{departmentId}")
    @Operation(summary = "Delete company department", description = "Delete department and unassign employees")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCompanyDepartment(@PathVariable UUID companyId,
                                                                                    @PathVariable UUID departmentId,
                                                                                    Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            companyDepartmentService.deleteDepartment(companyId, departmentId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("departmentId", departmentId);
            return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting department {} for company {}: {}", departmentId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete department: " + e.getMessage()));
        }
    }

    @GetMapping("/companies/{companyId}/departments/{departmentId}/members")
    @Operation(summary = "Get department members", description = "List employees assigned to a department")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepartmentMembers(@PathVariable UUID companyId,
                                                                                 @PathVariable UUID departmentId,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "20") int size,
                                                                                 @RequestParam(required = false) String search,
                                                                                 Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, false);
            validatePageRequest(page, size);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "joinedAt"));
            Page<CompanyDepartmentMemberDto> members = companyDepartmentService.getDepartmentMembers(
                    companyId,
                    departmentId,
                    search,
                    pageable
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("companyId", companyId);
            data.put("departmentId", departmentId);
            data.put("members", members.getContent());
            data.put("count", members.getNumberOfElements());
            data.put("currentPage", members.getNumber());
            data.put("pageSize", members.getSize());
            data.put("totalPages", members.getTotalPages());
            data.put("totalItems", members.getTotalElements());
            data.put("hasNext", members.hasNext());
            data.put("hasPrevious", members.hasPrevious());
            data.put("search", search != null ? search.trim() : "");

            return ResponseEntity.ok(ApiResponse.success("Department members retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting members for department {} in company {}: {}", departmentId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get department members: " + e.getMessage()));
        }
    }

    @PostMapping("/companies/{companyId}/departments/{departmentId}/members")
    @Operation(summary = "Add department members", description = "Assign one or more employees to a department")
    public ResponseEntity<ApiResponse<CompanyDepartmentMemberAssignmentResultDto>> addDepartmentMembers(
            @PathVariable UUID companyId,
            @PathVariable UUID departmentId,
            @Valid @RequestBody AddCompanyDepartmentMembersRequest request,
            Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = authorizeCompanyAccess(authentication, companyId, true);
            CompanyDepartmentMemberAssignmentResultDto result = companyDepartmentService.addMembers(
                    companyId,
                    departmentId,
                    request,
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Employees assigned to department successfully", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error assigning members to department {} for company {}: {}", departmentId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to assign employees to department: " + e.getMessage()));
        }
    }

    @DeleteMapping("/companies/{companyId}/departments/{departmentId}/members/{profileId}")
    @Operation(summary = "Remove department member", description = "Remove an employee from a department")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeDepartmentMember(@PathVariable UUID companyId,
                                                                                   @PathVariable UUID departmentId,
                                                                                   @PathVariable UUID profileId,
                                                                                   Authentication authentication) {
        try {
            authorizeCompanyAccess(authentication, companyId, true);
            companyDepartmentService.removeMember(companyId, departmentId, profileId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("departmentId", departmentId);
            data.put("profileId", profileId);
            return ResponseEntity.ok(ApiResponse.success("Employee removed from department successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing member {} from department {} for company {}: {}",
                    profileId, departmentId, companyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove employee from department: " + e.getMessage()));
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

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
