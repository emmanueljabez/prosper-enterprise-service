package com.prosper.prospermentor.config;

import com.prosper.prospermentor.security.JwtAuthenticationFilter;
import com.prosper.prospermentor.security.SupabaseAuthenticationProvider;
import com.prosper.prospermentor.security.SupabaseUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for JWT-based authentication with Supabase
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SupabaseAuthenticationProvider supabaseAuthenticationProvider;
    private final SupabaseUserDetailsService userDetailsService;

    /**
     * Password encoder bean (kept for backward compatibility if needed)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication manager with custom Supabase provider
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(supabaseAuthenticationProvider));
    }

    /**
     * Security filter chain configuration for JWT authentication
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/public/**", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/database/**").permitAll() // Database info endpoints (for development)
                .requestMatchers("/api/admin/migration/**").permitAll() // Migration endpoints (for development)
                .requestMatchers("/health", "/actuator/**").permitAll()
                
                // Root endpoint and error pages
                .requestMatchers("/", "/error").permitAll()
                
                // Swagger/OpenAPI documentation endpoints
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()
                
                // Authentication endpoints (public)
                .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/signup",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
                        "/api/auth/refresh",
                        "/api/auth/complete-invitation-signup",
                        "/api/auth/complete-company-registration"
                ).permitAll()

                // Company invitation endpoints (public for employee signup)
                .requestMatchers("/api/v1/companies/whitelist/verify-invitation").permitAll()
                .requestMatchers("/api/v1/companies/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/company-mentor-invitations/verify").permitAll()
                
                // Demo endpoints (public endpoint only)
                .requestMatchers("/api/demo/public").permitAll()
                // Test endpoints (public for debugging)
                .requestMatchers("/api/test/**").permitAll()
                .requestMatchers("/api/v1/reviews/flow/**").permitAll()
                // Session booking endpoints (for testing)
                .requestMatchers("/api/v1/sessions/**").permitAll()
                // Calendar endpoints (public - accessed from emails)
                .requestMatchers("/api/v1/calendar/**").permitAll()
                .requestMatchers("/api/v1/payments/confirmc2b").permitAll()
                .requestMatchers("/api/v1/payments/status/**").permitAll()
                .requestMatchers("/api/v1/payments/cybersource/callback").permitAll()
                .requestMatchers("/api/v1/invoices/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/auth/confirm-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/company-signup-intents").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/public/company-signup-intents/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/company-signup-intents/*/complete").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/public/b2b-demo-requests").permitAll()
                // Public mentor endpoints (to browse mentors)
                .requestMatchers( "/api/v1/profiles/mentors/**").permitAll()

                    .requestMatchers("/api/v1/programs/**").permitAll()
                    .requestMatchers("/api/v1/subscriptions/**").permitAll()
                
                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // Mentor endpoints
                .requestMatchers("/api/mentor/**").hasAnyRole("ADMIN", "MENTOR")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            // Add JWT filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration for frontend integration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*")); // Configure specific origins in production
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
