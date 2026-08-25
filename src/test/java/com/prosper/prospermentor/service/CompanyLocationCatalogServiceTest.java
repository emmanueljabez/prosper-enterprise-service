package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyChapterDto;
import com.prosper.prospermentor.dto.CompanyRegionDto;
import com.prosper.prospermentor.dto.CreateCompanyChapterRequest;
import com.prosper.prospermentor.dto.CreateCompanyRegionRequest;
import com.prosper.prospermentor.dto.UpdateCompanyChapterRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyChapter;
import com.prosper.prospermentor.entity.CompanyRegion;
import com.prosper.prospermentor.repository.CompanyChapterRepository;
import com.prosper.prospermentor.repository.CompanyRegionRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyLocationCatalogServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyRegionRepository regionRepository;
    @Mock
    private CompanyChapterRepository chapterRepository;

    @InjectMocks
    private CompanyLocationCatalogService service;

    @Test
    void createRegion_shouldNormalizeAndPersistCompanyScopedRegion() {
        UUID companyId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Company company = new Company();
        company.setId(companyId);

        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);
        when(regionRepository.existsByCompany_IdAndNameIgnoreCase(companyId, "Kenya")).thenReturn(false);
        when(regionRepository.existsByCompany_IdAndCodeIgnoreCase(companyId, "KE")).thenReturn(false);
        when(regionRepository.save(any(CompanyRegion.class))).thenAnswer(invocation -> {
            CompanyRegion region = invocation.getArgument(0);
            region.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
            return region;
        });

        CompanyRegionDto dto = service.createRegion(companyId, CreateCompanyRegionRequest.builder()
                .name(" Kenya ")
                .code(" KE ")
                .description(" Country rollout ")
                .build(), userId);

        assertThat(dto.getCompanyId()).isEqualTo(companyId);
        assertThat(dto.getName()).isEqualTo("Kenya");
        assertThat(dto.getCode()).isEqualTo("KE");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void createRegion_shouldRejectDuplicateNameWithinCompany() {
        UUID companyId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(regionRepository.existsByCompany_IdAndNameIgnoreCase(companyId, "Kenya")).thenReturn(true);

        assertThatThrownBy(() -> service.createRegion(companyId, CreateCompanyRegionRequest.builder()
                .name("Kenya")
                .build(), UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region with this name already exists");
    }

    @Test
    void createChapter_shouldLinkOnlyToRegionOwnedBySameCompany() {
        UUID companyId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID regionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Company company = new Company();
        company.setId(companyId);
        CompanyRegion region = new CompanyRegion();
        region.setId(regionId);
        region.setCompany(company);
        region.setName("Kenya");

        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);
        when(regionRepository.findByIdAndCompany_Id(regionId, companyId)).thenReturn(Optional.of(region));
        when(chapterRepository.existsByCompany_IdAndNameIgnoreCase(companyId, "Nairobi")).thenReturn(false);
        when(chapterRepository.existsByCompany_IdAndCodeIgnoreCase(companyId, "NBO")).thenReturn(false);
        when(chapterRepository.save(any(CompanyChapter.class))).thenAnswer(invocation -> {
            CompanyChapter chapter = invocation.getArgument(0);
            chapter.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
            return chapter;
        });

        CompanyChapterDto dto = service.createChapter(companyId, CreateCompanyChapterRequest.builder()
                .name(" Nairobi ")
                .code(" NBO ")
                .regionId(regionId)
                .description(" City chapter ")
                .build(), userId);

        assertThat(dto.getCompanyId()).isEqualTo(companyId);
        assertThat(dto.getName()).isEqualTo("Nairobi");
        assertThat(dto.getCode()).isEqualTo("NBO");
        assertThat(dto.getRegionId()).isEqualTo(regionId);
        assertThat(dto.getRegionName()).isEqualTo("Kenya");
    }

    @Test
    void createChapter_shouldRejectRegionOutsideCompany() {
        UUID companyId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID regionId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        when(companyRepository.existsById(companyId)).thenReturn(true);
        when(regionRepository.findByIdAndCompany_Id(regionId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createChapter(companyId, CreateCompanyChapterRequest.builder()
                .name("Nairobi")
                .regionId(regionId)
                .build(), UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Region not found for this company");
    }

    @Test
    void updateChapter_shouldClearRegionWhenRegionIdIsNull() {
        UUID companyId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID chapterId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Company company = new Company();
        company.setId(companyId);
        CompanyRegion region = new CompanyRegion();
        region.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        region.setCompany(company);
        region.setName("Kenya");
        CompanyChapter chapter = new CompanyChapter();
        chapter.setId(chapterId);
        chapter.setCompany(company);
        chapter.setRegion(region);
        chapter.setName("Nairobi");
        chapter.setCode("NBO");

        when(chapterRepository.findByIdAndCompany_Id(chapterId, companyId)).thenReturn(Optional.of(chapter));
        when(chapterRepository.save(any(CompanyChapter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyChapterDto dto = service.updateChapter(companyId, chapterId, UpdateCompanyChapterRequest.builder()
                .name("Nairobi")
                .code("NBO")
                .regionId(null)
                .isActive(true)
                .build());

        assertThat(dto.getRegionId()).isNull();
        assertThat(dto.getRegionName()).isNull();
    }
}
