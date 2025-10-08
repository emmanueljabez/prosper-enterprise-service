package com.prosper.prospermentor.security;

import com.prosper.prospermentor.config.SupabaseConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT utility class for parsing and validating Supabase JWT tokens
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private final SupabaseConfig supabaseConfig;

    /**
     * Extract user ID from JWT token
     */
    public String extractUserId(String token) {
        Claims claims = extractClaims(token);
        return claims.getSubject();
    }

    /**
     * Extract user email from JWT token
     */
    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return (String) claims.get("email");
    }

    /**
     * Extract user role from JWT token
     */
    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        @SuppressWarnings("unchecked")
        Map<String, Object> userMetadata = (Map<String, Object>) claims.get("user_metadata");
        if (userMetadata != null) {
            return (String) userMetadata.get("role");
        }
        return "MENTEE"; // Default role
    }

    /**
     * Extract all user metadata from JWT token
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractUserMetadata(String token) {
        Claims claims = extractClaims(token);
        return (Map<String, Object>) claims.get("user_metadata");
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
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract claims from JWT token
     */
    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(supabaseConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}



