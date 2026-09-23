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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerFreeTrialTest {

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
    void signup_shouldDelegateToSignupService() {
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("mentee@example.com");
        request.setPassword("Password123!");

        when(authSignupService.signup(request))
                .thenReturn(Mono.just(ResponseEntity.ok((Object) Map.of(
                        "status", "PENDING_EMAIL_VERIFICATION",
                        "emailVerificationRequired", true
                ))));

        ResponseEntity<Object> response = authController.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authSignupService).signup(request);
    }
}
