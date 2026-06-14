package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.MyReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "My Reviews", description = "Authenticated review workspace endpoints")
public class MyReviewController {

    private final MyReviewService myReviewService;

    @GetMapping("/me/reviews")
    @Operation(summary = "Get my review workspace", description = "Get WhatsApp review task status and review history for the authenticated user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyReviews(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireAuthenticatedUser(authentication);
            MyReviewService.MyReviewWorkspace workspace = myReviewService.getMyReviews(userDetails.getUserIdAsUuid());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", workspace.summary());
            data.put("reviews", workspace.reviews());
            data.put("count", workspace.reviews().size());

            return ResponseEntity.ok(ApiResponse.success("Review workspace retrieved successfully", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting review workspace: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get review workspace: " + e.getMessage()));
        }
    }

    private SupabaseUserDetails requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        return userDetails;
    }
}
