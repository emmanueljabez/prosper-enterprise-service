package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyChapterDto;
import com.prosper.prospermentor.dto.CompanyRegionDto;
import com.prosper.prospermentor.dto.CreateCompanyChapterRequest;
import com.prosper.prospermentor.dto.CreateCompanyRegionRequest;
import com.prosper.prospermentor.dto.UpdateCompanyChapterRequest;
import com.prosper.prospermentor.dto.UpdateCompanyRegionRequest;
import com.prosper.prospermentor.entity.CompanyChapter;
import com.prosper.prospermentor.entity.CompanyLocationStatus;
import com.prosper.prospermentor.entity.CompanyRegion;
import com.prosper.prospermentor.repository.CompanyChapterRepository;
import com.prosper.prospermentor.repository.CompanyRegionRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyLocationCatalogService {

    private final CompanyRepository companyRepository;
    private final CompanyRegionRepository regionRepository;
    private final CompanyChapterRepository chapterRepository;

    @Transactional(readOnly = true)
    public Page<CompanyRegionDto> getRegions(UUID companyId, String search, Pageable pageable) {
        ensureCompanyExists(companyId);
        Page<CompanyRegion> regions = regionRepository.findByCompanyIdWithFilters(
                companyId,
                normalizeSearch(search),
                pageable
        );

        Map<UUID, Long> chapterCounts = buildChapterCountMap(regions.getContent().stream()
                .map(CompanyRegion::getId)
                .toList());

        return regions.map(region -> toRegionDto(region, chapterCounts.getOrDefault(region.getId(), 0L)));
    }

    public CompanyRegionDto createRegion(UUID companyId,
                                         CreateCompanyRegionRequest request,
                                         UUID actorUserId) {
        ensureCompanyExists(companyId);

        String name = requireName(request.getName(), "Region name is required");
        String code = trimToNull(request.getCode());
        String description = trimToNull(request.getDescription());

        if (regionRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new IllegalArgumentException("A region with this name already exists");
        }

        if (code != null && regionRepository.existsByCompany_IdAndCodeIgnoreCase(companyId, code)) {
            throw new IllegalArgumentException("A region with this code already exists");
        }

        CompanyRegion region = new CompanyRegion();
        region.setCompany(companyRepository.getReferenceById(companyId));
        region.setName(name);
        region.setCode(code);
        region.setDescription(description);
        region.setIsActive(true);
        region.setStatus(CompanyLocationStatus.ACTIVE);
        region.setCreatedByUserId(actorUserId);

        return toRegionDto(regionRepository.save(region), 0L);
    }

    public CompanyRegionDto updateRegion(UUID companyId,
                                         UUID regionId,
                                         UpdateCompanyRegionRequest request) {
        CompanyRegion region = getRegionForCompany(companyId, regionId);

        if (request.getName() != null) {
            String name = requireName(request.getName(), "Region name is required");
            if (regionRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, regionId)) {
                throw new IllegalArgumentException("A region with this name already exists");
            }
            region.setName(name);
        }

        if (request.getCode() != null) {
            String code = trimToNull(request.getCode());
            if (code != null && regionRepository.existsByCompany_IdAndCodeIgnoreCaseAndIdNot(companyId, code, regionId)) {
                throw new IllegalArgumentException("A region with this code already exists");
            }
            region.setCode(code);
        }

        if (request.getDescription() != null) {
            region.setDescription(trimToNull(request.getDescription()));
        }

        if (request.getIsActive() != null) {
            region.setIsActive(request.getIsActive());
            region.setStatus(request.getIsActive() ? CompanyLocationStatus.ACTIVE : CompanyLocationStatus.INACTIVE);
        }

        long chapterCount = chapterRepository.findByCompanyIdWithFilters(companyId, regionId, "", Pageable.unpaged())
                .getTotalElements();
        return toRegionDto(regionRepository.save(region), chapterCount);
    }

    public void deleteRegion(UUID companyId, UUID regionId) {
        regionRepository.delete(getRegionForCompany(companyId, regionId));
    }

    @Transactional(readOnly = true)
    public Page<CompanyChapterDto> getChapters(UUID companyId, UUID regionId, String search, Pageable pageable) {
        ensureCompanyExists(companyId);
        if (regionId != null) {
            getRegionForCompany(companyId, regionId);
        }
        return chapterRepository.findByCompanyIdWithFilters(companyId, regionId, normalizeSearch(search), pageable)
                .map(this::toChapterDto);
    }

    public CompanyChapterDto createChapter(UUID companyId,
                                           CreateCompanyChapterRequest request,
                                           UUID actorUserId) {
        ensureCompanyExists(companyId);

        String name = requireName(request.getName(), "Chapter name is required");
        String code = trimToNull(request.getCode());
        String description = trimToNull(request.getDescription());
        CompanyRegion region = resolveRegion(companyId, request.getRegionId());

        if (chapterRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new IllegalArgumentException("A chapter with this name already exists");
        }

        if (code != null && chapterRepository.existsByCompany_IdAndCodeIgnoreCase(companyId, code)) {
            throw new IllegalArgumentException("A chapter with this code already exists");
        }

        CompanyChapter chapter = new CompanyChapter();
        chapter.setCompany(companyRepository.getReferenceById(companyId));
        chapter.setRegion(region);
        chapter.setName(name);
        chapter.setCode(code);
        chapter.setDescription(description);
        chapter.setIsActive(true);
        chapter.setStatus(CompanyLocationStatus.ACTIVE);
        chapter.setCreatedByUserId(actorUserId);

        return toChapterDto(chapterRepository.save(chapter));
    }

    public CompanyChapterDto updateChapter(UUID companyId,
                                           UUID chapterId,
                                           UpdateCompanyChapterRequest request) {
        CompanyChapter chapter = getChapterForCompany(companyId, chapterId);

        if (request.getName() != null) {
            String name = requireName(request.getName(), "Chapter name is required");
            if (chapterRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, chapterId)) {
                throw new IllegalArgumentException("A chapter with this name already exists");
            }
            chapter.setName(name);
        }

        if (request.getCode() != null) {
            String code = trimToNull(request.getCode());
            if (code != null && chapterRepository.existsByCompany_IdAndCodeIgnoreCaseAndIdNot(companyId, code, chapterId)) {
                throw new IllegalArgumentException("A chapter with this code already exists");
            }
            chapter.setCode(code);
        }

        if (request.getDescription() != null) {
            chapter.setDescription(trimToNull(request.getDescription()));
        }

        chapter.setRegion(resolveRegion(companyId, request.getRegionId()));

        if (request.getIsActive() != null) {
            chapter.setIsActive(request.getIsActive());
            chapter.setStatus(request.getIsActive() ? CompanyLocationStatus.ACTIVE : CompanyLocationStatus.INACTIVE);
        }

        return toChapterDto(chapterRepository.save(chapter));
    }

    public void deleteChapter(UUID companyId, UUID chapterId) {
        chapterRepository.delete(getChapterForCompany(companyId, chapterId));
    }

    @Transactional(readOnly = true)
    public CompanyRegion getRegionForCompany(UUID companyId, UUID regionId) {
        return regionRepository.findByIdAndCompany_Id(regionId, companyId)
                .orElseThrow(() -> new NoSuchElementException("Region not found"));
    }

    @Transactional(readOnly = true)
    public CompanyChapter getChapterForCompany(UUID companyId, UUID chapterId) {
        return chapterRepository.findByIdAndCompany_Id(chapterId, companyId)
                .orElseThrow(() -> new NoSuchElementException("Chapter not found"));
    }

    private CompanyRegion resolveRegion(UUID companyId, UUID regionId) {
        if (regionId == null) {
            return null;
        }
        return regionRepository.findByIdAndCompany_Id(regionId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Region not found for this company"));
    }

    private Map<UUID, Long> buildChapterCountMap(List<UUID> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : regionRepository.countChaptersByRegionIds(regionIds)) {
            if (row.length < 2 || row[0] == null || row[1] == null) continue;
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new NoSuchElementException("Company not found");
        }
    }

    private String normalizeSearch(String search) {
        return search != null && !search.trim().isBlank() ? search.trim() : "";
    }

    private String requireName(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CompanyRegionDto toRegionDto(CompanyRegion region, long chapterCount) {
        return CompanyRegionDto.builder()
                .id(region.getId())
                .companyId(region.getCompany() != null ? region.getCompany().getId() : null)
                .name(region.getName())
                .code(region.getCode())
                .description(region.getDescription())
                .status(region.getStatus())
                .isActive(Boolean.TRUE.equals(region.getIsActive()))
                .chapterCount(chapterCount)
                .createdAt(region.getCreatedAt())
                .updatedAt(region.getUpdatedAt())
                .build();
    }

    private CompanyChapterDto toChapterDto(CompanyChapter chapter) {
        CompanyRegion region = chapter.getRegion();
        return CompanyChapterDto.builder()
                .id(chapter.getId())
                .companyId(chapter.getCompany() != null ? chapter.getCompany().getId() : null)
                .regionId(region != null ? region.getId() : null)
                .regionName(region != null ? region.getName() : null)
                .name(chapter.getName())
                .code(chapter.getCode())
                .description(chapter.getDescription())
                .status(chapter.getStatus())
                .isActive(Boolean.TRUE.equals(chapter.getIsActive()))
                .createdAt(chapter.getCreatedAt())
                .updatedAt(chapter.getUpdatedAt())
                .build();
    }
}
