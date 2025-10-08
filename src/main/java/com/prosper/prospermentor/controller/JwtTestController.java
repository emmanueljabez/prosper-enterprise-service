package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.security.ES256JwtUtil;
import com.prosper.prospermentor.security.JwkService;
import com.prosper.prospermentor.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.interfaces.ECPublicKey;
import java.util.Map;

/**
 * Test controller for JWT validation debugging
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class JwtTestController {

    private final ES256JwtUtil es256JwtUtil;
    private final JwtUtil jwtUtil;
    private final JwkService jwkService;

    @PostMapping("/jwt/validate")
    public Map<String, Object> validateJwt(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        
        if (token == null || token.trim().isEmpty()) {
            return Map.of("error", "Token is required");
        }

        try {
            // Test JWK Service
            ECPublicKey publicKey = jwkService.getPublicKey();
            String keyId = jwkService.getKeyId();
            String algorithm = jwkService.getAlgorithm();
            
            // Test ES256 validation
            boolean es256Valid = es256JwtUtil.validateToken(token);
            String es256Error = null;
            String es256UserId = null;
            String es256Email = null;
            String es256Role = null;
            
            if (es256Valid) {
                try {
                    es256UserId = es256JwtUtil.extractUserId(token);
                    es256Email = es256JwtUtil.extractEmail(token);
                    es256Role = es256JwtUtil.extractRole(token);
                } catch (Exception e) {
                    es256Error = "Failed to extract claims: " + e.getMessage();
                }
            }
            
            // Test HS256 validation
            boolean hs256Valid = jwtUtil.validateToken(token);
            String hs256Error = null;
            String hs256UserId = null;
            String hs256Email = null;
            String hs256Role = null;
            
            if (hs256Valid) {
                try {
                    hs256UserId = jwtUtil.extractUserId(token);
                    hs256Email = jwtUtil.extractEmail(token);
                    hs256Role = jwtUtil.extractRole(token);
                } catch (Exception e) {
                    hs256Error = "Failed to extract claims: " + e.getMessage();
                }
            }
            
            return Map.of(
                "jwk", Map.of(
                    "keyId", keyId,
                    "algorithm", algorithm,
                    "publicKeyLoaded", publicKey != null
                ),
                "es256", Map.of(
                    "valid", es256Valid,
                    "error", es256Error != null ? es256Error : "none",
                    "userId", es256UserId != null ? es256UserId : "null",
                    "email", es256Email != null ? es256Email : "null",
                    "role", es256Role != null ? es256Role : "null"
                ),
                "hs256", Map.of(
                    "valid", hs256Valid,
                    "error", hs256Error != null ? hs256Error : "none",
                    "userId", hs256UserId != null ? hs256UserId : "null",
                    "email", hs256Email != null ? hs256Email : "null",
                    "role", hs256Role != null ? hs256Role : "null"
                )
            );
            
        } catch (Exception e) {
            log.error("JWT validation test failed", e);
            return Map.of("error", "Validation test failed: " + e.getMessage());
        }
    }
}
