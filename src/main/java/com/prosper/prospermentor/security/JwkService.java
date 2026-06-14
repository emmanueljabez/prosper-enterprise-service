package com.prosper.prospermentor.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.prosper.prospermentor.config.SupabaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling JSON Web Keys (JWK) for ES256 JWT verification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwkService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fallback JWKs used only if the live Supabase JWKS endpoint cannot be reached.
     */
    private static final Map<String, Map<String, Object>> FALLBACK_JWKS = Map.of(
            "821fd269-e544-42f3-90ad-2a98e0b1599b", Map.of(
                    "x", "LDEGXRLwEivAoznlLpwVQE8SoTJV2cvpzoFwzssQga0",
                    "y", "VyRIwv8Ge-DbSP3GmK42iBijs14EDMwhM_pcgrsgH8g",
                    "alg", "ES256",
                    "crv", "P-256",
                    "ext", true,
                    "kid", "821fd269-e544-42f3-90ad-2a98e0b1599b",
                    "kty", "EC",
                    "key_ops", new String[]{"verify"}
            ),
            "09a3f323-f8c0-4268-a144-c3a1ddd954e4", Map.of(
                    "x", "FJq69ANl6VoqD6EN_XJpc5yGAlvGVTiCNQLB_VKCbzg",
                    "y", "VlOrlxc0eVPGDW4Np6YWM7avaQF92Hp2nJ8I6iguaBs",
                    "alg", "ES256",
                    "crv", "P-256",
                    "ext", true,
                    "kid", "09a3f323-f8c0-4268-a144-c3a1ddd954e4",
                    "kty", "EC",
                    "key_ops", new String[]{"verify"}
            )
    );

    private final SupabaseConfig supabaseConfig;
    private final Map<String, ECPublicKey> publicKeys = new ConcurrentHashMap<>();
    private volatile boolean keysLoaded;

    /**
     * Get the EC public key for JWT verification.
     */
    public ECPublicKey getPublicKey(String keyId) {
        ensureKeysLoaded();
        ECPublicKey publicKey = publicKeys.get(keyId);
        if (publicKey == null) {
            throw new IllegalArgumentException("No JWK registered for key ID: " + keyId);
        }
        return publicKey;
    }

    /**
     * Backwards-compatible default key lookup.
     */
    public ECPublicKey getPublicKey() {
        return getPublicKey("821fd269-e544-42f3-90ad-2a98e0b1599b");
    }

    public String getKeyId() {
        return "821fd269-e544-42f3-90ad-2a98e0b1599b";
    }

    public String getAlgorithm() {
        return "ES256";
    }

    private void ensureKeysLoaded() {
        if (keysLoaded) {
            return;
        }

        synchronized (this) {
            if (keysLoaded) {
                return;
            }

            Map<String, Map<String, Object>> jwks = fetchRemoteJwks();
            if (jwks.isEmpty()) {
                log.warn("Falling back to bundled Supabase JWKs");
                jwks = FALLBACK_JWKS;
            }

            jwks.forEach((kid, jwkMap) -> publicKeys.put(kid, loadPublicKey(jwkMap)));
            keysLoaded = true;
        }
    }

    private Map<String, Map<String, Object>> fetchRemoteJwks() {
        try {
            String jwksUrl = supabaseConfig.getUrl().replaceAll("/+$", "") + "/auth/v1/.well-known/jwks.json";
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .uri(URI.create(jwksUrl))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Supabase JWKS endpoint returned status {}", response.statusCode());
                return Map.of();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode keys = root.path("keys");
            if (!keys.isArray() || keys.isEmpty()) {
                return Map.of();
            }

            Map<String, Map<String, Object>> jwks = new ConcurrentHashMap<>();
            for (JsonNode keyNode : keys) {
                String kid = keyNode.path("kid").asText(null);
                if (kid == null || kid.isBlank()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> jwk = OBJECT_MAPPER.convertValue(keyNode, Map.class);
                jwks.put(kid, jwk);
            }
            log.info("Loaded {} JWK(s) from Supabase", jwks.size());
            return jwks;
        } catch (Exception e) {
            log.warn("Failed to fetch Supabase JWKS endpoint: {}", e.getMessage());
            return Map.of();
        }
    }

    private ECPublicKey loadPublicKey(Map<String, Object> jwkMap) {
        try {
            JWK jwk = JWK.parse(jwkMap);
            if (!(jwk instanceof ECKey ecKey)) {
                throw new IllegalArgumentException("JWK is not an EC key, got: " + jwk.getClass().getSimpleName());
            }

            PublicKey key = ecKey.toPublicKey();
            if (!(key instanceof ECPublicKey ecPublicKey)) {
                throw new IllegalArgumentException("Generated key is not an ECPublicKey, got: " + key.getClass().getSimpleName());
            }

            log.info("Loaded ES256 public key with kid: {}", ecKey.getKeyID());
            return ecPublicKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ES256 public key", e);
        }
    }
}
