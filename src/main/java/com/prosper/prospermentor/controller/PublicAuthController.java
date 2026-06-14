package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.ConfirmEmailRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.SupabaseAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final SupabaseAuthService supabaseAuthService;

    @PostMapping("/confirm-email")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        return supabaseAuthService.verifyEmailTokenHash(request.getTokenHash(), request.getType())
                .map(result -> ResponseEntity.ok(ApiResponse.success(
                        "Email verified successfully",
                        Map.<String, Object>of("emailVerified", true)
                )))
                .onErrorResume(error -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(
                                "Email verification link is invalid or has expired",
                                Map.<String, Object>of("emailVerified", false)
                        ))));
    }
}
