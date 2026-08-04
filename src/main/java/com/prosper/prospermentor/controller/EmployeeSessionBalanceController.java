package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.EmployeeSessionAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/employee-session-allocations")
@RequiredArgsConstructor
@Slf4j
public class EmployeeSessionBalanceController {

    private final EmployeeSessionAllocationService employeeSessionAllocationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyBalance(Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireUser(authentication);
            Map<String, Object> balance = employeeSessionAllocationService.getMyBalance(userDetails.getUserIdAsUuid());
            return ResponseEntity.ok(ApiResponse.success("Employee session balance retrieved successfully", balance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to retrieve employee session balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve employee session balance"));
        }
    }

    private SupabaseUserDetails requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        if (userDetails.getUserIdAsUuid() == null) {
            throw new SecurityException("Invalid authenticated user");
        }
        return userDetails;
    }
}
