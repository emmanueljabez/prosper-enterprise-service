package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.B2BDemoRequestDto;
import com.prosper.prospermentor.dto.CreateB2BDemoRequestRequest;
import com.prosper.prospermentor.entity.B2BDemoRequest;
import com.prosper.prospermentor.repository.B2BDemoRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class B2BDemoRequestService {

    private static final String DEFAULT_SOURCE_PAGE = "enterprise-pricing";

    private final B2BDemoRequestRepository repository;

    public B2BDemoRequestDto createRequest(CreateB2BDemoRequestRequest request) {
        B2BDemoRequest demoRequest = new B2BDemoRequest();
        demoRequest.setFullName(cleanRequired(request.getFullName()));
        demoRequest.setWorkEmail(cleanRequired(request.getWorkEmail()).toLowerCase(Locale.ROOT));
        demoRequest.setOrganisation(cleanRequired(request.getOrganisation()));
        demoRequest.setPhoneNumber(cleanOptional(request.getPhoneNumber()));
        demoRequest.setPartnershipType(cleanOptional(request.getPartnershipType()));
        demoRequest.setCohortSize(cleanOptional(request.getCohortSize()));
        demoRequest.setTimeline(cleanOptional(request.getTimeline()));
        demoRequest.setDetails(cleanOptional(request.getDetails()));
        demoRequest.setSourcePage(cleanOptional(request.getSourcePage()));
        if (demoRequest.getSourcePage() == null) {
            demoRequest.setSourcePage(DEFAULT_SOURCE_PAGE);
        }
        demoRequest.setStatus(B2BDemoRequest.DemoRequestStatus.NEW);

        return toDto(repository.save(demoRequest));
    }

    @Transactional(readOnly = true)
    public Page<B2BDemoRequestDto> listRequests(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDto);
    }

    private B2BDemoRequestDto toDto(B2BDemoRequest demoRequest) {
        return B2BDemoRequestDto.builder()
                .id(demoRequest.getId())
                .fullName(demoRequest.getFullName())
                .workEmail(demoRequest.getWorkEmail())
                .organisation(demoRequest.getOrganisation())
                .phoneNumber(demoRequest.getPhoneNumber())
                .partnershipType(demoRequest.getPartnershipType())
                .cohortSize(demoRequest.getCohortSize())
                .timeline(demoRequest.getTimeline())
                .details(demoRequest.getDetails())
                .status(demoRequest.getStatus() != null ? demoRequest.getStatus().name() : null)
                .sourcePage(demoRequest.getSourcePage())
                .createdAt(demoRequest.getCreatedAt())
                .updatedAt(demoRequest.getUpdatedAt())
                .build();
    }

    private String cleanRequired(String value) {
        return value.trim();
    }

    private String cleanOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
