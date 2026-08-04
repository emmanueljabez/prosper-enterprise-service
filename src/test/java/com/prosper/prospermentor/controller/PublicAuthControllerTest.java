package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.ConfirmEmailRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.SupabaseAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAuthControllerTest {

    @Mock
    private SupabaseAuthService supabaseAuthService;

    @InjectMocks
    private PublicAuthController publicAuthController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void confirmEmail_shouldVerifyTokenHashWithoutReturningSessionTokens() throws Exception {
        ConfirmEmailRequest request = new ConfirmEmailRequest();
        request.setTokenHash("hashed-abc");
        request.setType("signup");

        when(supabaseAuthService.verifyEmailTokenHash("hashed-abc", "signup"))
                .thenReturn(Mono.just(objectMapper.readTree("""
                        {
                          "access_token": "secret",
                          "refresh_token": "secret",
                          "user": {
                            "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "email": "admin@example.com"
                          }
                        }
                        """)));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = publicAuthController.confirmEmail(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsEntry("emailVerified", true);
        assertThat(response.getBody().getData()).doesNotContainKeys("access_token", "refresh_token");
    }

    @Test
    void confirmEmail_shouldReturnBadRequestForExpiredToken() {
        ConfirmEmailRequest request = new ConfirmEmailRequest();
        request.setTokenHash("expired");
        request.setType("signup");

        when(supabaseAuthService.verifyEmailTokenHash("expired", "signup"))
                .thenReturn(Mono.error(new RuntimeException("Email link is invalid or has expired")));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = publicAuthController.confirmEmail(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getData()).containsEntry("emailVerified", false);
    }
}
