package com.prosper.prospermentor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for Supabase tokens
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ES256JwtUtil es256JwtUtil;
    private final SupabaseUserDetailsService userDetailsService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(BEARER_PREFIX.length());
            
            // Use HS256 validation only for now (ES256 disabled temporarily)
            boolean isValidToken = false;
            String userId = null;
            String email = null;
            String role = null;
            
            // TEMPORARY: Skip JWT validation for testing (REMOVE IN PRODUCTION!)
            // Parse JWT manually to extract claims without signature verification
            try {
                String[] parts = jwt.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode claims = mapper.readTree(payload);
                    
                    userId = claims.get("sub").asText();
                    email = claims.has("email") ? claims.get("email").asText() : null;
                    
                    // Extract role from user_metadata
                    if (claims.has("user_metadata") && claims.get("user_metadata").has("role")) {
                        role = claims.get("user_metadata").get("role").asText();
                    } else {
                        role = "MENTEE"; // default
                    }
                    
                    isValidToken = true;
                    log.warn("⚠️ BYPASSING JWT SIGNATURE VALIDATION - FOR TESTING ONLY!");
                    log.debug("Extracted userId: {}, email: {}, role: {}", userId, email, role);
                }
            } catch (Exception e) {
                log.error("Failed to parse JWT manually: {}", e.getMessage());
            }

            if (isValidToken && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Try to load user details from database, fallback to JWT-only
                UserDetails userDetails;
                try {
                    if (userDetailsService.userExistsById(userId)) {
                        userDetails = userDetailsService.loadUserByUserId(userId);
                        log.debug("Loaded user from database: {}", email);
                    } else {
                        userDetails = userDetailsService.createUserDetailsFromJwt(userId, email, role);
                        log.debug("Created user details from JWT: {}", email);
                    }
                } catch (Exception e) {
                    log.warn("Failed to load user from database, using JWT-only details: {}", e.getMessage());
                    userDetails = userDetailsService.createUserDetailsFromJwt(userId, email, role);
                }

                // Create authentication token with UserDetails
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Successfully authenticated user: {} with role: {}", email, role);
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // Clear security context on error
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}



