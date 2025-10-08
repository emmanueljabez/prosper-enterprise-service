package com.prosper.prospermentor.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.interfaces.ECPublicKey;
import java.util.Date;
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
        Claims claims = extractClaims(token);
        return claims.getSubject();
    }

    /**
     * Extract user email from ES256 JWT token
     */
    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return (String) claims.get("email");
    }

    /**
     * Extract user role from ES256 JWT token
     */
    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        
        // Try user_metadata first (Supabase standard)
        @SuppressWarnings("unchecked")
        Map<String, Object> userMetadata = (Map<String, Object>) claims.get("user_metadata");
        if (userMetadata != null && userMetadata.containsKey("role")) {
            return (String) userMetadata.get("role");
        }
        
        // Try app_metadata as fallback
        @SuppressWarnings("unchecked")
        Map<String, Object> appMetadata = (Map<String, Object>) claims.get("app_metadata");
        if (appMetadata != null && appMetadata.containsKey("role")) {
            return (String) appMetadata.get("role");
        }
        
        // Try direct role claim
        String directRole = (String) claims.get("role");
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
        Claims claims = extractClaims(token);
        return (Map<String, Object>) claims.get("user_metadata");
    }

    /**
     * Extract all app metadata from ES256 JWT token
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractAppMetadata(String token) {
        Claims claims = extractClaims(token);
        return (Map<String, Object>) claims.get("app_metadata");
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
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
            ECPublicKey publicKey = jwkService.getPublicKey();
            
            // Parse and verify token in one step
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                log.debug("Token is expired");
                return false;
            }
            
            // Additional validation checks
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
    private Claims extractClaims(String token) {
        ECPublicKey publicKey = jwkService.getPublicKey();
        
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get token expiration time
     */
    public Date getTokenExpiration(String token) {
        Claims claims = extractClaims(token);
        return claims.getExpiration();
    }

    /**
     * Get token issued at time
     */
    public Date getTokenIssuedAt(String token) {
        Claims claims = extractClaims(token);
        return claims.getIssuedAt();
    }

    /**
     * Extract issuer from token
     */
    public String extractIssuer(String token) {
        Claims claims = extractClaims(token);
        return claims.getIssuer();
    }

    /**
     * Extract audience from token
     */
    public String extractAudience(String token) {
        Claims claims = extractClaims(token);
        return claims.getAudience().iterator().next(); // Get first audience
    }

    /**
     * Get all claims as a map
     */
    public Map<String, Object> extractAllClaims(String token) {
        return extractClaims(token);
    }
}
