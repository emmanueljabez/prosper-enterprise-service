package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.controller.AuthController;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.notification.AuthVerificationNotificationService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSignupServiceTest {

    @Mock private SupabaseAuthService supabaseAuthService;
    @Mock private ProfileService profileService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private MenteeNotificationService menteeNotificationService;
    @Mock private AuthVerificationNotificationService authVerificationNotificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthSignupService service;

    @BeforeEach
    void setUp() {
        service = new AuthSignupService(
                supabaseAuthService,
                profileService,
                subscriptionService,
                menteeNotificationService,
                authVerificationNotificationService,
                new AuthSessionMapper(objectMapper)
        );
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
    }

    @Test
    void signup_shouldReturnUnifiedPendingVerificationResponse() throws Exception {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("mentee@example.com");
        request.setPassword("Password123!");
        request.setRole("mentee");
        request.setAudience("mentee");
        request.setFirstName("Mentee");
        request.setLastName("Signup");
        request.setPhoneNumber("+254700000000");
        request.setProduct("FREE_TRIAL");
        request.setTrial(true);

        when(supabaseAuthService.generateSignupConfirmationLink(
                "mentee@example.com",
                "Password123!",
                "mentee",
                "Mentee",
                "Signup",
                "+254700000000",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1&audience=mentee&trial=1&product=FREE_TRIAL"
        )).thenReturn(Mono.just(objectMapper.readTree("""
                {
                  "action_link": "https://supabase.example.com/auth/v1/verify?token=abc&type=signup",
                  "hashed_token": "hashed-abc",
                  "user": {
                    "id": "55555555-5555-5555-5555-555555555555",
                    "email": "mentee@example.com",
                    "user_metadata": {
                      "first_name": "Mentee",
                      "last_name": "Signup"
                    }
                  }
                }
                """)));
        when(profileService.createProfileWithDetails(
                userId,
                "mentee@example.com",
                "mentee",
                "Mentee",
                "Signup",
                "+254700000000",
                null
        )).thenReturn(Optional.of(Map.of(
                "id", userId,
                "email", "mentee@example.com",
                "role", "mentee",
                "firstName", "Mentee",
                "lastName", "Signup"
        )));
        when(subscriptionService.activateFreeTrial(userId))
                .thenReturn(ApiResponse.success("Free trial activated", trialSubscription(userId)));

        ResponseEntity<Object> response = service.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("status", "PENDING_EMAIL_VERIFICATION")
                .containsEntry("emailVerificationRequired", true)
                .containsKeys("user", "profile", "memberships", "entitlements", "defaultDestination", "freeTrial");

        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) body.get("defaultDestination");
        assertThat(destination)
                .containsEntry("product", "b2c")
                .containsEntry("url", "https://enterprise.prospermentor.com/auth/email-verification?email=mentee%40example.com&audience=mentee&trial=1&product=FREE_TRIAL");

        verify(menteeNotificationService).sendMenteeEmailConfirmation(
                "mentee@example.com",
                "Mentee",
                true,
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-abc&type=signup&audience=mentee&trial=1&product=FREE_TRIAL"
        );
    }

    @Test
    void signup_shouldSendRoleAwareVerificationForMentorSignup() throws Exception {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("mentor@example.com");
        request.setPassword("Password123!");
        request.setRole("mentor");
        request.setAudience("b2c");
        request.setFirstName("Mentor");
        request.setLastName("Signup");
        request.setPhoneNumber("+254711111111");

        when(supabaseAuthService.generateSignupConfirmationLink(
                "mentor@example.com",
                "Password123!",
                "mentor",
                "Mentor",
                "Signup",
                "+254711111111",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1"
        )).thenReturn(Mono.just(objectMapper.readTree("""
                {
                  "action_link": "https://supabase.example.com/auth/v1/verify?token=mentor-token&type=signup",
                  "hashed_token": "hashed-mentor",
                  "user": {
                    "id": "66666666-6666-6666-6666-666666666666",
                    "email": "mentor@example.com",
                    "user_metadata": {
                      "first_name": "Mentor",
                      "last_name": "Signup"
                    }
                  }
                }
                """)));
        when(profileService.createProfileWithDetails(
                userId,
                "mentor@example.com",
                "mentor",
                "Mentor",
                "Signup",
                "+254711111111",
                null
        )).thenReturn(Optional.of(Map.of(
                "id", userId,
                "email", "mentor@example.com",
                "role", "mentor",
                "firstName", "Mentor",
                "lastName", "Signup"
        )));

        ResponseEntity<Object> response = service.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authVerificationNotificationService).sendEmailConfirmation(
                "mentor@example.com",
                "Mentor",
                "mentor",
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-mentor&type=signup&audience=mentor"
        );
        verify(menteeNotificationService, never()).sendMenteeEmailConfirmation(
                "mentor@example.com",
                "Mentor",
                false,
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-mentor&type=signup&audience=mentor"
        );
    }

    @Test
    void signup_shouldCarryCompanyJoinTokenToVerificationUrls() throws Exception {
        UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("join@example.com");
        request.setPassword("Password123!");
        request.setRole("mentee");
        request.setAudience("mentee");
        request.setFirstName("Join");
        request.setLastName("Signup");
        request.setPhoneNumber("+254722222222");
        request.setCompanyJoinToken("join.token.value");

        when(supabaseAuthService.generateSignupConfirmationLink(
                "join@example.com",
                "Password123!",
                "mentee",
                "Join",
                "Signup",
                "+254722222222",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1&companyJoinToken=join.token.value"
        )).thenReturn(Mono.just(objectMapper.readTree("""
                {
                  "action_link": "https://supabase.example.com/auth/v1/verify?token=join-token&type=signup",
                  "hashed_token": "hashed-join",
                  "user": {
                    "id": "77777777-7777-7777-7777-777777777777",
                    "email": "join@example.com",
                    "user_metadata": {
                      "first_name": "Join",
                      "last_name": "Signup"
                    }
                  }
                }
                """)));
        when(profileService.createProfileWithDetails(
                userId,
                "join@example.com",
                "mentee",
                "Join",
                "Signup",
                "+254722222222",
                null
        )).thenReturn(Optional.of(Map.of(
                "id", userId,
                "email", "join@example.com",
                "role", "mentee",
                "firstName", "Join",
                "lastName", "Signup"
        )));

        ResponseEntity<Object> response = service.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) body.get("defaultDestination");
        assertThat(destination)
                .containsEntry("url", "https://enterprise.prospermentor.com/auth/email-verification?email=join%40example.com&audience=mentee&companyJoinToken=join.token.value");

        verify(menteeNotificationService).sendMenteeEmailConfirmation(
                "join@example.com",
                "Join",
                false,
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-join&type=signup&audience=mentee&companyJoinToken=join.token.value"
        );
    }

    @Test
    void signup_shouldReturnStableDuplicateEmailErrorCode() {
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Password123!");

        when(supabaseAuthService.generateSignupConfirmationLink(
                "existing@example.com",
                "Password123!",
                "mentee",
                null,
                null,
                null,
                "https://enterprise.prospermentor.com/auth/login?email_verified=1"
        )).thenReturn(Mono.error(new RuntimeException("User already registered")));

        ResponseEntity<Object> response = service.signup(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "error", "User already exists with this email",
                "errorCode", "EMAIL_ALREADY_EXISTS"
        ));
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
