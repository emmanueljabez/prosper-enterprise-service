package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyWalkthroughProgressDto;
import com.prosper.prospermentor.dto.UpdateCompanyWalkthroughProgressRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyWalkthroughProgress;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanyWalkthroughProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyWalkthroughProgressService {

    public static final String DEFAULT_VERSION = "2026-08-company-admin-v1";

    private final CompanyWalkthroughProgressRepository walkthroughProgressRepository;
    private final CompanyRepository companyRepository;
    private final ProfileService profileService;

    @Transactional(readOnly = true)
    public ApiResponse<CompanyWalkthroughProgressDto> getProgress(UUID companyId, UUID profileId, String version) {
        String normalizedVersion = normalizeVersion(version);
        Company company = requireCompany(companyId);
        Profile profile = requireProfileForCompany(companyId, profileId);

        return walkthroughProgressRepository.findByCompanyIdAndProfileIdAndVersion(companyId, profileId, normalizedVersion)
                .map(progress -> ApiResponse.success("Walkthrough progress retrieved successfully", toDto(progress)))
                .orElseGet(() -> ApiResponse.success(
                        "Walkthrough progress retrieved successfully",
                        defaultProgress(company, profile, normalizedVersion)
                ));
    }

    @Transactional
    public ApiResponse<CompanyWalkthroughProgressDto> updateProgress(
            UUID companyId,
            UUID profileId,
            UpdateCompanyWalkthroughProgressRequest request
    ) {
        String normalizedVersion = normalizeVersion(request != null ? request.getVersion() : null);
        Company company = requireCompany(companyId);
        Profile profile = requireProfileForCompany(companyId, profileId);

        CompanyWalkthroughProgress progress = walkthroughProgressRepository
                .findByCompanyIdAndProfileIdAndVersion(companyId, profileId, normalizedVersion)
                .orElseGet(() -> {
                    CompanyWalkthroughProgress created = new CompanyWalkthroughProgress();
                    created.setCompany(company);
                    created.setProfile(profile);
                    created.setVersion(normalizedVersion);
                    return created;
                });

        progress.setIntroDismissed(request != null && request.isIntroDismissed());
        progress.setCompletedTaskIds(normalizeIds(request != null ? request.getCompletedTaskIds() : List.of()));
        progress.setCompletedTourIds(normalizeIds(request != null ? request.getCompletedTourIds() : List.of()));
        progress.setLastSeenAt(LocalDateTime.now());

        CompanyWalkthroughProgress saved = walkthroughProgressRepository.save(progress);
        return ApiResponse.success("Walkthrough progress saved successfully", toDto(saved));
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    }

    private Profile requireProfileForCompany(UUID companyId, UUID profileId) {
        Profile profile = profileService.getProfileWithCompany(profileId)
                .orElseThrow(() -> new SecurityException("Not authorized to access this company"));

        UUID profileCompanyId = profile.getCompany() != null ? profile.getCompany().getId() : null;
        if (!companyId.equals(profileCompanyId)) {
            throw new SecurityException("Not authorized to access this company");
        }

        return profile;
    }

    private String normalizeVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return DEFAULT_VERSION;
        }
        return version.trim();
    }

    private List<String> normalizeIds(List<String> ids) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(normalized::add);
        }
        return new ArrayList<>(normalized);
    }

    private CompanyWalkthroughProgressDto defaultProgress(Company company, Profile profile, String version) {
        return CompanyWalkthroughProgressDto.builder()
                .companyId(company.getId())
                .profileId(profile.getId())
                .version(version)
                .introDismissed(false)
                .completedTaskIds(new ArrayList<>())
                .completedTourIds(new ArrayList<>())
                .build();
    }

    private CompanyWalkthroughProgressDto toDto(CompanyWalkthroughProgress progress) {
        return CompanyWalkthroughProgressDto.builder()
                .companyId(progress.getCompany().getId())
                .profileId(progress.getProfile().getId())
                .version(progress.getVersion())
                .introDismissed(progress.isIntroDismissed())
                .completedTaskIds(normalizeIds(progress.getCompletedTaskIds()))
                .completedTourIds(normalizeIds(progress.getCompletedTourIds()))
                .lastSeenAt(progress.getLastSeenAt())
                .createdAt(progress.getCreatedAt())
                .updatedAt(progress.getUpdatedAt())
                .build();
    }
}
