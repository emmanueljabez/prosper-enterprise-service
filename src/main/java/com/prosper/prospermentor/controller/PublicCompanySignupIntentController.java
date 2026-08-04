package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.CompleteCompanySignupIntentRequest;
import com.prosper.prospermentor.dto.CreateCompanySignupIntentRequest;
import com.prosper.prospermentor.dto.ResumeCompanySignupIntentPurchaseRequest;
import com.prosper.prospermentor.entity.CompanySignupIntent;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.CompanyAdminRegistrationService;
import com.prosper.prospermentor.service.CompanySignupIntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/company-signup-intents")
@RequiredArgsConstructor
public class PublicCompanySignupIntentController {

    private final CompanySignupIntentService companySignupIntentService;
    private final CompanyAdminRegistrationService companyAdminRegistrationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createIntent(@Valid @RequestBody CreateCompanySignupIntentRequest request) {
        try {
            CompanySignupIntent intent = companySignupIntentService.createIntent(
                    request.getCompanyName(),
                    request.getWorkEmail(),
                    request.getPhoneNumber(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPlanId(),
                    request.getSessionCount()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                            "Company signup intent created successfully",
                            companySignupIntentService.toPublicPayload(intent)
                    ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIntent(@PathVariable String token) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Company signup intent retrieved successfully",
                    companySignupIntentService.toPublicPayload(companySignupIntentService.requireActiveIntent(token))
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{token}/complete")
    public Mono<ResponseEntity<Object>> completeIntent(@PathVariable String token,
                                                       @Valid @RequestBody CompleteCompanySignupIntentRequest request) {
        return companyAdminRegistrationService.completeIntent(token, request);
    }

    @PostMapping("/{token}/resume-purchase")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumePurchase(@PathVariable String token,
                                                                           Authentication authentication,
                                                                           @Valid @RequestBody ResumeCompanySignupIntentPurchaseRequest request) {
        try {
            SupabaseUserDetails userDetails = (SupabaseUserDetails) authentication.getPrincipal();
            Map<String, Object> payload = companyAdminRegistrationService.resumePurchase(
                    token,
                    userDetails.getUserIdAsUuid(),
                    request.getSessionCount(),
                    request.getRedirectSuccessUrl(),
                    request.getRedirectCancelUrl()
            );
            return ResponseEntity.ok(ApiResponse.success("Company purchase resumed successfully", payload));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
