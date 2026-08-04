package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.model.ApiResponse;
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
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class AuthControllerFreeTrialTest {

    @Mock private SupabaseAuthService supabaseAuthService;
    @Mock private ProfileService profileService;
    @Mock private CompanyService companyService;
    @Mock private CompanyAdminRegistrationService companyAdminRegistrationService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private MenteeNotificationService menteeNotificationService;
    @Mock private PasswordResetService passwordResetService;

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
                passwordResetService
        );
        ReflectionTestUtils.setField(authController, "frontendUrl", "https://enterprise.prospermentor.com");
    }

    @Test
    void signup_shouldActivateFreeTrialAndSendCustomConfirmationWhenTrialProductRequested() throws Exception {
        UUID userId = UUID.randomUUID();

        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("mentee@example.com");
        request.setPassword("Password123!");
        request.setRole("mentee");
        request.setFirstName("Mentee");
        request.setLastName("User");
        request.setPhoneNumber("+254700000000");
        request.setProduct("FREE_TRIAL");
        request.setTrial(true);

        when(supabaseAuthService.generateSignupConfirmationLink(
                "mentee@example.com",
                "Password123!",
                "mentee",
                "Mentee",
                "User",
                "+254700000000",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1&audience=mentee&trial=1&product=FREE_TRIAL"
        ))
                .thenReturn(Mono.just(objectMapper.readTree("""
                        {
                          "action_link": "https://supabase.example.com/auth/v1/verify?token=abc&type=signup",
                          "hashed_token": "hashed-abc",
                          "user": {
                            "id": "%s",
                            "email": "mentee@example.com"
                          }
                        }
                        """.formatted(userId))));
        when(profileService.createProfileWithDetails(
                userId,
                "mentee@example.com",
                "mentee",
                "Mentee",
                "User",
                "+254700000000",
                null
        ))
                .thenReturn(Optional.of(Map.of("id", userId, "email", "mentee@example.com", "role", "mentee")));
        when(subscriptionService.activateFreeTrial(userId))
                .thenReturn(ApiResponse.success("Free trial activated", trialSubscription(userId)));

        ResponseEntity<Object> response = authController.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsKey("freeTrial")
                .containsEntry("emailVerificationRequired", true)
                .containsEntry("message", "Mentee account created. Verify your email, then sign in to continue.");
        assertThat(body).doesNotContainKeys("access_token", "refresh_token");

        @SuppressWarnings("unchecked")
        Map<String, Object> freeTrial = (Map<String, Object>) body.get("freeTrial");
        assertThat(freeTrial).containsEntry("requested", true);
        assertThat(freeTrial).containsEntry("activated", true);
        assertThat(freeTrial).containsEntry("sessionDurationMinutes", 30);

        verify(subscriptionService).activateFreeTrial(userId);
        verify(menteeNotificationService).sendMenteeEmailConfirmation(
                "mentee@example.com",
                "Mentee",
                true,
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-abc&type=signup&audience=mentee&trial=1&product=FREE_TRIAL"
        );
        verify(supabaseAuthService, never()).signUpWithPassword(any(), any(), any());
    }

    private Subscription trialSubscription(UUID userId) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(userId);
        subscription.setStatus(Subscription.SubscriptionStatus.TRIAL);
        subscription.setIsTrial(true);
        subscription.setSessionsPerMonth(1);
        subscription.setSessionsUsed(0);
        return subscription;
    }
}
