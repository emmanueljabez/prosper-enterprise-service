package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortSelfJoinRequest;
import com.prosper.prospermentor.dto.CohortSelfJoinResponseDto;
import com.prosper.prospermentor.dto.CompanyProgramCohortParticipantDto;
import com.prosper.prospermentor.dto.ResolveCohortDuplicateRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CompanyProgramCohortJoinRequestRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramCohortIntakeService {

    private final CompanyProgramCohortRepository cohortRepository;
    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CompanyProgramCohortJoinRequestRepository joinRequestRepository;
    private final CompanyProgramParticipantRepository programParticipantRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public CohortSelfJoinResponseDto getSelfJoinPreview(String joinCode) {
        CompanyProgramCohort cohort = findJoinableCohort(joinCode);
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
        return List.of(participant.getFirstNameSnapshot(), participant.getLastNameSnapshot()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse("Cohort participant");
    }
}
