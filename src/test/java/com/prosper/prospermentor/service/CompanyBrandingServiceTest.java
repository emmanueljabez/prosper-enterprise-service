package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyBrandingServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private CompanyRepository companyRepository;

    @TempDir
    private Path logoDirectory;

    private CompanyBrandingService companyBrandingService;
    private Company company;

    @BeforeEach
    void setUp() {
        companyBrandingService = new CompanyBrandingService(companyRepository, logoDirectory);
        company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Kenya Airways");
        company.setEmailAddress("admin@kenya-airways.test");
        company.setPhoneNumber("+254700000000");
    }

    @Test
    void uploadLogo_shouldStoreImageAndReplaceExistingManagedLogo() throws Exception {
        String previousLogoFilename = "previous-logo.png";
        Files.write(logoDirectory.resolve(previousLogoFilename), new byte[]{9, 8, 7});
        company.setLogoUrl("/api/v1/companies/" + COMPANY_ID + "/branding/logo/" + previousLogoFilename);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "company logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<Company> response = companyBrandingService.uploadLogo(COMPANY_ID, file);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(company);
        assertThat(company.getLogoUrl())
                .startsWith("/api/v1/companies/" + COMPANY_ID + "/branding/logo/")
                .endsWith(".png");

        String storedFilename = company.getLogoUrl().substring(company.getLogoUrl().lastIndexOf('/') + 1);
        Path storedFile = logoDirectory.resolve(storedFilename);
        assertThat(Files.readAllBytes(storedFile)).containsExactly(1, 2, 3);
        assertThat(Files.exists(logoDirectory.resolve(previousLogoFilename))).isFalse();
    }

    @Test
    void uploadLogo_shouldRejectNonImageFilesWithoutSaving() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "company.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        ApiResponse<Company> response = companyBrandingService.uploadLogo(COMPANY_ID, file);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("image");
        assertThat(company.getLogoUrl()).isNull();
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void constructor_shouldDeclareAutowiredConstructorForSpringContext() throws Exception {
        Constructor<CompanyBrandingService> constructor = CompanyBrandingService.class.getConstructor(CompanyRepository.class);

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void deleteLogo_shouldClearLogoUrlAndRemoveManagedFile() throws Exception {
        String storedLogoFilename = "stored-logo.webp";
        Files.write(logoDirectory.resolve(storedLogoFilename), new byte[]{4, 5, 6});
        company.setLogoUrl("/api/v1/companies/" + COMPANY_ID + "/branding/logo/" + storedLogoFilename);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<Company> response = companyBrandingService.deleteLogo(COMPANY_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(company);
        assertThat(company.getLogoUrl()).isNull();
        assertThat(Files.exists(logoDirectory.resolve(storedLogoFilename))).isFalse();
    }
}
