package com.prosper.prospermentor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

            TokenClaims tokenClaims = extractValidatedClaims(jwt);

            if (tokenClaims != null
                    && tokenClaims.userId() != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Try to load user details from database, fallback to JWT-only
                UserDetails userDetails;
                try {
                    if (userDetailsService.userExistsById(tokenClaims.userId())) {
                        userDetails = userDetailsService.loadUserByUserId(tokenClaims.userId());
                        log.debug("Loaded user from database: {}", tokenClaims.email());
                    } else {
                        userDetails = userDetailsService.createUserDetailsFromJwt(
                                tokenClaims.userId(),
                                tokenClaims.email(),
                                tokenClaims.role()
                        );
                        log.debug("Created user details from validated JWT: {}", tokenClaims.email());
                    }
                } catch (Exception e) {
                    log.warn("Failed to load user from database, using JWT-only details: {}", e.getMessage());
                    userDetails = userDetailsService.createUserDetailsFromJwt(
                            tokenClaims.userId(),
                            tokenClaims.email(),
                            tokenClaims.role()
                    );
                }

                // Create authentication token with UserDetails
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Successfully authenticated user: {} with role: {}", tokenClaims.email(), tokenClaims.role());
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // Clear security context on error
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private TokenClaims extractValidatedClaims(String jwt) {
        if (es256JwtUtil.validateToken(jwt)) {
            return new TokenClaims(
                    es256JwtUtil.extractUserId(jwt),
                    es256JwtUtil.extractEmail(jwt),
                    es256JwtUtil.extractRole(jwt)
            );
        }

        if (jwtUtil.validateToken(jwt)) {
            return new TokenClaims(
                    jwtUtil.extractUserId(jwt),
                    jwtUtil.extractEmail(jwt),
                    jwtUtil.extractRole(jwt)
            );
        }

        log.warn("Rejected JWT because signature validation failed");
        return null;
    }

    private record TokenClaims(String userId, String email, String role) {
    }
}


