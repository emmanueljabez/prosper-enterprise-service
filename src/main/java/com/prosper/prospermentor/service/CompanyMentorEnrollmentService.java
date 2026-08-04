package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorInvitation;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.CompanyMentorProgramScope;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyMentorInvitationRepository;
import com.prosper.prospermentor.repository.CompanyMentorPoolMembershipRepository;
import com.prosper.prospermentor.repository.CompanyMentorProgramScopeRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.CompanyMentorNotificationService;
import com.prosper.prospermentor.util.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyMentorEnrollmentService {

    private static final int INVITATION_TTL_DAYS = 7;
    private static final Set<CompanyMentorInvitation.InvitationStatus> OPEN_INVITATION_STATUSES = Set.of(
            CompanyMentorInvitation.InvitationStatus.DRAFT,
            CompanyMentorInvitation.InvitationStatus.SENT,
            CompanyMentorInvitation.InvitationStatus.FAILED_DELIVERY
    );
    private static final Set<CompanyMentorPoolMembership.MembershipStatus> LIVE_MEMBERSHIP_STATUSES = Set.of(
            CompanyMentorPoolMembership.MembershipStatus.PENDING_INVITE,
            CompanyMentorPoolMembership.MembershipStatus.ACTIVE,
            CompanyMentorPoolMembership.MembershipStatus.SUSPENDED
    );

    private final CompanyRepository companyRepository;
    private final ProfileRepository profileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final CompanyMentorInvitationRepository invitationRepository;
    private final CompanyMentorPoolMembershipRepository membershipRepository;
    private final CompanyMentorProgramScopeRepository programScopeRepository;
    private final CompanyProgramRepository companyProgramRepository;
    private final CompanyMentorNotificationService notificationService;

    public CompanyMentorDtos.InvitationDto inviteMentor(UUID companyId,
                                                        CompanyMentorDtos.InviteRequest request,
                                                        UUID invitedByUserId) {
        Company company = getCompany(companyId);
        String email = normalizeEmail(request.getEmail());
        String phone = normalizeRequiredPhone(request.getPhone());

        if (invitationRepository.existsByCompany_IdAndEmailIgnoreCaseAndStatusIn(companyId, email, OPEN_INVITATION_STATUSES)) {
            throw new IllegalArgumentException("An open invite already exists for this email");
        }

        if (invitationRepository.existsByCompany_IdAndPhoneAndStatusIn(companyId, phone, OPEN_INVITATION_STATUSES)) {
            throw new IllegalArgumentException("An open invite already exists for this phone");
        }

        profileRepository.findByEmailIgnoreCase(email)
                .ifPresent(profile -> {
                    if (membershipRepository.existsByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
                            companyId,
                            profile.getId(),
                            LIVE_MEMBERSHIP_STATUSES
                    )) {
                        throw new IllegalArgumentException("Mentor is already in this company pool");
                    }
                });

        CompanyMentorInvitation invitation = new CompanyMentorInvitation();
        invitation.setCompany(company);
        invitation.setEmail(email);
        invitation.setPhone(phone);
        invitation.setFirstName(normalizeNullable(request.getFirstName()));
        invitation.setLastName(normalizeNullable(request.getLastName()));
        invitation.setTitle(normalizeNullable(request.getTitle()));
        invitation.setDepartment(normalizeNullable(request.getDepartment()));
        invitation.setTags(normalizeTags(request.getTags()));
        invitation.setDefaultVisibility(resolveVisibility(request.getDefaultVisibility()));
        invitation.setProgramOrCohortReference(resolveProgramOrCohortReference(request));
        invitation.setInvitedByUserId(invitedByUserId);

        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_TTL_DAYS);
        invitation.setInvitationTokenHash(hashToken(rawToken));
        invitation.setInvitationTokenExpiresAt(expiresAt);

        CompanyMentorInvitation saved = invitationRepository.save(invitation);
        applyDeliveryAttempt(saved, notificationService.sendMentorInvitation(company, email, phone, rawToken, expiresAt));
        return toInvitationDto(invitationRepository.save(saved));
    }

    @Transactional(readOnly = true)
    public CompanyMentorDtos.ImportValidationResponse validateImport(UUID companyId, MultipartFile file) {
        Company company = getCompany(companyId);
        List<CompanyMentorDtos.ImportRowResult> rows = parseImportRows(company, file);
        List<CompanyMentorDtos.ImportRowError> errors = rows.stream()
                .flatMap(row -> row.getErrors().stream())
                .toList();

        return CompanyMentorDtos.ImportValidationResponse.builder()
                .valid(errors.isEmpty())
                .totalRows(rows.size())
                .validRows((int) rows.stream().filter(row -> row.getErrors().isEmpty()).count())
                .errorRows((int) rows.stream().filter(row -> !row.getErrors().isEmpty()).count())
                .rows(rows)
                .errors(errors)
                .build();
    }

    public CompanyMentorDtos.ImportValidationResponse importMentors(UUID companyId, MultipartFile file, UUID invitedByUserId) {
        CompanyMentorDtos.ImportValidationResponse validation = validateImport(companyId, file);
        if (!validation.isValid()) {
            return validation;
        }

        for (CompanyMentorDtos.ImportRowResult row : validation.getRows()) {
            inviteMentor(
                    companyId,
                    CompanyMentorDtos.InviteRequest.builder()
                            .email(row.getEmail())
                            .phone(row.getPhone())
                            .firstName(row.getFirstName())
                            .lastName(row.getLastName())
                            .title(row.getTitle())
                            .department(row.getDepartment())
                            .tags(row.getTags())
                            .defaultVisibility(row.getVisibility())
                            .cohortReference(row.getProgramOrCohortReference())
                            .build(),
                    invitedByUserId
            );
        }
        return validation;
    }

    public CompanyMentorDtos.InvitationDto resendInvitation(UUID companyId, UUID invitationId) {
        CompanyMentorInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new NoSuchElementException("Invitation not found"));

        if (invitation.getCompany() == null || !companyId.equals(invitation.getCompany().getId())) {
            throw new SecurityException("Invitation does not belong to this company");
        }
        if (invitation.getStatus() == CompanyMentorInvitation.InvitationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Invitation has already been accepted");
        }
        if (invitation.getStatus() == CompanyMentorInvitation.InvitationStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled invitations cannot be resent");
        }

        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_TTL_DAYS);
        invitation.setInvitationTokenHash(hashToken(rawToken));
        invitation.setInvitationTokenExpiresAt(expiresAt);
        invitation.setLastSentAt(null);
        invitation.setEmailDeliveryStatus(CompanyMentorInvitation.DeliveryStatus.NOT_ATTEMPTED);
        invitation.setWhatsappDeliveryStatus(CompanyMentorInvitation.DeliveryStatus.NOT_ATTEMPTED);
        invitationRepository.save(invitation);

        applyDeliveryAttempt(invitation, notificationService.sendMentorInvitation(
                invitation.getCompany(),
                invitation.getEmail(),
                invitation.getPhone(),
                rawToken,
                expiresAt
        ));
        return toInvitationDto(invitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public CompanyMentorDtos.VerifyInviteResponse verifyInvitation(String rawToken) {
        CompanyMentorInvitation invitation = findValidInvitation(rawToken);
        Optional<Profile> existingProfile = profileRepository.findByEmailIgnoreCase(invitation.getEmail())
                .filter(this::isMentorProfile);

        return CompanyMentorDtos.VerifyInviteResponse.builder()
                .email(invitation.getEmail())
                .phone(invitation.getPhone())
                .firstName(invitation.getFirstName())
                .lastName(invitation.getLastName())
                .title(invitation.getTitle())
                .department(invitation.getDepartment())
                .tags(safeList(invitation.getTags()))
                .companyId(invitation.getCompany().getId())
                .companyName(invitation.getCompany().getName())
                .defaultVisibility(invitation.getDefaultVisibility())
                .existingProsperMentor(existingProfile.isPresent())
                .expiresAt(invitation.getInvitationTokenExpiresAt())
                .build();
    }

    public CompanyMentorDtos.PoolMemberDto acceptInvitation(String rawToken, UUID mentorProfileId) {
        CompanyMentorInvitation invitation = findValidInvitation(rawToken);
        Profile mentor = profileRepository.findById(mentorProfileId)
                .orElseThrow(() -> new NoSuchElementException("Mentor profile not found"));
        if (!isMentorProfile(mentor)) {
            throw new IllegalArgumentException("Authenticated profile is not a mentor");
        }

        MentorProfile mentorDetails = mentorProfileRepository.findById(mentorProfileId)
                .orElseThrow(() -> new NoSuchElementException("Mentor profile details not found"));

        Company company = invitation.getCompany();
        CompanyMentorPoolMembership membership = membershipRepository
                .findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
                        company.getId(),
                        mentorProfileId,
                        LIVE_MEMBERSHIP_STATUSES
                )
                .orElseGet(CompanyMentorPoolMembership::new);

        membership.setCompany(company);
        membership.setMentorProfile(mentor);
        membership.setSourceInvitation(invitation);
        membership.setVisibilityMode(resolveVisibility(invitation.getDefaultVisibility()));
        membership.setMembershipStatus(CompanyMentorPoolMembership.MembershipStatus.ACTIVE);
        membership.setPublicListingPreexisting(Boolean.TRUE.equals(mentor.getIsVerified()));
        if (membership.getVisibilityMode() == CompanyMentorPoolMembership.VisibilityMode.PUBLIC_REQUESTED) {
            membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.REQUESTED);
            membership.setPublicRequestedAt(LocalDateTime.now());
        } else if (membership.getPublicApprovalStatus() == null) {
            membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.NOT_REQUESTED);
        }
        updateBookability(membership, mentorDetails);

        CompanyMentorPoolMembership savedMembership = membershipRepository.save(membership);

        invitation.setStatus(CompanyMentorInvitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedProfile(mentor);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setInvitationTokenHash(null);
        invitation.setInvitationTokenExpiresAt(null);
        invitationRepository.save(invitation);

        return toPoolMemberDto(savedMembership);
    }

    @Transactional(readOnly = true)
    public CompanyMentorDtos.MentorPoolResponse getMentorPool(UUID companyId, int page, int size, String search) {
        getCompany(companyId);
        List<CompanyMentorDtos.InvitationDto> invitations = invitationRepository
                .findByCompany_Id(companyId, PageRequest.of(Math.max(page, 0), Math.max(size, 1)))
                .getContent()
                .stream()
                .map(this::toInvitationDto)
                .toList();

        String normalizedSearch = normalizeSearch(search);
        List<CompanyMentorDtos.PoolMemberDto> members = membershipRepository
                .findByCompany_IdAndMembershipStatusIn(companyId, LIVE_MEMBERSHIP_STATUSES)
                .stream()
                .map(this::toPoolMemberDto)
                .filter(member -> matchesMemberSearch(member, normalizedSearch))
                .sorted(Comparator.comparing(CompanyMentorDtos.PoolMemberDto::getMentorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return CompanyMentorDtos.MentorPoolResponse.builder()
                .invitations(invitations)
                .members(members)
                .metrics(buildMetrics(invitations, members))
                .build();
    }

    public CompanyMentorDtos.PoolMemberDto updateVisibility(UUID companyId,
                                                            UUID membershipId,
                                                            CompanyMentorDtos.VisibilityUpdateRequest request) {
        CompanyMentorPoolMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Company mentor membership not found"));
        if (membership.getCompany() == null || !companyId.equals(membership.getCompany().getId())) {
            throw new SecurityException("Membership does not belong to this company");
        }

        CompanyMentorPoolMembership.VisibilityMode visibilityMode = resolveVisibility(request.getVisibilityMode());
        membership.setVisibilityMode(visibilityMode);
        if (visibilityMode == CompanyMentorPoolMembership.VisibilityMode.PUBLIC_REQUESTED) {
            membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.REQUESTED);
            membership.setPublicRequestedAt(LocalDateTime.now());
        }

        membership.getProgramScopes().clear();
        if (request.getCompanyProgramIds() != null) {
            for (UUID companyProgramId : request.getCompanyProgramIds()) {
                CompanyProgram companyProgram = companyProgramRepository.findByIdAndCompany_Id(companyProgramId, companyId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid company program scope"));
                CompanyMentorProgramScope scope = new CompanyMentorProgramScope();
                scope.setMembership(membership);
                scope.setCompanyProgram(companyProgram);
                membership.getProgramScopes().add(scope);
            }
        }

        return toPoolMemberDto(membershipRepository.save(membership));
    }

    public CompanyMentorDtos.PoolMemberDto approvePublicVisibility(UUID membershipId, UUID approvedByUserId) {
        CompanyMentorPoolMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Company mentor membership not found"));
        membership.setVisibilityMode(CompanyMentorPoolMembership.VisibilityMode.PUBLIC_APPROVED);
        membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.APPROVED);
        membership.setPublicApprovedAt(LocalDateTime.now());
        membership.setPublicApprovedByUserId(approvedByUserId);
        return toPoolMemberDto(membershipRepository.save(membership));
    }

    public CompanyMentorDtos.PoolMemberDto rejectPublicVisibility(UUID membershipId, UUID rejectedByUserId) {
        CompanyMentorPoolMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Company mentor membership not found"));
        membership.setVisibilityMode(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE);
        membership.setPublicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.REJECTED);
        membership.setPublicApprovedByUserId(rejectedByUserId);
        membership.setPublicApprovedAt(null);
        return toPoolMemberDto(membershipRepository.save(membership));
    }

    public void removeMembership(UUID companyId, UUID membershipId) {
        CompanyMentorPoolMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Company mentor membership not found"));
        if (membership.getCompany() == null || !companyId.equals(membership.getCompany().getId())) {
            throw new SecurityException("Membership does not belong to this company");
        }
        membership.setMembershipStatus(CompanyMentorPoolMembership.MembershipStatus.REMOVED);
        membership.setCompanyBookable(false);
        membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public boolean isMentorPubliclyDiscoverable(UUID mentorProfileId) {
        List<CompanyMentorPoolMembership> memberships = membershipRepository.findByMentorProfile_IdAndMembershipStatusIn(
                mentorProfileId,
                Set.of(CompanyMentorPoolMembership.MembershipStatus.ACTIVE)
        );
        if (memberships.isEmpty()) {
            return true;
        }
        return memberships.stream().anyMatch(membership ->
                Boolean.TRUE.equals(membership.getPublicListingPreexisting())
                        || membership.getPublicApprovalStatus() == CompanyMentorPoolMembership.PublicApprovalStatus.APPROVED
                        || membership.getVisibilityMode() == CompanyMentorPoolMembership.VisibilityMode.PUBLIC_APPROVED
        );
    }

    @Transactional(readOnly = true)
    public boolean canCompanyBookMentor(UUID companyId, UUID companyProgramId, UUID mentorProfileId) {
        return membershipRepository.findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
                        companyId,
                        mentorProfileId,
                        Set.of(CompanyMentorPoolMembership.MembershipStatus.ACTIVE)
                )
                .filter(membership -> Boolean.TRUE.equals(membership.getCompanyBookable()))
                .filter(membership -> membershipAllowsProgram(membership, companyProgramId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Set<UUID> eligibleCompanyMentorIds(UUID companyId, UUID companyProgramId) {
        return eligibleCompanyMentorPoolMembersByMentorId(companyId, companyProgramId).keySet();
    }

    @Transactional(readOnly = true)
    public Map<UUID, CompanyMentorDtos.PoolMemberDto> eligibleCompanyMentorPoolMembersByMentorId(UUID companyId, UUID companyProgramId) {
        return membershipRepository.findByCompany_IdAndMembershipStatusIn(
                        companyId,
                        Set.of(CompanyMentorPoolMembership.MembershipStatus.ACTIVE)
                )
                .stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getCompanyBookable()))
                .filter(membership -> membershipAllowsProgram(membership, companyProgramId))
                .filter(membership -> membership.getMentorProfile() != null && membership.getMentorProfile().getId() != null)
                .collect(Collectors.toMap(
                        membership -> membership.getMentorProfile().getId(),
                        this::toPoolMemberDto,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private List<CompanyMentorDtos.ImportRowResult> parseImportRows(Company company, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeader(sheet.getRow(0));
            List<CompanyMentorDtos.ImportRowResult> rows = new ArrayList<>();
            Set<String> emailsInFile = new HashSet<>();
            Set<String> phonesInFile = new HashSet<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                rows.add(parseImportRow(company, row, columns, emailsInFile, phonesInFile));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse mentor import file", e);
        }
    }

    private CompanyMentorDtos.ImportRowResult parseImportRow(Company company,
                                                             Row row,
                                                             Map<String, Integer> columns,
                                                             Set<String> emailsInFile,
                                                             Set<String> phonesInFile) {
        int rowNumber = row.getRowNum() + 1;
        List<CompanyMentorDtos.ImportRowError> errors = new ArrayList<>();
        String email = normalizeNullable(readCell(row, columns.get("email")));
        String phoneRaw = normalizeNullable(readCell(row, columns.get("phone")));
        String normalizedEmail = null;
        String normalizedPhone = null;

        try {
            normalizedEmail = normalizeEmail(email);
            if (!emailsInFile.add(normalizedEmail)) {
                errors.add(rowError(rowNumber, "email", email, "Duplicate email in import file"));
            }
            if (invitationRepository.existsByCompany_IdAndEmailIgnoreCaseAndStatusIn(company.getId(), normalizedEmail, OPEN_INVITATION_STATUSES)) {
                errors.add(rowError(rowNumber, "email", email, "An open invite already exists for this email"));
            }
        } catch (IllegalArgumentException e) {
            errors.add(rowError(rowNumber, "email", email, e.getMessage()));
        }

        try {
            normalizedPhone = normalizeRequiredPhone(phoneRaw);
            if (!phonesInFile.add(normalizedPhone)) {
                errors.add(rowError(rowNumber, "phone", phoneRaw, "Duplicate phone in import file"));
            }
            if (invitationRepository.existsByCompany_IdAndPhoneAndStatusIn(company.getId(), normalizedPhone, OPEN_INVITATION_STATUSES)) {
                errors.add(rowError(rowNumber, "phone", phoneRaw, "An open invite already exists for this phone"));
            }
        } catch (IllegalArgumentException e) {
            errors.add(rowError(rowNumber, "phone", phoneRaw, e.getMessage()));
        }

        if (normalizedEmail != null) {
            String profileLookupEmail = normalizedEmail;
            profileRepository.findByEmailIgnoreCase(profileLookupEmail)
                    .ifPresent(profile -> {
                        if (membershipRepository.existsByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
                                company.getId(),
                                profile.getId(),
                                LIVE_MEMBERSHIP_STATUSES
                        )) {
                            errors.add(rowError(rowNumber, "email", profileLookupEmail, "Mentor is already in this company pool"));
                        }
                    });
        }

        CompanyMentorPoolMembership.VisibilityMode visibility = CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE;
        String visibilityRaw = normalizeNullable(readCell(row, columns.get("visibility")));
        if (visibilityRaw != null) {
            try {
                visibility = CompanyMentorPoolMembership.VisibilityMode.valueOf(visibilityRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(rowError(rowNumber, "visibility", visibilityRaw, "Invalid visibility value"));
            }
        }

        String programOrCohort = normalizeNullable(readCell(row, columns.get("program_or_cohort")));
        validateProgramReference(company, rowNumber, programOrCohort, errors);

        boolean existingProsperMentor = normalizedEmail != null
                && profileRepository.findByEmailIgnoreCase(normalizedEmail).filter(this::isMentorProfile).isPresent();

        return CompanyMentorDtos.ImportRowResult.builder()
                .rowNumber(rowNumber)
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .firstName(normalizeNullable(readCell(row, columns.get("first_name"))))
                .lastName(normalizeNullable(readCell(row, columns.get("last_name"))))
                .title(normalizeNullable(readCell(row, columns.get("title"))))
                .department(normalizeNullable(readCell(row, columns.get("department"))))
                .tags(splitTags(readCell(row, columns.get("tags"))))
                .visibility(visibility)
                .programOrCohortReference(programOrCohort)
                .existingProsperMentor(existingProsperMentor)
                .errors(errors)
                .build();
    }

    private Map<String, Integer> readHeader(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Import file must include a header row");
        }

        Map<String, Integer> columns = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String header = normalizeHeader(formatter.formatCellValue(cell));
            if (header != null) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        if (!columns.containsKey("email")) {
            throw new IllegalArgumentException("Import file must include an email column");
        }
        if (!columns.containsKey("phone")) {
            throw new IllegalArgumentException("Import file must include a phone column");
        }
        return columns;
    }

    private void validateProgramReference(Company company,
                                          int rowNumber,
                                          String programOrCohort,
                                          List<CompanyMentorDtos.ImportRowError> errors) {
        if (programOrCohort == null) {
            return;
        }
        try {
            UUID companyProgramId = UUID.fromString(programOrCohort);
            if (companyProgramRepository.findByIdAndCompany_Id(companyProgramId, company.getId()).isEmpty()) {
                errors.add(rowError(rowNumber, "program_or_cohort", programOrCohort, "Company program was not found"));
            }
        } catch (IllegalArgumentException ignored) {
            // Non-UUID cohort labels are accepted until a dedicated cohort table exists.
        }
    }

    private void applyDeliveryAttempt(CompanyMentorInvitation invitation,
                                      CompanyMentorNotificationService.DeliveryAttemptResult delivery) {
        boolean emailSent = delivery != null && delivery.emailSent();
        boolean whatsappSent = delivery != null && delivery.whatsappSent();

        invitation.setEmailDeliveryStatus(emailSent
                ? CompanyMentorInvitation.DeliveryStatus.SENT
                : CompanyMentorInvitation.DeliveryStatus.FAILED);
        invitation.setWhatsappDeliveryStatus(whatsappSent
                ? CompanyMentorInvitation.DeliveryStatus.SENT
                : CompanyMentorInvitation.DeliveryStatus.FAILED);
        invitation.setStatus(emailSent || whatsappSent
                ? CompanyMentorInvitation.InvitationStatus.SENT
                : CompanyMentorInvitation.InvitationStatus.FAILED_DELIVERY);
        invitation.setLastSentAt(LocalDateTime.now());
    }

    private CompanyMentorInvitation findValidInvitation(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("Invitation token is required");
        }
        CompanyMentorInvitation invitation = invitationRepository.findByInvitationTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));
        if (invitation.getInvitationTokenExpiresAt() != null && invitation.getInvitationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CompanyMentorInvitation.InvitationStatus.EXPIRED);
            throw new IllegalArgumentException("Invitation has expired");
        }
        if (invitation.getStatus() == CompanyMentorInvitation.InvitationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Invitation has already been accepted");
        }
        if (invitation.getStatus() == CompanyMentorInvitation.InvitationStatus.CANCELLED) {
            throw new IllegalArgumentException("Invitation has been cancelled");
        }
        return invitation;
    }

    private void updateBookability(CompanyMentorPoolMembership membership, MentorProfile mentorDetails) {
        boolean profileComplete = mentorDetails != null;
        boolean availabilityComplete = mentorDetails != null && Boolean.TRUE.equals(mentorDetails.getIsAvailable());
        membership.setProfileComplete(profileComplete);
        membership.setAvailabilityComplete(availabilityComplete);
        membership.setCompanyBookable(membership.getMembershipStatus() == CompanyMentorPoolMembership.MembershipStatus.ACTIVE
                && profileComplete
                && availabilityComplete);
    }

    private boolean membershipAllowsProgram(CompanyMentorPoolMembership membership, UUID companyProgramId) {
        if (membership == null || membership.getProgramScopes() == null || membership.getProgramScopes().isEmpty()) {
            return true;
        }
        if (companyProgramId == null) {
            return false;
        }
        return membership.getProgramScopes().stream()
                .map(CompanyMentorProgramScope::getCompanyProgram)
                .filter(Objects::nonNull)
                .map(CompanyProgram::getId)
                .anyMatch(companyProgramId::equals);
    }

    private Company getCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NoSuchElementException("Company not found"));
    }

    private String generateRawToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Email must be valid");
        }
        return normalized;
    }

    private String normalizeRequiredPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String normalized = PhoneNumberUtil.normalizeToE164(phone);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Phone number must be valid");
        }
        return normalized;
    }

    private CompanyMentorPoolMembership.VisibilityMode resolveVisibility(CompanyMentorPoolMembership.VisibilityMode visibilityMode) {
        return visibilityMode != null
                ? visibilityMode
                : CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE;
    }

    private String resolveProgramOrCohortReference(CompanyMentorDtos.InviteRequest request) {
        if (request.getCompanyProgramIds() != null && !request.getCompanyProgramIds().isEmpty()) {
            return request.getCompanyProgramIds().stream()
                    .filter(Objects::nonNull)
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
        }
        return normalizeNullable(request.getCohortReference());
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeSearch(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeHeader(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_")
                : null;
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags == null
                ? List.of()
                : tags.stream()
                .map(this::normalizeNullable)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(this::normalizeNullable)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean isMentorProfile(Profile profile) {
        return profile != null && profile.getRole() != null && "mentor".equalsIgnoreCase(profile.getRole().trim());
    }

    private String displayName(Profile profile) {
        if (profile == null) {
            return "Mentor";
        }
        String name = String.join(" ",
                Optional.ofNullable(profile.getFirstName()).orElse("").trim(),
                Optional.ofNullable(profile.getLastName()).orElse("").trim()
        ).trim();
        if (StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(profile.getEmail()) ? profile.getEmail() : "Mentor";
    }

    private String readCell(Row row, Integer columnIndex) {
        if (columnIndex == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        return cell == null ? null : new DataFormatter().formatCellValue(cell);
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private CompanyMentorDtos.ImportRowError rowError(int rowNumber, String field, String value, String reason) {
        return CompanyMentorDtos.ImportRowError.builder()
                .rowNumber(rowNumber)
                .field(field)
                .value(value)
                .reason(reason)
                .build();
    }

    private boolean matchesMemberSearch(CompanyMentorDtos.PoolMemberDto member, String search) {
        if (search == null) {
            return true;
        }
        return List.of(member.getMentorName(), member.getMentorEmail(), member.getTitle())
                .stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(search));
    }

    private CompanyMentorDtos.MentorPoolMetrics buildMetrics(List<CompanyMentorDtos.InvitationDto> invitations,
                                                             List<CompanyMentorDtos.PoolMemberDto> members) {
        return CompanyMentorDtos.MentorPoolMetrics.builder()
                .totalCompanyMentors(members.size())
                .pendingInvites(invitations.stream().filter(invitation -> invitation.getStatus() == CompanyMentorInvitation.InvitationStatus.SENT).count())
                .acceptedIncompleteProfile(members.stream().filter(member -> !member.isProfileComplete()).count())
                .profileCompleteNoAvailability(members.stream().filter(member -> member.isProfileComplete() && !member.isAvailabilityComplete()).count())
                .companyBookable(members.stream().filter(CompanyMentorDtos.PoolMemberDto::isCompanyBookable).count())
                .publicRequested(members.stream().filter(member -> member.getPublicApprovalStatus() == CompanyMentorPoolMembership.PublicApprovalStatus.REQUESTED).count())
                .publicApproved(members.stream().filter(member -> member.getPublicApprovalStatus() == CompanyMentorPoolMembership.PublicApprovalStatus.APPROVED).count())
                .failedEmailDeliveries(invitations.stream().filter(invitation -> invitation.getEmailDeliveryStatus() == CompanyMentorInvitation.DeliveryStatus.FAILED).count())
                .failedWhatsappDeliveries(invitations.stream().filter(invitation -> invitation.getWhatsappDeliveryStatus() == CompanyMentorInvitation.DeliveryStatus.FAILED).count())
                .build();
    }

    private CompanyMentorDtos.InvitationDto toInvitationDto(CompanyMentorInvitation invitation) {
        return CompanyMentorDtos.InvitationDto.builder()
                .id(invitation.getId())
                .companyId(invitation.getCompany() != null ? invitation.getCompany().getId() : null)
                .companyName(invitation.getCompany() != null ? invitation.getCompany().getName() : null)
                .email(invitation.getEmail())
                .phone(invitation.getPhone())
                .firstName(invitation.getFirstName())
                .lastName(invitation.getLastName())
                .title(invitation.getTitle())
                .department(invitation.getDepartment())
                .tags(safeList(invitation.getTags()))
                .defaultVisibility(invitation.getDefaultVisibility())
                .programOrCohortReference(invitation.getProgramOrCohortReference())
                .status(invitation.getStatus())
                .emailDeliveryStatus(invitation.getEmailDeliveryStatus())
                .whatsappDeliveryStatus(invitation.getWhatsappDeliveryStatus())
                .acceptedProfileId(invitation.getAcceptedProfile() != null ? invitation.getAcceptedProfile().getId() : null)
                .acceptedAt(invitation.getAcceptedAt())
                .lastSentAt(invitation.getLastSentAt())
                .invitationTokenExpiresAt(invitation.getInvitationTokenExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }

    private CompanyMentorDtos.PoolMemberDto toPoolMemberDto(CompanyMentorPoolMembership membership) {
        Profile mentor = membership.getMentorProfile();
        CompanyMentorInvitation invitation = membership.getSourceInvitation();
        return CompanyMentorDtos.PoolMemberDto.builder()
                .id(membership.getId())
                .companyId(membership.getCompany() != null ? membership.getCompany().getId() : null)
                .mentorProfileId(mentor != null ? mentor.getId() : null)
                .sourceInvitationId(invitation != null ? invitation.getId() : null)
                .mentorName(displayName(mentor))
                .mentorEmail(mentor != null ? mentor.getEmail() : null)
                .phone(mentor != null && mentor.getPhone() != null ? mentor.getPhone() : invitation != null ? invitation.getPhone() : null)
                .title(invitation != null ? invitation.getTitle() : null)
                .department(invitation != null ? invitation.getDepartment() : null)
                .tags(invitation != null ? safeList(invitation.getTags()) : List.of())
                .visibilityMode(membership.getVisibilityMode())
                .membershipStatus(membership.getMembershipStatus())
                .profileComplete(Boolean.TRUE.equals(membership.getProfileComplete()))
                .availabilityComplete(Boolean.TRUE.equals(membership.getAvailabilityComplete()))
                .companyBookable(Boolean.TRUE.equals(membership.getCompanyBookable()))
                .publicApprovalStatus(membership.getPublicApprovalStatus())
                .publicListingPreexisting(Boolean.TRUE.equals(membership.getPublicListingPreexisting()))
                .programScopes(toScopeDtos(membership.getProgramScopes()))
                .createdAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }

    private List<CompanyMentorDtos.ProgramScopeDto> toScopeDtos(Collection<CompanyMentorProgramScope> scopes) {
        if (scopes == null) {
            return List.of();
        }
        return scopes.stream()
                .map(scope -> CompanyMentorDtos.ProgramScopeDto.builder()
                        .id(scope.getId())
                        .companyProgramId(scope.getCompanyProgram() != null ? scope.getCompanyProgram().getId() : null)
                        .companyProgramName(scope.getCompanyProgram() != null ? scope.getCompanyProgram().getName() : null)
                        .cohortId(scope.getCohortId())
                        .build())
                .toList();
    }
}
