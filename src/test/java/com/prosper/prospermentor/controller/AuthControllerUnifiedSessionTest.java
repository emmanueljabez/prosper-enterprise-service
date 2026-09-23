package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.service.AuthSessionMapper;
import com.prosper.prospermentor.service.AuthSignupService;
import com.prosper.prospermentor.service.CompanyAdminRegistrationService;
import com.prosper.prospermentor.service.CompanyService;
import com.prosper.prospermentor.service.PasswordResetService;
import com.prosper.prospermentor.service.ProfileService;
import com.prosper.prospermentor.service.SubscriptionService;
import com.prosper.prospermentor.service.SupabaseAuthService;
import com.prosper.prospermentor.service.notification.MenteeNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnifiedSessionTest {

    @Mock private SupabaseAuthService supabaseAuthService;
    @Mock private ProfileService profileService;
    @Mock private CompanyService companyService;
    @Mock private CompanyAdminRegistrationService companyAdminRegistrationService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private MenteeNotificationService menteeNotificationService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private AuthSignupService authSignupService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                supabaseAuthService,
                profileService,
                companyService,
                companyAdminRegistrationService,
                subscriptionService,
                objectMapper,
                menteeNotificationService,
                passwordResetService,
                new AuthSessionMapper(objectMapper),
                authSignupService
        );
    }

    @Test
    void login_shouldReturnUnifiedSessionFieldsAndLegacyTokenFields() throws Exception {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setEmail("employee@example.com");
        request.setPassword("Password123!");

        when(supabaseAuthService.signInWithPassword("employee@example.com", "Password123!"))
                .thenReturn(Mono.just(objectMapper.readTree("""
                        {
                          "access_token": "access-value",
                          "refresh_token": "refresh-value",
                          "user": {
                            "id": "44444444-4444-4444-4444-444444444444",
                            "email": "employee@example.com"
                          }
                        }
                        """)));
        when(profileService.getCompleteProfile(userId))
                .thenReturn(Optional.of(Map.of(
                        "id", userId,
                        "email", "employee@example.com",
                        "firstName", "Employee",
                        "lastName", "User",
                        "role", "mentee",
                        "companyId", "55555555-5555-5555-5555-555555555555"
                )));

        ResponseEntity<Object> response = authController.login(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("access_token", "access-value")
                .containsEntry("refresh_token", "refresh-value")
                .containsEntry("accessToken", "access-value")
                .containsEntry("refreshToken", "refresh-value")
                .containsKeys("user", "profile", "memberships", "entitlements", "defaultDestination");
    }
}
