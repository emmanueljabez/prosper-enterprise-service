package com.prosper.prospermentor.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Custom authentication provider for Supabase JWT tokens
 * Integrates with Spring Security's authentication mechanism
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupabaseAuthenticationProvider implements AuthenticationProvider {

    private final SupabaseUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.debug("Authenticating with SupabaseAuthenticationProvider");

        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }

        UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) authentication;
        
        // For JWT-based authentication, the token itself is passed as credentials
        Object credentials = authToken.getCredentials();
        
        if (credentials instanceof String jwtToken) {
            return authenticateWithJwt(jwtToken);
        }

        // If we have a SupabaseUserDetails as principal, it's already authenticated
        if (authToken.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            log.debug("User already authenticated: {}", userDetails.getEmail());
            return authToken;
        }

        throw new BadCredentialsException("Unsupported authentication method");
    }

    /**
     * Authenticate using JWT token
     */
    private Authentication authenticateWithJwt(String jwtToken) throws AuthenticationException {
        try {
            // Validate JWT token
            if (!jwtUtil.validateToken(jwtToken)) {
                throw new BadCredentialsException("Invalid or expired JWT token");
            }

            // Extract user information from JWT
            String userId = jwtUtil.extractUserId(jwtToken);
            String email = jwtUtil.extractEmail(jwtToken);
            String role = jwtUtil.extractRole(jwtToken);

            if (userId == null || email == null) {
                throw new BadCredentialsException("JWT token missing required user information");
            }

            // Try to load user from database
            UserDetails userDetails;
            try {
                if (userDetailsService.userExistsById(userId)) {
                    userDetails = userDetailsService.loadUserByUserId(userId);
                    log.debug("Loaded user from database: {}", email);
                } else {
                    // User doesn't exist in local database yet, create from JWT
                    userDetails = userDetailsService.createUserDetailsFromJwt(userId, email, role);
                    log.debug("Created user details from JWT: {}", email);
                }
            } catch (UsernameNotFoundException e) {
                // Fallback to JWT-only user details
                userDetails = userDetailsService.createUserDetailsFromJwt(userId, email, role);
                log.debug("Using JWT-only user details for: {}", email);
            }

            // Create authenticated token
            UsernamePasswordAuthenticationToken authenticatedToken = 
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null, // No credentials stored
                    userDetails.getAuthorities()
                );

            log.debug("Successfully authenticated user: {} with role: {}", email, role);
            return authenticatedToken;

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            throw new BadCredentialsException("JWT authentication failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
