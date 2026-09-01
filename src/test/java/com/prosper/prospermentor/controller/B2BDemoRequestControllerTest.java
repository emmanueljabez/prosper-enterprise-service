package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.B2BDemoRequestDto;
import com.prosper.prospermentor.dto.CreateB2BDemoRequestRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.B2BDemoRequestService;
import com.prosper.prospermentor.service.B2BDemoRequestThrottleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class B2BDemoRequestControllerTest {

    @Mock
    private B2BDemoRequestService b2BDemoRequestService;
    @Mock
    private B2BDemoRequestThrottleService throttleService;

    @InjectMocks
    private B2BDemoRequestController controller;

    @Test
    void createRequest_shouldPersistDemoRequestAndReturnCreatedAcknowledgement() {
        CreateB2BDemoRequestRequest request = new CreateB2BDemoRequestRequest();
        request.setFullName("Ada Lovelace");
        request.setWorkEmail("ada@example.com");
        request.setOrganisation("Analytical Engines Ltd");
        request.setPhoneNumber("+254700000000");
        request.setPartnershipType("Corporate");
        request.setCohortSize("51-200");
        request.setTimeline("This quarter");
        request.setDetails("We want leadership mentorship for new managers.");

        B2BDemoRequestDto dto = B2BDemoRequestDto.builder()
                .id(UUID.randomUUID())
                .fullName("Ada Lovelace")
                .workEmail("ada@example.com")
                .organisation("Analytical Engines Ltd")
                .phoneNumber("+254700000000")
                .partnershipType("Corporate")
                .cohortSize("51-200")
                .timeline("This quarter")
                .details("We want leadership mentorship for new managers.")
                .status("NEW")
                .sourcePage("enterprise-pricing")
                .createdAt(Instant.now())
                .build();

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        when(b2BDemoRequestService.createRequest(request)).thenReturn(dto);

        ResponseEntity<ApiResponse<Void>> response = controller.createRequest(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("B2B demo request submitted successfully");
        verify(throttleService).assertAllowed("203.0.113.10", "ada@example.com");
    }

    @Test
    void listRequests_shouldReturnRecentDemoRequestsForAdminDashboard() {
        B2BDemoRequestDto dto = B2BDemoRequestDto.builder()
                .id(UUID.randomUUID())
                .fullName("Grace Hopper")
                .workEmail("grace@example.com")
                .organisation("Compiler Co")
                .partnershipType("Institution")
                .status("NEW")
                .sourcePage("enterprise-pricing")
                .createdAt(Instant.now())
                .build();

        when(b2BDemoRequestService.listRequests(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 50), 1));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.listRequests(0, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().get("requests")).isEqualTo(List.of(dto));
        assertThat(response.getBody().getData().get("pageSize")).isEqualTo(50);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(b2BDemoRequestService).listRequests(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void createRequest_shouldReturnTooManyRequestsWhenThrottleRejectsSubmission() {
        CreateB2BDemoRequestRequest request = new CreateB2BDemoRequestRequest();
        request.setFullName("Ada Lovelace");
        request.setWorkEmail("ada@example.com");
        request.setOrganisation("Analytical Engines Ltd");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("203.0.113.10");

        doThrow(new B2BDemoRequestThrottleService.TooManyDemoRequestsException("Too many demo requests"))
                .when(throttleService)
                .assertAllowed("203.0.113.10", "ada@example.com");

        ResponseEntity<ApiResponse<Void>> response = controller.createRequest(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Too many demo requests");
    }

    @Test
    void listRequests_shouldRejectUnboundedPageSize() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.listRequests(0, 500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("size must be between 1 and 100");
    }
}
