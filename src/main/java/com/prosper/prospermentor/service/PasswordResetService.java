package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.PasswordResetToken;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.PasswordResetTokenRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.PasswordResetNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ProfileRepository profileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetNotificationService passwordResetNotificationService;
    private final SupabaseAuthService supabaseAuthService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.password-reset.ttl-minutes:60}")
    private int tokenTtlMinutes;

    public void requestPasswordReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        Optional<Profile> profileOpt = profileRepository.findByEmailIgnoreCase(normalizedEmail);

        if (profileOpt.isEmpty()) {
            log.info("Password reset requested for non-existent account: {}", normalizedEmail);
            return;
        }

        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setProfile(profileOpt.get());
        resetToken.setEmail(normalizedEmail);
        resetToken.setTokenHash(hashToken(token));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes));

        passwordResetTokenRepository.save(resetToken);
        passwordResetNotificationService.sendPasswordResetEmail(
                normalizedEmail,
                buildResetUrl(token),
                tokenTtlMinutes
        );
    }

    public Mono<Void> resetPasswordWithToken(String token, String password) {
        return Mono.defer(() -> {
            if (token == null || token.trim().isEmpty()) {
                return Mono.error(new IllegalArgumentException("Reset link is invalid or has expired"));
            }
            if (password == null || password.trim().isEmpty()) {
                return Mono.error(new IllegalArgumentException("Password is required"));
            }
            if (password.length() < 8) {
                return Mono.error(new IllegalArgumentException("Password must be at least 8 characters long"));
            }

            PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hashToken(token.trim()))
                    .filter(this::isUsable)
                    .orElseThrow(() -> new IllegalArgumentException("Reset link is invalid or has expired"));

            Profile profile = resetToken.getProfile();
            if (profile == null || profile.getId() == null) {
                return Mono.error(new IllegalStateException("Password reset token is not linked to a profile"));
            }

            return supabaseAuthService.updateUserPassword(profile.getId().toString(), password)
                    .doOnSuccess(result -> {
                        resetToken.setUsedAt(LocalDateTime.now());
                        passwordResetTokenRepository.save(resetToken);
                    })
                    .then();
        });
    }

    private boolean isUsable(PasswordResetToken token) {
        return token.getUsedAt() == null
                && token.getExpiresAt() != null
                && token.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return normalizedEmail;
    }

    private String buildResetUrl(String token) {
        String normalizedFrontendUrl = normalizeBaseUrl(frontendUrl);
        return normalizedFrontendUrl + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://localhost:3000"
                : value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password reset token", e);
        }
    }
}
