package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyMentorEnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/company-mentor-approvals")
@RequiredArgsConstructor
@Slf4j
public class AdminCompanyMentorApprovalController {

    private final CompanyMentorEnrollmentService companyMentorEnrollmentService;

    @PostMapping("/{membershipId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> approve(@PathVariable UUID membershipId,
                                                                                Authentication authentication) {
        try {
            UUID approvedBy = requireAdminUserId(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Public visibility approved",
                    companyMentorEnrollmentService.approvePublicVisibility(membershipId, approvedBy)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error approving company mentor membership {}: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to approve public visibility"));
        }
    }

    @PostMapping("/{membershipId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyMentorDtos.PoolMemberDto>> reject(@PathVariable UUID membershipId,
                                                                               Authentication authentication) {
        try {
            UUID rejectedBy = requireAdminUserId(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Public visibility rejected",
                    companyMentorEnrollmentService.rejectPublicVisibility(membershipId, rejectedBy)
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error rejecting company mentor membership {}: {}", membershipId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reject public visibility"));
        }
    }

    private UUID requireAdminUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        if (!userDetails.isAdmin()) {
            throw new SecurityException("Admin access is required");
        }
        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            throw new SecurityException("Invalid authenticated user");
        }
        return userId;
    }
}
