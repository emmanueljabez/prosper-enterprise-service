package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanySubscriptionService;
import com.prosper.prospermentor.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company-subscriptions")
@Tag(name = "Company Subscriptions", description = "Corporate/company-sponsored subscription APIs")
@Slf4j
public class CompanySubscriptionController {

    private final CompanySubscriptionService companySubscriptionService;
    private final ProfileService profileService;

    public CompanySubscriptionController(CompanySubscriptionService companySubscriptionService,
                                         ProfileService profileService) {
        this.companySubscriptionService = companySubscriptionService;
        this.profileService = profileService;
    }

    @PostMapping
    @Operation(summary = "Create company subscription", description = "Create a company subscription purchase and return the invoice payment URL.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCompanySubscription(@RequestBody CreateCompanySubscriptionRequest request,
                                                                                      Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companyId = requireUuid(request.getCompanyId(), "companyId");
            authorizeCompanyRequest(userDetails, companyId);

            Map<String, Object> data = companySubscriptionService.createCompanySubscription(
                    companyId,
                    requireUuid(request.getPlanId(), "planId"),
                    request.getRequestedSessionCount(),
                    BillingInterval.fromString(request.getBillingInterval()),
                    userDetails.getUserIdAsUuid(),
                    request.getRedirectSuccessUrl(),
                    request.getRedirectCancelUrl()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Company subscription invoice created successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create company subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create company subscription"));
        }
    }

    @GetMapping("/company/{companyId}")
    @Operation(summary = "Get company subscriptions", description = "List subscriptions owned by a company.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCompanySubscriptions(@PathVariable String companyId,
                                                                                          Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companyUuid = requireUuid(companyId, "companyId");
            authorizeCompanyRequest(userDetails, companyUuid);

            List<Map<String, Object>> data = companySubscriptionService.getCompanySubscriptions(companyUuid);
            return ResponseEntity.ok(ApiResponse.success("Company subscriptions retrieved successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch company subscriptions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company subscriptions"));
        }
    }

    @GetMapping("/company/{companyId}/billing-dashboard")
    @Operation(summary = "Get company billing dashboard", description = "Get wallet metrics, trends, and recent billing transactions.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyBillingDashboard(@PathVariable String companyId,
                                                                                       @RequestParam(defaultValue = "0") Integer page,
                                                                                       @RequestParam(defaultValue = "10") Integer size,
                                                                                       Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companyUuid = requireUuid(companyId, "companyId");
            authorizeCompanyRequest(userDetails, companyUuid);

            if (page != null && page < 0) {
                throw new IllegalArgumentException("page must be greater than or equal to 0");
            }

            if (size != null && (size < 1 || size > 50)) {
                throw new IllegalArgumentException("size must be between 1 and 50");
            }

            Map<String, Object> data = companySubscriptionService.getCompanyBillingDashboard(companyUuid, page, size);
            return ResponseEntity.ok(ApiResponse.success("Company billing dashboard retrieved successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch company billing dashboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company billing dashboard"));
        }
    }

    @GetMapping("/{companySubscriptionId}")
    @Operation(summary = "Get company subscription", description = "Get a single company subscription with seat summary.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanySubscription(@PathVariable String companySubscriptionId,
                                                                                   Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            Map<String, Object> data = companySubscriptionService.getCompanySubscriptionDetails(
                    requireUuid(companySubscriptionId, "companySubscriptionId")
            );
            authorizeCompanyRequest(userDetails, extractCompanyId(data));
            return ResponseEntity.ok(ApiResponse.success("Company subscription retrieved successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch company subscription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company subscription"));
        }
    }

    @PostMapping("/{companySubscriptionId}/members")
    @Operation(summary = "Assign seat", description = "Assign a company subscription seat to a linked employee or mentee.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignSeat(@PathVariable String companySubscriptionId,
                                                                       @RequestBody AssignSeatRequest request,
                                                                       Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companySubscriptionUuid = requireUuid(companySubscriptionId, "companySubscriptionId");
            Map<String, Object> subscriptionData = companySubscriptionService.getCompanySubscriptionDetails(companySubscriptionUuid);
            authorizeCompanyRequest(userDetails, extractCompanyId(subscriptionData));

            Map<String, Object> data = companySubscriptionService.assignMember(
                    companySubscriptionUuid,
                    requireUuid(request.getProfileId(), "profileId"),
                    userDetails.getUserIdAsUuid()
            );
            return ResponseEntity.ok(ApiResponse.success("Seat assigned successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to assign seat", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to assign seat"));
        }
    }

    @DeleteMapping("/{companySubscriptionId}/members/{profileId}")
    @Operation(summary = "Revoke seat", description = "Revoke a seat assignment from a company subscription.")
    public ResponseEntity<ApiResponse<Void>> revokeSeat(@PathVariable String companySubscriptionId,
                                                        @PathVariable String profileId,
                                                        Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companySubscriptionUuid = requireUuid(companySubscriptionId, "companySubscriptionId");
            Map<String, Object> subscriptionData = companySubscriptionService.getCompanySubscriptionDetails(companySubscriptionUuid);
            authorizeCompanyRequest(userDetails, extractCompanyId(subscriptionData));

            companySubscriptionService.revokeMember(
                    companySubscriptionUuid,
                    requireUuid(profileId, "profileId")
            );
            return ResponseEntity.ok(ApiResponse.success("Seat revoked successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to revoke seat", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to revoke seat"));
        }
    }

    @GetMapping("/{companySubscriptionId}/members")
    @Operation(summary = "List seats", description = "List members assigned to a company subscription.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMembers(@PathVariable String companySubscriptionId,
                                                                             Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companySubscriptionUuid = requireUuid(companySubscriptionId, "companySubscriptionId");
            Map<String, Object> subscriptionData = companySubscriptionService.getCompanySubscriptionDetails(companySubscriptionUuid);
            authorizeCompanyRequest(userDetails, extractCompanyId(subscriptionData));

            List<Map<String, Object>> data = companySubscriptionService.getMembers(companySubscriptionUuid);
            return ResponseEntity.ok(ApiResponse.success("Company subscription members retrieved successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch company subscription members", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company subscription members"));
        }
    }

    @PostMapping("/{companySubscriptionId}/renew")
    @Operation(summary = "Renew company subscription", description = "Create an invoice-first renewal for an existing company subscription.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renew(@PathVariable String companySubscriptionId,
                                                                  @RequestBody RenewCompanySubscriptionRequest request,
                                                                  Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companySubscriptionUuid = requireUuid(companySubscriptionId, "companySubscriptionId");
            Map<String, Object> subscriptionData = companySubscriptionService.getCompanySubscriptionDetails(companySubscriptionUuid);
            authorizeCompanyRequest(userDetails, extractCompanyId(subscriptionData));

            Map<String, Object> data = companySubscriptionService.createRenewalInvoice(
                    companySubscriptionUuid,
                    userDetails.getUserIdAsUuid(),
                    BillingInterval.fromString(request.getBillingInterval()),
                    request.getRedirectSuccessUrl(),
                    request.getRedirectCancelUrl()
            );
            return ResponseEntity.ok(ApiResponse.success("Company subscription renewal invoice created successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create renewal invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create renewal invoice"));
        }
    }

    @PatchMapping("/{companySubscriptionId}/auto-renew")
    @Operation(summary = "Update company auto-refill preference", description = "Enable or disable auto-refill preference for a company subscription.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAutoRenew(@PathVariable String companySubscriptionId,
                                                                             @RequestBody UpdateAutoRenewRequest request,
                                                                             Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            UUID companySubscriptionUuid = requireUuid(companySubscriptionId, "companySubscriptionId");
            Map<String, Object> subscriptionData = companySubscriptionService.getCompanySubscriptionDetails(companySubscriptionUuid);
            authorizeCompanyRequest(userDetails, extractCompanyId(subscriptionData));

            Map<String, Object> data = companySubscriptionService.updateCompanySubscriptionAutoRenew(
                    companySubscriptionUuid,
                    request != null ? request.getAutoRenew() : null
            );
            return ResponseEntity.ok(ApiResponse.success("Auto-refill preference updated successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update company auto-refill preference", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update company auto-refill preference"));
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

        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            throw new SecurityException("Invalid authenticated user");
        }

        var profileOpt = profileService.getProfileWithCompany(userId);
        UUID profileCompanyId = profileOpt
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);
        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to manage subscriptions for this company");
        }
    }

    private UUID requireUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }

    private UUID extractCompanyId(Map<String, Object> data) {
        Object companyId = data.get("companyId");
        if (companyId instanceof UUID uuid) {
            return uuid;
        }
        if (companyId instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        throw new IllegalArgumentException("companyId missing from company subscription payload");
    }

    public static class CreateCompanySubscriptionRequest {
        private String companyId;
        private String planId;
        private int sessionCount;
        private int seatCount;
        private String billingInterval;
        private String redirectSuccessUrl;
        private String redirectCancelUrl;

        public String getCompanyId() {
            return companyId;
        }

        public void setCompanyId(String companyId) {
            this.companyId = companyId;
        }

        public String getPlanId() {
            return planId;
        }

        public void setPlanId(String planId) {
            this.planId = planId;
        }

        public int getSessionCount() {
            return sessionCount;
        }

        public void setSessionCount(int sessionCount) {
            this.sessionCount = sessionCount;
        }

        public int getSeatCount() {
            return seatCount;
        }

        public void setSeatCount(int seatCount) {
            this.seatCount = seatCount;
        }

        public int getRequestedSessionCount() {
            return sessionCount > 0 ? sessionCount : seatCount;
        }

        public String getBillingInterval() {
            return billingInterval;
        }

        public void setBillingInterval(String billingInterval) {
            this.billingInterval = billingInterval;
        }

        public String getRedirectSuccessUrl() {
            return redirectSuccessUrl;
        }

        public void setRedirectSuccessUrl(String redirectSuccessUrl) {
            this.redirectSuccessUrl = redirectSuccessUrl;
        }

        public String getRedirectCancelUrl() {
            return redirectCancelUrl;
        }

        public void setRedirectCancelUrl(String redirectCancelUrl) {
            this.redirectCancelUrl = redirectCancelUrl;
        }
    }

    public static class AssignSeatRequest {
        private String profileId;

        public String getProfileId() {
            return profileId;
        }

        public void setProfileId(String profileId) {
            this.profileId = profileId;
        }
    }

    public static class RenewCompanySubscriptionRequest {
        private String billingInterval;
        private String redirectSuccessUrl;
        private String redirectCancelUrl;

        public String getBillingInterval() {
            return billingInterval;
        }

        public void setBillingInterval(String billingInterval) {
            this.billingInterval = billingInterval;
        }

        public String getRedirectSuccessUrl() {
            return redirectSuccessUrl;
        }

        public void setRedirectSuccessUrl(String redirectSuccessUrl) {
            this.redirectSuccessUrl = redirectSuccessUrl;
        }

        public String getRedirectCancelUrl() {
            return redirectCancelUrl;
        }

        public void setRedirectCancelUrl(String redirectCancelUrl) {
            this.redirectCancelUrl = redirectCancelUrl;
        }
    }

    public static class UpdateAutoRenewRequest {
        private Boolean autoRenew;

        public Boolean getAutoRenew() {
            return autoRenew;
        }

        public void setAutoRenew(Boolean autoRenew) {
            this.autoRenew = autoRenew;
        }
    }
}
