package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyJoinLinkDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyJoinLink;
import com.prosper.prospermentor.repository.CompanyJoinLinkRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyJoinLinkService {

    private static final String TOKEN_SEPARATOR = ".";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CompanyRepository companyRepository;
    private final CompanyJoinLinkRepository joinLinkRepository;
    private final ProfileRepository profileRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.company-join-link-secret:${supabase.jwt-secret:}}")
    private String joinLinkSigningSecret;

    @Transactional
    public CompanyJoinLinkDto getOrCreateJoinLink(UUID companyId, UUID adminProfileId) {
        Company company = findCompany(companyId);
        CompanyJoinLink link = joinLinkRepository
                .findFirstByCompanyIdAndStatus(companyId, CompanyJoinLink.Status.ACTIVE)
                .orElseGet(() -> createActiveLink(company, adminProfileId));

        return toDto(link, false);
    }

    @Transactional
    public CompanyJoinLinkDto regenerateJoinLink(UUID companyId, UUID adminProfileId) {
        Company company = findCompany(companyId);
        joinLinkRepository.findFirstByCompanyIdAndStatus(companyId, CompanyJoinLink.Status.ACTIVE)
                .ifPresent(activeLink -> {
                    activeLink.setStatus(CompanyJoinLink.Status.REVOKED);
                    activeLink.setRevokedAt(LocalDateTime.now());
                    activeLink.setRevokedByProfileId(adminProfileId);
                    joinLinkRepository.save(activeLink);
                });

        return toDto(createActiveLink(company, adminProfileId), false);
    }

    @Transactional
    public CompanyJoinLinkDto completeJoinAfterVerification(String rawToken, UUID profileId, String email) {
        ParsedToken parsedToken = parseAndValidateFormat(rawToken);
        CompanyJoinLink link = joinLinkRepository
                .findByIdAndStatus(parsedToken.linkId(), CompanyJoinLink.Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN"));

        validateToken(rawToken, parsedToken, link);

        UUID resolvedProfileId = resolveProfileId(profileId, email);
        int updated = profileRepository.updateCompanyId(
                resolvedProfileId,
                link.getCompany().getId(),
                java.time.ZonedDateTime.now()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("COMPANY_JOIN_NOT_ALLOWED");
        }

        link.setLastUsedAt(LocalDateTime.now());
        joinLinkRepository.save(link);

        return toDto(link, true);
    }

    String toJoinToken(CompanyJoinLink link) {
        if (link.getId() == null || link.getTokenNonce() == null || link.getTokenNonce().isBlank()) {
            throw new IllegalStateException("Company join link is missing token material");
        }
        String payload = link.getId() + TOKEN_SEPARATOR + link.getTokenNonce();
        return payload + TOKEN_SEPARATOR + sign(payload);
    }

    private CompanyJoinLink createActiveLink(Company company, UUID adminProfileId) {
        CompanyJoinLink link = new CompanyJoinLink();
        link.setId(UUID.randomUUID());
        link.setCompany(company);
        link.setTokenNonce(generateNonce());
        link.setStatus(CompanyJoinLink.Status.ACTIVE);
        link.setCreatedByProfileId(adminProfileId);
        link.setTokenHash(sha256Hex(toJoinToken(link)));
        return joinLinkRepository.save(link);
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    }

    private UUID resolveProfileId(UUID profileId, String email) {
        if (profileId != null) {
            return profileId;
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("COMPANY_JOIN_NOT_ALLOWED");
        }
        return profileRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("COMPANY_JOIN_NOT_ALLOWED"))
                .getId();
    }

    private void validateToken(String rawToken, ParsedToken parsedToken, CompanyJoinLink link) {
        if (!parsedToken.nonce().equals(link.getTokenNonce())) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }

        String expectedPayload = link.getId() + TOKEN_SEPARATOR + link.getTokenNonce();
        String expectedSignature = sign(expectedPayload);
        if (!constantTimeEquals(expectedSignature, parsedToken.signature())) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }

        String expectedHash = sha256Hex(rawToken);
        if (!constantTimeEquals(expectedHash, link.getTokenHash())) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }
    }

    private ParsedToken parseAndValidateFormat(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }
        String[] parts = rawToken.trim().split("\\.", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }
        try {
            return new ParsedToken(UUID.fromString(parts[0]), parts[1], parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("INVALID_COMPANY_JOIN_TOKEN");
        }
    }

    private CompanyJoinLinkDto toDto(CompanyJoinLink link, boolean linked) {
        String joinToken = toJoinToken(link);
        Company company = link.getCompany();
        return CompanyJoinLinkDto.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .joinToken(joinToken)
                .joinUrl(joinUrl(joinToken))
                .status(link.getStatus().name())
                .createdAt(link.getCreatedAt())
                .revokedAt(link.getRevokedAt())
                .lastUsedAt(link.getLastUsedAt())
                .linked(linked)
                .build();
    }

    private String joinUrl(String joinToken) {
        return normalizeBaseUrl(frontendUrl)
                + "/auth/signup?audience=mentee&companyJoinToken="
                + URLEncoder.encode(joinToken, StandardCharsets.UTF_8);
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

    private String generateNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign company join token", e);
        }
    }

    private String signingSecret() {
        if (joinLinkSigningSecret != null && !joinLinkSigningSecret.isBlank()) {
            return joinLinkSigningSecret;
        }
        return "local-development-company-join-link-secret";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private record ParsedToken(UUID linkId, String nonce, String signature) {
    }
}
