package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.service.B2BDemoRequestService;
import com.prosper.prospermentor.service.B2BDemoRequestThrottleService;
import com.prosper.prospermentor.security.ES256JwtUtil;
import com.prosper.prospermentor.security.JwtUtil;
import com.prosper.prospermentor.security.SupabaseUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = B2BDemoRequestController.class)
@AutoConfigureMockMvc
@Import(B2BDemoRequestControllerSecurityTest.MethodSecurityConfig.class)
class B2BDemoRequestControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private B2BDemoRequestService b2BDemoRequestService;

    @MockBean
    private B2BDemoRequestThrottleService throttleService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private ES256JwtUtil es256JwtUtil;

    @MockBean
    private SupabaseUserDetailsService userDetailsService;

    @Test
    void listRequests_shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/admin/b2b-demo-requests")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(b2BDemoRequestService);
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void listRequests_shouldRejectNonAdminRequests() throws Exception {
        mockMvc.perform(get("/api/admin/b2b-demo-requests")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(b2BDemoRequestService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listRequests_shouldAllowAdminRequests() throws Exception {
        when(b2BDemoRequestService.listRequests(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/api/admin/b2b-demo-requests")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requests").isArray())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {
    }
}
