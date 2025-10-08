package com.prosper.prospermentor.security;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Map;

/**
 * Service for handling JSON Web Keys (JWK) for ES256 JWT verification
 */
@Service
@Slf4j
public class JwkService {

    /**
     * Your Supabase ES256 JWK public key
     * This should ideally be fetched from Supabase's JWK endpoint or configured externally
     */
    private static final Map<String, Object> SUPABASE_JWK = Map.of(
            "x", "LDEGXRLwEivAoznlLpwVQE8SoTJV2cvpzoFwzssQga0",
            "y", "VyRIwv8Ge-DbSP3GmK42iBijs14EDMwhM_pcgrsgH8g",
            "alg", "ES256",
            "crv", "P-256",
            "ext", true,
            "kid", "821fd269-e544-42f3-90ad-2a98e0b1599b",
            "kty", "EC",
            "key_ops", new String[]{"verify"}
    );

    private ECPublicKey publicKey;

    /**
     * Get the EC public key for JWT verification
     */
    public ECPublicKey getPublicKey() {
        if (publicKey == null) {
            publicKey = loadPublicKey();
        }
        return publicKey;
    }

    /**
     * Load the public key from the JWK
     */
    private ECPublicKey loadPublicKey() {
        try {
            log.info("Loading ES256 public key from JWK...");
            
            // Create JWK from the map
            JWK jwk = JWK.parse(SUPABASE_JWK);
            log.debug("Parsed JWK: {}", jwk.getKeyType());
            
            if (!(jwk instanceof ECKey)) {
                throw new IllegalArgumentException("JWK is not an EC key, got: " + jwk.getClass().getSimpleName());
            }
            
            ECKey ecKey = (ECKey) jwk;
            log.debug("ECKey algorithm: {}, curve: {}, keyID: {}", ecKey.getAlgorithm(), ecKey.getCurve(), ecKey.getKeyID());
            
            PublicKey key = ecKey.toPublicKey();
            log.debug("Generated PublicKey type: {}", key.getClass().getSimpleName());
            
            if (!(key instanceof ECPublicKey)) {
                throw new IllegalArgumentException("Generated key is not an ECPublicKey, got: " + key.getClass().getSimpleName());
            }
            
            ECPublicKey ecPublicKey = (ECPublicKey) key;
            log.info("Successfully loaded ES256 public key with kid: {}, algorithm: {}", ecKey.getKeyID(), ecKey.getAlgorithm());
            log.debug("Public key curve: {}", ecPublicKey.getParams().toString());
            
            return ecPublicKey;
            
        } catch (Exception e) {
            log.error("Failed to load public key from JWK", e);
            log.error("JWK details: {}", SUPABASE_JWK);
            throw new RuntimeException("Failed to initialize ES256 public key: " + e.getMessage(), e);
        }
    }

    /**
     * Get the key ID from the JWK
     */
    public String getKeyId() {
        return (String) SUPABASE_JWK.get("kid");
    }

    /**
     * Get the algorithm from the JWK
     */
    public String getAlgorithm() {
        return (String) SUPABASE_JWK.get("alg");
    }

    /**
     * Validate that this is the correct key for the given token
     * (In a production system, you might fetch JWKs from a JWK Set endpoint)
     */
    public boolean isValidKeyForToken(String keyId) {
        return getKeyId().equals(keyId);
    }

    /**
     * For future enhancement: Load JWK from Supabase's JWK endpoint
     * This would be more dynamic and secure
     */
    public void refreshJwkFromEndpoint(String jwkEndpoint) {
        // TODO: Implement fetching JWK from Supabase's JWK endpoint
        // This would allow for key rotation without code changes
        log.info("JWK refresh from endpoint not yet implemented: {}", jwkEndpoint);
    }
}
