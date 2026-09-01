package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.B2BDemoRequestDto;
import com.prosper.prospermentor.dto.CreateB2BDemoRequestRequest;
import com.prosper.prospermentor.entity.B2BDemoRequest;
import com.prosper.prospermentor.repository.B2BDemoRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class B2BDemoRequestServiceTest {

    @Mock
    private B2BDemoRequestRepository repository;

    @InjectMocks
    private B2BDemoRequestService service;

    @Test
    void createRequest_shouldNormalizeContactFieldsAndDefaultStatus() {
        CreateB2BDemoRequestRequest request = new CreateB2BDemoRequestRequest();
        request.setFullName("  Ada Lovelace  ");
        request.setWorkEmail("  ADA@EXAMPLE.COM  ");
        request.setOrganisation("  Analytical Engines Ltd  ");
        request.setPhoneNumber("  +254700000000  ");
        request.setPartnershipType("  Corporate  ");
        request.setCohortSize("  51-200  ");
        request.setTimeline("  This quarter  ");
        request.setDetails("  We want leadership mentorship.  ");

        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        when(repository.save(any(B2BDemoRequest.class))).thenAnswer(invocation -> {
            B2BDemoRequest saved = invocation.getArgument(0);
            saved.setId(id);
            saved.setCreatedAt(createdAt);
            saved.setUpdatedAt(createdAt);
            return saved;
        });

        B2BDemoRequestDto dto = service.createRequest(request);

        ArgumentCaptor<B2BDemoRequest> captor = ArgumentCaptor.forClass(B2BDemoRequest.class);
        verify(repository).save(captor.capture());
        B2BDemoRequest entity = captor.getValue();

        assertThat(entity.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(entity.getWorkEmail()).isEqualTo("ada@example.com");
        assertThat(entity.getOrganisation()).isEqualTo("Analytical Engines Ltd");
        assertThat(entity.getPhoneNumber()).isEqualTo("+254700000000");
        assertThat(entity.getPartnershipType()).isEqualTo("Corporate");
        assertThat(entity.getCohortSize()).isEqualTo("51-200");
        assertThat(entity.getTimeline()).isEqualTo("This quarter");
        assertThat(entity.getDetails()).isEqualTo("We want leadership mentorship.");
        assertThat(entity.getStatus()).isEqualTo(B2BDemoRequest.DemoRequestStatus.NEW);
        assertThat(entity.getSourcePage()).isEqualTo("enterprise-pricing");

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getWorkEmail()).isEqualTo("ada@example.com");
        assertThat(dto.getStatus()).isEqualTo("NEW");
    }
}
