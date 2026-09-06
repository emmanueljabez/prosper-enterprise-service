package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class CompanyBrandingService {

    private static final long MAX_COMPANY_LOGO_BYTES = 5L * 1024L * 1024L;
    private static final String LOGO_URL_PREFIX_TEMPLATE = "/api/v1/companies/%s/branding/logo/";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif",
            "image/svg+xml"
    );
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/svg+xml", ".svg"
    );

    private final CompanyRepository companyRepository;
    private final Path logoDirectory;

    public CompanyBrandingService(CompanyRepository companyRepository) {
        this(
                companyRepository,
                Paths.get(System.getProperty("prosper.company-logo-dir", "uploads/company-logos"))
                        .toAbsolutePath()
                        .normalize()
        );
    }

    CompanyBrandingService(CompanyRepository companyRepository, Path logoDirectory) {
        this.companyRepository = companyRepository;
        this.logoDirectory = logoDirectory.toAbsolutePath().normalize();
    }

    public ApiResponse<Company> uploadLogo(UUID companyId, MultipartFile file) {
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        String validationError = validateLogoFile(file);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }

        Company company = companyOpt.get();
        String previousLogoUrl = company.getLogoUrl();
        String originalFilename = normalizeUploadedFilename(file.getOriginalFilename());
        String extension = resolveLogoExtension(file, originalFilename);
        String storedFilename = companyId + "-" + UUID.randomUUID() + extension;
        Path target = logoDirectory.resolve(storedFilename).normalize();

        if (!target.startsWith(logoDirectory)) {
            return ApiResponse.error("Invalid logo file name");
        }

        try {
            Files.createDirectories(logoDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            company.setLogoUrl(buildLogoUrl(companyId, storedFilename));
            Company savedCompany = companyRepository.save(company);
            deleteManagedLogo(previousLogoUrl, companyId);

            return ApiResponse.success("Company logo uploaded successfully", savedCompany);
        } catch (IOException e) {
            log.error("Failed to upload company logo for company {}: {}", companyId, e.getMessage(), e);
            return ApiResponse.error("Logo upload failed");
        }
    }

    public ApiResponse<Company> deleteLogo(UUID companyId) {
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        String previousLogoUrl = company.getLogoUrl();
        company.setLogoUrl(null);
        Company savedCompany = companyRepository.save(company);
        deleteManagedLogo(previousLogoUrl, companyId);

        return ApiResponse.success("Company logo removed successfully", savedCompany);
    }

    @Transactional(readOnly = true)
    public Optional<StoredLogoResource> getStoredLogo(UUID companyId, String filename) {
        String normalizedFilename = normalizeUploadedFilename(filename);
        if (!normalizedFilename.startsWith(companyId + "-")) {
            return Optional.empty();
        }

        Path logoPath = logoDirectory.resolve(normalizedFilename).normalize();
        if (!logoPath.startsWith(logoDirectory) || !Files.exists(logoPath)) {
            return Optional.empty();
        }

        String contentType;
        try {
            contentType = Files.probeContentType(logoPath);
        } catch (IOException e) {
            contentType = null;
        }

        return Optional.of(new StoredLogoResource(
                logoPath,
                normalizedFilename,
                contentType != null ? contentType : "application/octet-stream"
        ));
    }

    private String validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Please upload a non-empty logo image";
        }

        if (file.getSize() > MAX_COMPANY_LOGO_BYTES) {
            return "Company logo image must not exceed 5 MB";
        }

        String contentType = String.valueOf(file.getContentType() == null ? "" : file.getContentType()).toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return "Company logo must be a PNG, JPG, GIF, WebP, or SVG image";
        }

        return null;
    }

    private String resolveLogoExtension(MultipartFile file, String originalFilename) {
        String contentType = String.valueOf(file.getContentType() == null ? "" : file.getContentType()).toLowerCase();
        String contentTypeExtension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (contentTypeExtension != null) {
            return contentTypeExtension;
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex).toLowerCase() : ".png";
    }

    private String buildLogoUrl(UUID companyId, String storedFilename) {
        return String.format(LOGO_URL_PREFIX_TEMPLATE, companyId) + storedFilename;
    }

    private void deleteManagedLogo(String logoUrl, UUID companyId) {
        managedLogoFilename(logoUrl, companyId).ifPresent(filename -> {
            Path logoPath = logoDirectory.resolve(filename).normalize();
            if (!logoPath.startsWith(logoDirectory)) {
                return;
            }

            try {
                Files.deleteIfExists(logoPath);
            } catch (IOException e) {
                log.warn("Failed to delete previous company logo {}: {}", filename, e.getMessage());
            }
        });
    }

    private Optional<String> managedLogoFilename(String logoUrl, UUID companyId) {
        String expectedPrefix = String.format(LOGO_URL_PREFIX_TEMPLATE, companyId);
        if (logoUrl == null || !logoUrl.startsWith(expectedPrefix)) {
            return Optional.empty();
        }

        String filename = normalizeUploadedFilename(logoUrl.substring(expectedPrefix.length()));
        return Optional.of(filename);
    }

    private String normalizeUploadedFilename(String value) {
        String filename = String.valueOf(value == null ? "company-logo" : value).replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }

        filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            return "company-logo";
        }
        return filename;
    }

    public record StoredLogoResource(Path path, String filename, String contentType) {
    }
}
