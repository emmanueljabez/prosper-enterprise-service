package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyJoinLinkDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyJoinLink;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyJoinLinkRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyJoinLinkServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PROFILE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyJoinLinkRepository joinLinkRepository;
    @Mock
    private ProfileRepository profileRepository;

    private CompanyJoinLinkService service;

    @BeforeEach
    void setUp() {
        service = new CompanyJoinLinkService(companyRepository, joinLinkRepository, profileRepository);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(service, "joinLinkSigningSecret", "test-join-link-secret-with-enough-length");
    }

    @Test
    void getOrCreateJoinLink_shouldCreateSignedReusableJoinUrl() {
        AtomicReference<CompanyJoinLink> savedLink = new AtomicReference<>();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(joinLinkRepository.findFirstByCompanyIdAndStatus(COMPANY_ID, CompanyJoinLink.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(joinLinkRepository.save(any(CompanyJoinLink.class))).thenAnswer(invocation -> {
            CompanyJoinLink link = invocation.getArgument(0);
            savedLink.set(link);
            return link;
        });

        CompanyJoinLinkDto dto = service.getOrCreateJoinLink(COMPANY_ID, ADMIN_ID);

        assertThat(dto.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(dto.getCompanyName()).isEqualTo("Girls for Girls");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getJoinUrl()).startsWith("https://enterprise.prospermentor.com/auth/signup?audience=mentee&companyJoinToken=");
        assertThat(dto.getJoinToken()).contains(".");
        assertThat(savedLink.get().getTokenHash()).isNotBlank();
        assertThat(savedLink.get().getTokenHash()).doesNotContain(dto.getJoinToken());
        assertThat(savedLink.get().getTokenNonce()).isNotBlank();
    }

    @Test
    void regenerateJoinLink_shouldRevokeActiveLinkAndCreateReplacement() {
        CompanyJoinLink existing = activeLink(company());
        AtomicReference<CompanyJoinLink> replacement = new AtomicReference<>();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(joinLinkRepository.findFirstByCompanyIdAndStatus(COMPANY_ID, CompanyJoinLink.Status.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(joinLinkRepository.save(any(CompanyJoinLink.class))).thenAnswer(invocation -> {
            CompanyJoinLink link = invocation.getArgument(0);
            if (link.getStatus() == CompanyJoinLink.Status.ACTIVE) {
                replacement.set(link);
            }
            return link;
        });

        CompanyJoinLinkDto dto = service.regenerateJoinLink(COMPANY_ID, ADMIN_ID);

        assertThat(existing.getStatus()).isEqualTo(CompanyJoinLink.Status.REVOKED);
        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getRevokedByProfileId()).isEqualTo(ADMIN_ID);
        assertThat(replacement.get()).isNotNull();
        assertThat(replacement.get().getStatus()).isEqualTo(CompanyJoinLink.Status.ACTIVE);
        assertThat(dto.getJoinToken()).isNotBlank();
        assertThat(dto.getJoinToken()).isNotEqualTo(service.toJoinToken(existing));
    }

    @Test
    void completeJoinAfterVerification_shouldLinkProfileToCompanyAndRecordLastUsed() {
        AtomicReference<CompanyJoinLink> savedLink = new AtomicReference<>();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(joinLinkRepository.findFirstByCompanyIdAndStatus(COMPANY_ID, CompanyJoinLink.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(joinLinkRepository.save(any(CompanyJoinLink.class))).thenAnswer(invocation -> {
            CompanyJoinLink link = invocation.getArgument(0);
            savedLink.set(link);
            return link;
        });

        CompanyJoinLinkDto generated = service.getOrCreateJoinLink(COMPANY_ID, ADMIN_ID);
        when(joinLinkRepository.findByIdAndStatus(savedLink.get().getId(), CompanyJoinLink.Status.ACTIVE))
                .thenReturn(Optional.of(savedLink.get()));
        when(profileRepository.updateCompanyId(eq(PROFILE_ID), eq(COMPANY_ID), any()))
                .thenReturn(1);

        CompanyJoinLinkDto completed = service.completeJoinAfterVerification(
                generated.getJoinToken(),
                PROFILE_ID,
                "mentee@example.com"
        );

        assertThat(completed.isLinked()).isTrue();
        assertThat(completed.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(completed.getCompanyName()).isEqualTo("Girls for Girls");
        assertThat(savedLink.get().getLastUsedAt()).isNotNull();
        verify(profileRepository).updateCompanyId(eq(PROFILE_ID), eq(COMPANY_ID), any());
    }

    @Test
    void completeJoinAfterVerification_shouldRejectInvalidToken() {
        assertThatThrownBy(() -> service.completeJoinAfterVerification("not-a-valid-token", PROFILE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_COMPANY_JOIN_TOKEN");
    }

    private Company company() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Girls for Girls");
        company.setEmailAddress("gloria@example.com");
        company.setPhoneNumber("+254700000000");
        return company;
    }

    private CompanyJoinLink activeLink(Company company) {
        CompanyJoinLink link = new CompanyJoinLink();
        link.setId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        link.setCompany(company);
        link.setTokenNonce("existing-nonce");
        link.setTokenHash("existing-token-hash");
        link.setStatus(CompanyJoinLink.Status.ACTIVE);
        link.setCreatedByProfileId(ADMIN_ID);
        link.setCreatedAt(LocalDateTime.now().minusDays(1));
        link.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return link;
    }

    private String queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String part : query.split("&")) {
            String[] pieces = part.split("=", 2);
            if (URLDecoder.decode(pieces[0], StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pieces.length > 1 ? pieces[1] : "", StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
