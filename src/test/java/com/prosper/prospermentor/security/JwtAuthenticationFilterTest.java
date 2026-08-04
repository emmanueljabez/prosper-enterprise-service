package com.prosper.prospermentor.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ES256JwtUtil es256JwtUtil;
    @Mock
    private SupabaseUserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_shouldAuthenticateUsingValidatedEs256Token() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, es256JwtUtil, userDetailsService);
        MockHttpServletRequest request = authorizedRequest("es256-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(es256JwtUtil.validateToken("es256-token")).thenReturn(true);
        when(es256JwtUtil.extractUserId("es256-token")).thenReturn("user-1");
        when(es256JwtUtil.extractEmail("es256-token")).thenReturn("admin@example.com");
        when(es256JwtUtil.extractRole("es256-token")).thenReturn("ADMIN");
        when(userDetailsService.userExistsById("user-1")).thenReturn(true);
        when(userDetailsService.loadUserByUserId("user-1"))
                .thenReturn(new User("admin@example.com", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@example.com");
        verify(es256JwtUtil).validateToken("es256-token");
        verify(jwtUtil, never()).validateToken(anyString());
    }

    @Test
    void doFilterInternal_shouldRejectTokenWhenNoValidatorAcceptsIt() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, es256JwtUtil, userDetailsService);
        MockHttpServletRequest request = authorizedRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(es256JwtUtil.validateToken("invalid-token")).thenReturn(false);
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUserId(anyString());
        verify(userDetailsService, never()).createUserDetailsFromJwt(anyString(), any(), anyString());
    }

    private MockHttpServletRequest authorizedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/v1/dashboard/company");
        return request;
    }
}
