package com.prosper.prospermentor.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.security.interfaces.ECPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT utility class for ES256 (ECDSA with SHA-256) token verification
 * Uses public key cryptography for enhanced security and SOC2 compliance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ES256JwtUtil {

    private final JwkService jwkService;

    /**
     * Extract user ID from ES256 JWT token
     */
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extract user email from ES256 JWT token
     */
    public String extractEmail(String token) {
        return stringClaim(token, "email");
    }

    /**
     * Extract user role from ES256 JWT token
     */
    public String extractRole(String token) {
        JWTClaimsSet claims = extractClaims(token);

        // Try user_metadata first (Supabase standard)
        @SuppressWarnings("unchecked")
        Map<String, Object> userMetadata = (Map<String, Object>) claims.getClaim("user_metadata");
        if (userMetadata != null && userMetadata.containsKey("role")) {
            return (String) userMetadata.get("role");
        }

        // Try app_metadata as fallback
        @SuppressWarnings("unchecked")
        Map<String, Object> appMetadata = (Map<String, Object>) claims.getClaim("app_metadata");
        if (appMetadata != null && appMetadata.containsKey("role")) {
            return (String) appMetadata.get("role");
        }

        // Try direct role claim
        String directRole = stringClaim(claims, "role");
        if (directRole != null) {
            return directRole;
        }

        return "MENTEE"; // Default role
    }

    /**
     * Extract all user metadata from ES256 JWT token
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractUserMetadata(String token) {
        return (Map<String, Object>) extractClaims(token).getClaim("user_metadata");
    }

    /**
     * Extract all app metadata from ES256 JWT token
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractAppMetadata(String token) {
        return (Map<String, Object>) extractClaims(token).getClaim("app_metadata");
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaims(token).getExpirationTime();
            return expiration == null || expiration.before(new Date());
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Validate ES256 JWT token using public key verification
     */
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            String keyId = signedJwt.getHeader().getKeyID();
            ECPublicKey publicKey = jwkService.getPublicKey(keyId);

            if (!signedJwt.verify(new ECDSAVerifier(publicKey))) {
                log.debug("ES256 JWT signature verification failed for kid {}", keyId);
                return false;
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                log.debug("Token is expired");
                return false;
            }

            if (claims.getSubject() == null || claims.getSubject().trim().isEmpty()) {
                log.warn("Token has no subject (user ID)");
                return false;
            }

            log.debug("ES256 token validation successful for user: {}", claims.getSubject());
            return true;

        } catch (Exception e) {
            log.debug("ES256 JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }


    /**
     * Extract and verify claims from ES256 JWT token
     */
    private JWTClaimsSet extractClaims(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            String keyId = signedJwt.getHeader().getKeyID();
            ECPublicKey publicKey = jwkService.getPublicKey(keyId);

            if (!signedJwt.verify(new ECDSAVerifier(publicKey))) {
                throw new IllegalArgumentException("Invalid ES256 signature");
            }

            return signedJwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("Failed to parse ES256 token", e);
        }
    }

    /**
     * Get token expiration time
     */
    public Date getTokenExpiration(String token) {
        return extractClaims(token).getExpirationTime();
    }

    /**
     * Get token issued at time
     */
    public Date getTokenIssuedAt(String token) {
        return extractClaims(token).getIssueTime();
    }

    /**
     * Extract issuer from token
     */
    public String extractIssuer(String token) {
        return extractClaims(token).getIssuer();
    }

    /**
     * Extract audience from token
     */
    public String extractAudience(String token) {
        List<String> audience = extractClaims(token).getAudience();
        return (audience == null || audience.isEmpty()) ? null : audience.get(0);
    }

    /**
     * Get all claims as a map
     */
    public Map<String, Object> extractAllClaims(String token) {
        return extractClaims(token).getClaims();
    }

    private String stringClaim(String token, String claimName) {
        return stringClaim(extractClaims(token), claimName);
    }

    private String stringClaim(JWTClaimsSet claims, String claimName) {
        try {
            return claims.getStringClaim(claimName);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to read claim " + claimName, e);
        }
    }
}
