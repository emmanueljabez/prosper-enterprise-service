package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.EmployeeSessionAllocationService;
import com.prosper.prospermentor.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employee-session-allocations")
@RequiredArgsConstructor
@Slf4j
public class EmployeeSessionAllocationController {

    private final EmployeeSessionAllocationService employeeSessionAllocationService;
    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@PathVariable UUID companyId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) String search,
                                                                 Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            authorizeCompanyRequest(userDetails, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Employee session allocations retrieved successfully",
                    employeeSessionAllocationService.list(companyId, page, size, search)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list employee session allocations for company {}", companyId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve employee session allocations"));
        }
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lookup(@PathVariable UUID companyId,
                                                                   @RequestParam(name = "profileIds") List<UUID> profileIds,
                                                                   Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            authorizeCompanyRequest(userDetails, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Employee session allocation lookup completed successfully",
                    employeeSessionAllocationService.lookup(companyId, profileIds)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to lookup employee session allocations for company {}", companyId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to lookup employee session allocations"));
        }
    }

    @PostMapping("/{profileId}/allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocate(@PathVariable UUID companyId,
                                                                     @PathVariable UUID profileId,
                                                                     @RequestBody QuantityRequest request,
                                                                     Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            authorizeCompanyRequest(userDetails, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Employee sessions allocated successfully",
                    employeeSessionAllocationService.allocate(
                            request.getCompanySubscriptionId(),
                            companyId,
                            profileId,
                            request.getQuantity(),
                            userDetails.getUserIdAsUuid()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to allocate employee sessions for company {} profile {}", companyId, profileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to allocate employee sessions"));
        }
    }

    @PostMapping("/{profileId}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(@PathVariable UUID companyId,
                                                                     @PathVariable UUID profileId,
                                                                     @RequestBody QuantityRequest request,
                                                                     Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            authorizeCompanyRequest(userDetails, companyId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Employee sessions withdrawn successfully",
                    employeeSessionAllocationService.withdraw(
                            request.getCompanySubscriptionId(),
                            companyId,
                            profileId,
                            request.getQuantity(),
                            userDetails.getUserIdAsUuid()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to withdraw employee sessions for company {} profile {}", companyId, profileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to withdraw employee sessions"));
        }
    }

    private SupabaseUserDetails requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        return userDetails;
    }

    private void authorizeCompanyRequest(SupabaseUserDetails userDetails, UUID companyId) {
        if (userDetails.isAdmin()) {
            return;
        }

        if (!userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID userCompanyId = profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);
        if (userCompanyId == null || !userCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to manage this company");
        }
    }

    public static class QuantityRequest {
        private UUID companySubscriptionId;
        private int quantity;

        public UUID getCompanySubscriptionId() {
            return companySubscriptionId;
        }

        public void setCompanySubscriptionId(UUID companySubscriptionId) {
            this.companySubscriptionId = companySubscriptionId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
