package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.B2BDemoRequestDto;
import com.prosper.prospermentor.dto.CreateB2BDemoRequestRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.B2BDemoRequestService;
import com.prosper.prospermentor.service.B2BDemoRequestThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class B2BDemoRequestController {

    private final B2BDemoRequestService b2BDemoRequestService;
    private final B2BDemoRequestThrottleService throttleService;

    @PostMapping("/api/v1/public/b2b-demo-requests")
    public ResponseEntity<ApiResponse<Void>> createRequest(
            @Valid @RequestBody CreateB2BDemoRequestRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            throttleService.assertAllowed(resolveClientAddress(servletRequest), request.getWorkEmail());
            b2BDemoRequestService.createRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("B2B demo request submitted successfully"));
        } catch (B2BDemoRequestThrottleService.TooManyDemoRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/admin/b2b-demo-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            validatePageRequest(page, size);
            Page<B2BDemoRequestDto> requests = b2BDemoRequestService.listRequests(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requests", requests.getContent());
            data.put("count", requests.getNumberOfElements());
            data.put("currentPage", requests.getNumber());
            data.put("pageSize", requests.getSize());
            data.put("totalPages", requests.getTotalPages());
            data.put("totalItems", requests.getTotalElements());
            data.put("hasNext", requests.hasNext());
            data.put("hasPrevious", requests.hasPrevious());

            return ResponseEntity.ok(ApiResponse.success("B2B demo requests retrieved successfully", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private String resolveClientAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim().toLowerCase(Locale.ROOT);
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim().toLowerCase(Locale.ROOT);
        }

        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress.trim().toLowerCase(Locale.ROOT);
    }
}
