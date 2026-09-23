package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.dto.CompanyJoinLinkDto;
import com.prosper.prospermentor.dto.ConfirmEmailRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.CompanyJoinLinkService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final SupabaseAuthService supabaseAuthService;
    private final CompanyJoinLinkService companyJoinLinkService;

    @PostMapping("/confirm-email")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        return supabaseAuthService.verifyEmailTokenHash(request.getTokenHash(), request.getType())
                .map(result -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("emailVerified", true);
                    if (request.getCompanyJoinToken() != null && !request.getCompanyJoinToken().trim().isEmpty()) {
                        CompanyJoinLinkDto joinLink = companyJoinLinkService.completeJoinAfterVerification(
                                request.getCompanyJoinToken(),
                                resolveProfileId(result),
                                resolveEmail(result)
                        );
                        data.put("companyJoin", Map.<String, Object>of(
                                "linked", joinLink.isLinked(),
                                "companyId", joinLink.getCompanyId(),
                                "companyName", joinLink.getCompanyName()
                        ));
                    }
                    return ResponseEntity.ok(ApiResponse.success(
                            "Email verified successfully",
                            data
                    ));
                })
                .onErrorResume(error -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(
                                "Email verification link is invalid or has expired",
                                Map.<String, Object>of("emailVerified", false)
                        ))));
    }

    private UUID resolveProfileId(JsonNode result) {
        JsonNode user = resolveUserNode(result);
        if (user != null && user.hasNonNull("id")) {
            return UUID.fromString(user.get("id").asText());
        }
        return null;
    }

    private String resolveEmail(JsonNode result) {
        JsonNode user = resolveUserNode(result);
        return user != null && user.hasNonNull("email") ? user.get("email").asText() : null;
    }

    private JsonNode resolveUserNode(JsonNode result) {
        if (result == null || result.isNull()) {
            return null;
        }
        return result.has("user") ? result.get("user") : result;
    }
}
