package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.PasswordResetToken;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.PasswordResetTokenRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.PasswordResetNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordResetNotificationService passwordResetNotificationService;
    @Mock
    private SupabaseAuthService supabaseAuthService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestPasswordReset_shouldCreateBackendTokenAndSendBrandedEmail() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Profile profile = new Profile();
        profile.setId(userId);
        profile.setEmail("User@Example.com");

        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 60);

        when(profileRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(profile));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.requestPasswordReset(" User@Example.com ");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getProfile()).isSameAs(profile);
        assertThat(savedToken.getEmail()).isEqualTo("user@example.com");
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(55));
        assertThat(savedToken.getUsedAt()).isNull();

        verify(passwordResetNotificationService).sendPasswordResetEmail(
                eq("user@example.com"),
                org.mockito.ArgumentMatchers.matches("https://enterprise\\.prospermentor\\.com/reset-password\\?token=.+"),
                eq(60)
        );
        verify(supabaseAuthService, never()).updateUserPassword(any(), any());
    }

    @Test
    void requestPasswordReset_shouldNotRevealMissingAccounts() {
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "https://enterprise.prospermentor.com");

        when(profileRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("missing@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetNotificationService, never()).sendPasswordResetEmail(any(), any(), any(Integer.class));
        verify(supabaseAuthService, never()).updateUserPassword(any(), any());
    }

    @Test
    void resetPasswordWithToken_shouldUpdateSupabasePasswordAndMarkTokenUsed() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Profile profile = new Profile();
        profile.setId(userId);
        profile.setEmail("user@example.com");

        PasswordResetToken token = new PasswordResetToken();
        token.setProfile(profile);
        token.setEmail("user@example.com");
        token.setTokenHash(sha256Hex("plain-token"));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("plain-token"))).thenReturn(Optional.of(token));
        when(supabaseAuthService.updateUserPassword(userId.toString(), "Password123!"))
                .thenReturn(Mono.just(objectMapper.createObjectNode()));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.resetPasswordWithToken("plain-token", "Password123!").block();

        verify(supabaseAuthService).updateUserPassword(userId.toString(), "Password123!");
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void resetPasswordWithToken_shouldReturnMonoErrorForUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(sha256Hex("missing-token"))).thenReturn(Optional.empty());

        Mono<Void> result = passwordResetService.resetPasswordWithToken("missing-token", "Password123!");

        assertThatThrownBy(result::block)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reset link is invalid or has expired");
        verify(supabaseAuthService, never()).updateUserPassword(any(), any());
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
