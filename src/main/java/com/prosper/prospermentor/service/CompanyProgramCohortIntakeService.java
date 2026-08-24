package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.AddCohortRosterParticipantsRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinResponseDto;
import com.prosper.prospermentor.dto.CohortPlenaryAttendanceDto;
import com.prosper.prospermentor.dto.CohortRosterParticipantRequest;
import com.prosper.prospermentor.dto.CompanyProgramCohortJoinRequestDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.dto.PlenaryAttendanceImportRow;
import com.prosper.prospermentor.dto.ResolveCohortDuplicateRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramCohortJoinRequestRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramCohortIntakeService {

    private final CompanyProgramCohortRepository cohortRepository;
    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    private final CompanyProgramCohortJoinRequestRepository joinRequestRepository;
    private final CompanyProgramParticipantRepository programParticipantRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public CohortSelfJoinResponseDto getSelfJoinPreview(String joinCode) {
        CompanyProgramCohort cohort = findJoinableCohort(joinCode);
        ensureSelfJoinOpen(cohort);
        ensureSelfJoinCapacity(cohort);
        return toSelfJoinResponse(cohort, null);
    }

    public CohortSelfJoinResponseDto submitSelfJoin(String joinCode, CohortSelfJoinRequest request) {
        CompanyProgramCohort cohort = findJoinableCohort(joinCode);
        ensureSelfJoinOpen(cohort);
        ensureSelfJoinCapacity(cohort);

        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());
        Optional<Profile> duplicateProfile = profileRepository.findByEmailIgnoreCase(normalizedEmail);
        if (duplicateProfile.isEmpty() && !normalizedPhone.isBlank()) {
            duplicateProfile = profileRepository.findByPhoneNormalized(normalizedPhone);
        }

        CompanyProgramCohortJoinRequest joinRequest = new CompanyProgramCohortJoinRequest();
        joinRequest.setCohort(cohort);
        joinRequest.setSubmittedEmail(normalizedEmail);
        joinRequest.setSubmittedPhone(request.getPhone());
        joinRequest.setSubmittedFirstName(request.getFirstName());
        joinRequest.setSubmittedLastName(request.getLastName());
        joinRequest.setSubmittedChapter(request.getChapter());
        joinRequest.setSubmittedRegion(request.getRegion());
        joinRequest.setSubmittedInterestTags(normalizeTags(request.getInterestTags()));
        joinRequest.setMatchedProfile(duplicateProfile.orElse(null));
        joinRequest.setStatus(duplicateProfile.isPresent()
                ? CompanyProgramCohortJoinRequest.JoinRequestStatus.DUPLICATE_REVIEW
                : CompanyProgramCohortJoinRequest.JoinRequestStatus.PENDING);

        CompanyProgramCohortJoinRequest saved = joinRequestRepository.save(joinRequest);
        log.info("Created cohort self-join request for cohort {} with status {}", cohort.getId(), saved.getStatus());
        return toSelfJoinResponse(cohort, saved);
    }

    @Transactional(readOnly = true)
    public List<CompanyProgramCohortParticipantDto> getParticipants(UUID cohortId) {
        return cohortParticipantRepository.findByCohort_Id(cohortId).stream()
                .map(this::toParticipantDto)
                .toList();
    }

    public List<CompanyProgramCohortParticipantDto> addRosterParticipants(UUID cohortId,
                                                                          AddCohortRosterParticipantsRequest request,
                                                                          UUID adminUserId) {
        CompanyProgramCohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("Company program cohort not found"));
        List<CohortRosterParticipantRequest> rows = request != null && request.getParticipants() != null
                ? request.getParticipants().stream().filter(Objects::nonNull).toList()
                : List.of();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("At least one roster participant is required");
        }

        List<CompanyProgramCohortParticipantDto> participants = new ArrayList<>();
        for (CohortRosterParticipantRequest row : rows) {
            Profile profile = resolveRosterProfile(cohort, row);
            CompanyProgramCohortParticipant participant = cohortParticipantRepository
                    .findByCohort_IdAndProfile_Id(cohortId, profile.getId())
                    .orElseGet(CompanyProgramCohortParticipant::new);

            participant.setCohort(cohort);
            participant.setProfile(profile);
            if (participant.getId() == null
                    || participant.getStatus() == CompanyProgramCohortParticipant.CohortParticipantStatus.REJECTED
                    || participant.getStatus() == CompanyProgramCohortParticipant.CohortParticipantStatus.WITHDRAWN) {
                participant.setSource(CompanyProgramCohortParticipant.ParticipantSource.ROSTER_UPLOAD);
                participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PENDING);
                participant.setDuplicateStatus(CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
                participant.setConfirmedByUserId(null);
                participant.setConfirmedAt(null);
            }
            applySnapshots(participant, profile, row.getChapter(), row.getRegion(), row.getInterestTags());

            participants.add(toParticipantDto(cohortParticipantRepository.save(participant)));
        }

        log.info("Added {} roster participant rows to cohort {} by admin {}", participants.size(), cohortId, adminUserId);
        return participants;
    }

    @Transactional(readOnly = true)
    public List<CompanyProgramCohortJoinRequestDto> getJoinRequests(UUID cohortId) {
        return joinRequestRepository.findByCohort_IdOrderByCreatedAtDesc(cohortId).stream()
                .map(this::toJoinRequestDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UUID getParticipantCompanyId(UUID participantId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        return companyId(participant.getCohort());
    }

    @Transactional(readOnly = true)
    public UUID getJoinRequestCompanyId(UUID joinRequestId) {
        CompanyProgramCohortJoinRequest joinRequest = joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new NoSuchElementException("Cohort join request not found"));
        return companyId(joinRequest.getCohort());
    }

    @Transactional(readOnly = true)
    public List<CohortPlenaryAttendanceDto> getPlenaryAttendance(UUID cohortId) {
        return attendanceRepository.findByCohort_Id(cohortId).stream()
                .map(this::toAttendanceDto)
                .toList();
    }

    public CompanyProgramCohortParticipantDto confirmJoinRequest(UUID joinRequestId, UUID profileId, UUID adminUserId) {
        CompanyProgramCohortJoinRequest joinRequest = joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new NoSuchElementException("Cohort join request not found"));
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));

        CompanyProgramCohortParticipant participant = cohortParticipantRepository
                .findByCohort_IdAndProfile_Id(joinRequest.getCohort().getId(), profileId)
                .orElseGet(CompanyProgramCohortParticipant::new);

        participant.setCohort(joinRequest.getCohort());
        participant.setProfile(profile);
        participant.setCompanyProgramParticipant(resolveProgramParticipant(joinRequest.getCohort(), profile, adminUserId));
        participant.setSource(CompanyProgramCohortParticipant.ParticipantSource.SELF_JOIN);
        participant.setSelfJoinRequest(joinRequest);
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        participant.setDuplicateStatus(CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        participant.setConfirmedByUserId(adminUserId);
        participant.setConfirmedAt(LocalDateTime.now());
        applySnapshots(participant, profile, joinRequest.getSubmittedChapter(), joinRequest.getSubmittedRegion(), joinRequest.getSubmittedInterestTags());

        joinRequest.setStatus(CompanyProgramCohortJoinRequest.JoinRequestStatus.CONFIRMED);
        joinRequest.setReviewedByUserId(adminUserId);
        joinRequest.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(joinRequest);

        return toParticipantDto(cohortParticipantRepository.save(participant));
    }

    public CompanyProgramCohortParticipantDto rejectJoinRequest(UUID joinRequestId, UUID adminUserId) {
        CompanyProgramCohortJoinRequest joinRequest = joinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new NoSuchElementException("Cohort join request not found"));
        joinRequest.setStatus(CompanyProgramCohortJoinRequest.JoinRequestStatus.REJECTED);
        joinRequest.setReviewedByUserId(adminUserId);
        joinRequest.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(joinRequest);
        return null;
    }

    public CompanyProgramCohortParticipantDto confirmParticipant(UUID cohortParticipantId, UUID adminUserId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        participant.setCompanyProgramParticipant(resolveProgramParticipant(participant.getCohort(), participant.getProfile(), adminUserId));
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        participant.setDuplicateStatus(CompanyProgramCohortParticipant.DuplicateStatus.CLEAR);
        participant.setConfirmedByUserId(adminUserId);
        participant.setConfirmedAt(LocalDateTime.now());
        return toParticipantDto(cohortParticipantRepository.save(participant));
    }

    public CompanyProgramCohortParticipantDto rejectParticipant(UUID cohortParticipantId, UUID adminUserId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.REJECTED);
        participant.setConfirmedByUserId(adminUserId);
        return toParticipantDto(cohortParticipantRepository.save(participant));
    }

    public CompanyProgramCohortParticipantDto resolveDuplicate(UUID cohortParticipantId,
                                                               ResolveCohortDuplicateRequest request,
                                                               UUID adminUserId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        Profile profile = profileRepository.findById(request.getProfileId())
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));
        participant.setProfile(profile);
        participant.setDuplicateCandidateProfile(profile);
        participant.setDuplicateStatus(request.getDuplicateStatus());
        participant.setConfirmedByUserId(adminUserId);
        applySnapshots(participant, profile, participant.getChapter(), participant.getRegion(), participant.getInterestTags());
        return toParticipantDto(cohortParticipantRepository.save(participant));
    }

    public CompanyProgramCohortParticipantDto recordPlenaryAttendance(UUID cohortParticipantId,
                                                                      CompanyProgramCohortPlenaryAttendance.AttendanceStatus status,
                                                                      CompanyProgramCohortPlenaryAttendance.AttendanceSource source,
                                                                      UUID recordedByUserId) {
        CompanyProgramCohortParticipant participant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        CompanyProgramCohortPlenaryAttendance attendance = attendanceRepository.findByCohortParticipant_Id(cohortParticipantId)
                .orElseGet(CompanyProgramCohortPlenaryAttendance::new);

        CompanyProgramCohortPlenaryAttendance.AttendanceStatus resolvedStatus = status != null
                ? status
                : CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED;
        attendance.setCohort(participant.getCohort());
        attendance.setCohortParticipant(participant);
        attendance.setStatus(resolvedStatus);
        attendance.setAttendanceSource(source != null
                ? source
                : CompanyProgramCohortPlenaryAttendance.AttendanceSource.ADMIN_OVERRIDE);
        attendance.setRecordedByUserId(recordedByUserId);
        attendance.setAttendedAt(resolvedStatus == CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED
                ? LocalDateTime.now()
                : null);
        attendanceRepository.save(attendance);

        if (resolvedStatus == CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED
                && participant.getStatus() == CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED) {
            participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED);
            participant = cohortParticipantRepository.save(participant);
        }

        return toParticipantDto(participant);
    }

    public List<CompanyProgramCohortParticipantDto> importPlenaryAttendance(UUID cohortId,
                                                                            List<PlenaryAttendanceImportRow> rows,
                                                                            UUID recordedByUserId) {
        if (!cohortRepository.existsById(cohortId)) {
            throw new NoSuchElementException("Company program cohort not found");
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<CompanyProgramCohortParticipant> participants = cohortParticipantRepository.findByCohort_Id(cohortId);
        return rows.stream()
                .filter(Objects::nonNull)
                .map(row -> resolveImportParticipant(participants, row)
                        .map(participant -> recordPlenaryAttendance(
                                participant.getId(),
                                row.getStatus(),
                                row.getAttendanceSource() != null
                                        ? row.getAttendanceSource()
                                        : CompanyProgramCohortPlenaryAttendance.AttendanceSource.IMPORT,
                                recordedByUserId
                        )))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public CompanyProgramCohortParticipantDto toParticipantDto(CompanyProgramCohortParticipant participant) {
        if (participant == null) {
            return null;
        }
        Profile profile = participant.getProfile();
        CompanyProgramCohort cohort = participant.getCohort();
        CompanyProgramParticipant programParticipant = participant.getCompanyProgramParticipant();
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        return CompanyProgramCohortParticipantDto.builder()
                .id(participant.getId())
                .cohortId(cohort != null ? cohort.getId() : null)
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyProgramParticipantId(programParticipant != null ? programParticipant.getId() : null)
                .profileId(profile != null ? profile.getId() : null)
                .profileName(buildProfileName(profile, participant))
                .profileEmail(profile != null ? profile.getEmail() : participant.getEmailSnapshot())
                .profilePhone(profile != null ? profile.getPhone() : participant.getPhoneSnapshot())
                .source(participant.getSource())
                .status(participant.getStatus())
                .chapter(participant.getChapter())
                .region(participant.getRegion())
                .interestTags(participant.getInterestTags() != null ? participant.getInterestTags() : List.of())
                .duplicateStatus(participant.getDuplicateStatus())
                .duplicateCandidateProfileId(participant.getDuplicateCandidateProfile() != null
                        ? participant.getDuplicateCandidateProfile().getId()
                        : null)
                .confirmedByUserId(participant.getConfirmedByUserId())
                .confirmedAt(participant.getConfirmedAt())
                .version(participant.getVersion())
                .createdAt(participant.getCreatedAt())
                .updatedAt(participant.getUpdatedAt())
                .build();
    }

    public CohortPlenaryAttendanceDto toAttendanceDto(CompanyProgramCohortPlenaryAttendance attendance) {
        if (attendance == null) {
            return null;
        }
        CompanyProgramCohortParticipant participant = attendance.getCohortParticipant();
        Profile profile = participant != null ? participant.getProfile() : null;
        return CohortPlenaryAttendanceDto.builder()
                .id(attendance.getId())
                .cohortId(attendance.getCohort() != null ? attendance.getCohort().getId() : null)
                .cohortParticipantId(participant != null ? participant.getId() : null)
                .profileId(profile != null ? profile.getId() : null)
                .profileName(participant != null ? buildProfileName(profile, participant) : null)
                .profileEmail(profile != null ? profile.getEmail() : participant != null ? participant.getEmailSnapshot() : null)
                .attendanceSource(attendance.getAttendanceSource())
                .status(attendance.getStatus())
                .attendedAt(attendance.getAttendedAt())
                .recordedByUserId(attendance.getRecordedByUserId())
                .createdAt(attendance.getCreatedAt())
                .updatedAt(attendance.getUpdatedAt())
                .build();
    }

    public CompanyProgramCohortJoinRequestDto toJoinRequestDto(CompanyProgramCohortJoinRequest joinRequest) {
        if (joinRequest == null) {
            return null;
        }
        CompanyProgramCohort cohort = joinRequest.getCohort();
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Profile matchedProfile = joinRequest.getMatchedProfile();
        return CompanyProgramCohortJoinRequestDto.builder()
                .id(joinRequest.getId())
                .cohortId(cohort != null ? cohort.getId() : null)
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .submittedEmail(joinRequest.getSubmittedEmail())
                .submittedPhone(joinRequest.getSubmittedPhone())
                .submittedFirstName(joinRequest.getSubmittedFirstName())
                .submittedLastName(joinRequest.getSubmittedLastName())
                .submittedChapter(joinRequest.getSubmittedChapter())
                .submittedRegion(joinRequest.getSubmittedRegion())
                .interestTags(joinRequest.getSubmittedInterestTags() != null ? joinRequest.getSubmittedInterestTags() : List.of())
                .matchedProfileId(matchedProfile != null ? matchedProfile.getId() : null)
                .matchedProfileName(profileName(matchedProfile))
                .status(joinRequest.getStatus())
                .reviewedByUserId(joinRequest.getReviewedByUserId())
                .reviewedAt(joinRequest.getReviewedAt())
                .createdAt(joinRequest.getCreatedAt())
                .updatedAt(joinRequest.getUpdatedAt())
                .build();
    }

    private Optional<CompanyProgramCohortParticipant> resolveImportParticipant(List<CompanyProgramCohortParticipant> participants,
                                                                               PlenaryAttendanceImportRow row) {
        if (row == null) {
            return Optional.empty();
        }
        if (row.getCohortParticipantId() != null) {
            return participants.stream()
                    .filter(participant -> row.getCohortParticipantId().equals(participant.getId()))
                    .findFirst();
        }
        String email = normalizeEmail(row.getEmail());
        if (email.isBlank()) {
            return Optional.empty();
        }
        return participants.stream()
                .filter(participant -> {
                    Profile profile = participant.getProfile();
                    String participantEmail = profile != null ? profile.getEmail() : participant.getEmailSnapshot();
                    return email.equals(normalizeEmail(participantEmail));
                })
                .findFirst();
    }

    private CompanyProgramParticipant resolveProgramParticipant(CompanyProgramCohort cohort,
                                                                Profile profile,
                                                                UUID enrolledByUserId) {
        if (cohort == null || cohort.getCompanyProgram() == null || profile == null) {
            return null;
        }

        UUID companyProgramId = cohort.getCompanyProgram().getId();
        return programParticipantRepository
                .findByCompanyProgram_IdAndProfile_IdIn(companyProgramId, List.of(profile.getId()))
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    CompanyProgramParticipant participant = new CompanyProgramParticipant();
                    participant.setCompanyProgram(cohort.getCompanyProgram());
                    participant.setProfile(profile);
                    participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ENROLLED);
                    participant.setEnrolledByUserId(enrolledByUserId);
                    participant.setEnrolledAt(LocalDateTime.now());
                    return programParticipantRepository.save(participant);
                });
    }

    private Profile resolveRosterProfile(CompanyProgramCohort cohort, CohortRosterParticipantRequest row) {
        Company company = cohortCompany(cohort);
        if (row.getProfileId() != null) {
            Profile profile = profileRepository.findById(row.getProfileId())
                    .orElseThrow(() -> new NoSuchElementException("Roster participant profile not found"));
            return ensureRosterProfileCompany(profile, company);
        }

        String email = normalizeEmail(row.getEmail());
        String phone = row.getPhone() != null ? row.getPhone().trim() : null;
        String normalizedPhone = normalizePhone(phone);

        Optional<Profile> profile = !email.isBlank()
                ? profileRepository.findByEmailIgnoreCase(email)
                : Optional.empty();
        if (profile.isEmpty() && !normalizedPhone.isBlank()) {
            profile = profileRepository.findByPhoneNormalized(normalizedPhone);
        }

        if (profile.isPresent()) {
            Profile existing = profile.get();
            boolean changed = applyMissingRosterProfileFields(existing, row, company, email, phone);
            return changed ? profileRepository.save(existing) : ensureRosterProfileCompany(existing, company);
        }

        if (email.isBlank()) {
            throw new IllegalArgumentException("Roster participant email is required when no existing profile matches the phone number");
        }

        Profile created = new Profile();
        created.setId(UUID.randomUUID());
        created.setCompany(company);
        created.setEmail(email);
        created.setUsername(buildRosterUsername(row, email));
        created.setFirstName(trimToNull(row.getFirstName()));
        created.setLastName(trimToNull(row.getLastName()));
        created.setPhone(phone);
        created.setRole("mentee");
        created.setIsVerified(false);
        return profileRepository.save(created);
    }

    private Profile ensureRosterProfileCompany(Profile profile, Company company) {
        Company currentCompany = profile.getCompany();
        if (currentCompany != null && currentCompany.getId() != null
                && company != null && company.getId() != null
                && !company.getId().equals(currentCompany.getId())) {
            throw new IllegalArgumentException("Roster participant profile belongs to another company");
        }
        if (currentCompany == null || currentCompany.getId() == null) {
            profile.setCompany(company);
            return profileRepository.save(profile);
        }
        return profile;
    }

    private boolean applyMissingRosterProfileFields(Profile profile,
                                                    CohortRosterParticipantRequest row,
                                                    Company company,
                                                    String normalizedEmail,
                                                    String phone) {
        Profile companyProfile = ensureRosterProfileCompany(profile, company);
        boolean changed = companyProfile != profile;
        if ((profile.getFirstName() == null || profile.getFirstName().isBlank()) && trimToNull(row.getFirstName()) != null) {
            profile.setFirstName(trimToNull(row.getFirstName()));
            changed = true;
        }
        if ((profile.getLastName() == null || profile.getLastName().isBlank()) && trimToNull(row.getLastName()) != null) {
            profile.setLastName(trimToNull(row.getLastName()));
            changed = true;
        }
        if ((profile.getEmail() == null || profile.getEmail().isBlank()) && !normalizedEmail.isBlank()) {
            profile.setEmail(normalizedEmail);
            changed = true;
        }
        if ((profile.getPhone() == null || profile.getPhone().isBlank()) && phone != null && !phone.isBlank()) {
            profile.setPhone(phone);
            changed = true;
        }
        if ((profile.getRole() == null || profile.getRole().isBlank())) {
            profile.setRole("mentee");
            changed = true;
        }
        return changed;
    }

    private Company cohortCompany(CompanyProgramCohort cohort) {
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Company company = companyProgram != null ? companyProgram.getCompany() : null;
        if (company == null || company.getId() == null) {
            throw new NoSuchElementException("Company context not found for cohort");
        }
        return company;
    }

    private String buildRosterUsername(CohortRosterParticipantRequest row, String normalizedEmail) {
        String rawBase = !normalizedEmail.isBlank()
                ? normalizedEmail.split("@", 2)[0]
                : Stream.of(row.getFirstName(), row.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + "." + second)
                .orElse("mentee");
        String base = rawBase.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (base.isBlank()) {
            base = "mentee";
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void applySnapshots(CompanyProgramCohortParticipant participant,
                                Profile profile,
                                String chapter,
                                String region,
                                List<String> interestTags) {
        participant.setFirstNameSnapshot(profile != null ? profile.getFirstName() : participant.getFirstNameSnapshot());
        participant.setLastNameSnapshot(profile != null ? profile.getLastName() : participant.getLastNameSnapshot());
        participant.setEmailSnapshot(profile != null ? profile.getEmail() : participant.getEmailSnapshot());
        participant.setPhoneSnapshot(profile != null ? profile.getPhone() : participant.getPhoneSnapshot());
        participant.setChapter(chapter);
        participant.setRegion(region);
        participant.setInterestTags(normalizeTags(interestTags));
    }

    private CompanyProgramCohort findJoinableCohort(String joinCode) {
        String hash = hashJoinCode(joinCode);
        return cohortRepository.findBySelfJoinCodeHashAndSelfJoinEnabledTrue(hash)
                .orElseThrow(() -> new NoSuchElementException("Cohort join code is invalid or unavailable"));
    }

    private void ensureSelfJoinOpen(CompanyProgramCohort cohort) {
        if (cohort.getStatus() != CompanyProgramCohort.CohortStatus.INTAKE_OPEN) {
            throw new IllegalStateException("Cohort intake is not open");
        }
        if (cohort.getSelfJoinExpiresAt() != null && cohort.getSelfJoinExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Cohort join code has expired");
        }
    }

    private void ensureSelfJoinCapacity(CompanyProgramCohort cohort) {
        if (cohort.getSelfJoinCapacity() == null || cohort.getId() == null) {
            return;
        }
        long activeParticipants = cohortParticipantRepository.countByCohort_IdAndStatusNot(
                cohort.getId(),
                CompanyProgramCohortParticipant.CohortParticipantStatus.REJECTED
        );
        if (activeParticipants >= cohort.getSelfJoinCapacity()) {
            throw new IllegalStateException("Cohort capacity has been reached");
        }
    }

    private CohortSelfJoinResponseDto toSelfJoinResponse(CompanyProgramCohort cohort,
                                                         CompanyProgramCohortJoinRequest joinRequest) {
        CompanyProgram companyProgram = cohort.getCompanyProgram();
        Company company = companyProgram != null ? companyProgram.getCompany() : null;
        return CohortSelfJoinResponseDto.builder()
                .joinRequestId(joinRequest != null ? joinRequest.getId() : null)
                .cohortId(cohort.getId())
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyProgramName(companyProgram != null ? companyProgram.getName() : null)
                .companyName(company != null ? company.getName() : null)
                .cohortName(cohort.getName())
                .chapter(cohort.getChapter())
                .region(cohort.getRegion())
                .cohortStatus(cohort.getStatus())
                .status(joinRequest != null ? joinRequest.getStatus() : null)
                .duplicateReviewRequired(joinRequest != null
                        && joinRequest.getStatus() == CompanyProgramCohortJoinRequest.JoinRequestStatus.DUPLICATE_REVIEW)
                .matchedProfileId(joinRequest != null && joinRequest.getMatchedProfile() != null
                        ? joinRequest.getMatchedProfile().getId()
                        : null)
                .interestTagSet(cohort.getInterestTagSet() != null ? cohort.getInterestTagSet() : List.of())
                .startsAt(cohort.getStartsAt())
                .endsAt(cohort.getEndsAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private UUID companyId(CompanyProgramCohort cohort) {
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Company company = companyProgram != null ? companyProgram.getCompany() : null;
        UUID companyId = company != null ? company.getId() : null;
        if (companyId == null) {
            throw new NoSuchElementException("Company context not found for cohort");
        }
        return companyId;
    }

    private String profileName(Profile profile) {
        if (profile == null) {
            return null;
        }
        return Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse(profile.getEmail());
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String hashJoinCode(String joinCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(joinCode).trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String buildProfileName(Profile profile, CompanyProgramCohortParticipant participant) {
        if (profile != null) {
            String fullName = List.of(profile.getFirstName(), profile.getLastName()).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .reduce((first, second) -> first + " " + second)
                    .orElse("");
            if (!fullName.isBlank()) {
                return fullName;
            }
            if (profile.getUsername() != null) {
                return profile.getUsername();
            }
            return profile.getEmail();
        }
        return Stream.of(participant.getFirstNameSnapshot(), participant.getLastNameSnapshot())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse("Cohort participant");
    }
}
