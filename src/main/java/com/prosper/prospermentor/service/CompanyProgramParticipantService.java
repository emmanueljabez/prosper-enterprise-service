package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyProgramParticipantDto;
import com.prosper.prospermentor.dto.CompanyProgramParticipantEnrollmentResultDto;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramDto;
import com.prosper.prospermentor.dto.EnrollCompanyProgramParticipantsRequest;
import com.prosper.prospermentor.dto.MatchWorkspaceSummaryDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.dto.ParticipantConsentSummaryDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.support.CompanyProgramCatalogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import com.prosper.prospermentor.entity.Program;
import org.springframework.data.domain.PageImpl;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramParticipantService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private static final EnumSet<CompanyProgram.CompanyProgramStatus> ENROLLABLE_PROGRAM_STATUSES = EnumSet.of(
            CompanyProgram.CompanyProgramStatus.DRAFT,
            CompanyProgram.CompanyProgramStatus.LIVE,
            CompanyProgram.CompanyProgramStatus.PAUSED
    );

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> VISIBLE_EMPLOYEE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramRepository companyProgramRepository;
    private final ProfileRepository profileRepository;
    private final CompanyProgramMentorAssignmentService mentorAssignmentService;
    private final CompanyProgramMatchWorkspaceService matchWorkspaceService;
    private final ParticipantConsentService participantConsentService;
    private final JourneyInstanceService journeyInstanceService;
    private final ParticipantPulseService participantPulseService;

    @Transactional(readOnly = true)
    public Page<CompanyProgramParticipantDto> getParticipants(UUID companyProgramId,
                                                              String search,
                                                              CompanyProgramParticipant.ParticipantStatus status,
                                                              Pageable pageable) {
        return getParticipants(companyProgramId, search, status, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CompanyProgramParticipantDto> getParticipants(UUID companyProgramId,
                                                              String search,
                                                              CompanyProgramParticipant.ParticipantStatus status,
                                                              LocalDate startDate,
                                                              LocalDate endDate,
                                                              Pageable pageable) {
        ensureCompanyProgramExists(companyProgramId);

        String normalizedSearch = search != null && !search.trim().isBlank() ? search.trim() : "";
        LocalDate resolvedEndDate = resolveEndDate(startDate, endDate);
        LocalDate resolvedStartDate = resolveStartDate(startDate, resolvedEndDate);
        LocalDateTime rangeStart = resolvedStartDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = resolvedEndDate.plusDays(1).atStartOfDay();

        Page<CompanyProgramParticipant> participantPage = companyProgramParticipantRepository.findByCompanyProgramIdWithFilters(
                companyProgramId,
                status,
                normalizedSearch,
                rangeStart,
                rangeEndExclusive,
                pageable
        );

        Map<UUID, MentorAssignmentSummaryDto> assignmentsByParticipantId = mentorAssignmentService.getAssignmentSummaries(
                participantPage.getContent().stream().map(CompanyProgramParticipant::getId).toList()
        );
        Map<UUID, MatchWorkspaceSummaryDto> workspaceByParticipantId = matchWorkspaceService.getWorkspaceSummaries(
                participantPage.getContent().stream().map(CompanyProgramParticipant::getId).toList()
        );
        Map<UUID, ParticipantConsentSummaryDto> consentSummariesByParticipantId = participantConsentService.getConsentSummaries(
                participantPage.getContent().stream().map(CompanyProgramParticipant::getId).toList()
        );

        List<CompanyProgramParticipantDto> participantDtos = participantPage.getContent().stream()
                .map(participant -> toDto(
                        participant,
                        assignmentsByParticipantId.get(participant.getId()),
                        workspaceByParticipantId.get(participant.getId()),
                        consentSummariesByParticipantId.get(participant.getId())
                ))
                .toList();

        return new PageImpl<>(participantDtos, pageable, participantPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Optional<CompanyProgramParticipant> getParticipant(UUID participantId) {
        return companyProgramParticipantRepository.findById(participantId);
    }

    public ApiResponse<CompanyProgramParticipantEnrollmentResultDto> enrollParticipants(UUID companyProgramId,
                                                                                        EnrollCompanyProgramParticipantsRequest request,
                                                                                        UUID enrolledByUserId) {
        CompanyProgram companyProgram = companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));

        if (!ENROLLABLE_PROGRAM_STATUSES.contains(companyProgram.getStatus())) {
            return ApiResponse.error("Employees can only be enrolled into draft, live, or paused company programs");
        }

        List<UUID> requestedProfileIds = request.getProfileIds() == null
                ? List.of()
                : request.getProfileIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (requestedProfileIds.isEmpty()) {
            return ApiResponse.error("At least one profileId is required");
        }

        Map<UUID, Profile> profilesById = profileRepository.findAllById(requestedProfileIds).stream()
                .collect(java.util.stream.Collectors.toMap(Profile::getId, profile -> profile));

        List<CompanyProgramParticipantEnrollmentResultDto.SkippedParticipantDto> skippedParticipants = new ArrayList<>();
        List<Profile> eligibleProfiles = new ArrayList<>();

        for (UUID profileId : requestedProfileIds) {
            Profile profile = profilesById.get(profileId);

            if (profile == null) {
                skippedParticipants.add(skipped(profileId, "PROFILE_NOT_FOUND"));
                continue;
            }

            if (profile.getCompany() == null || !companyProgram.getCompany().getId().equals(profile.getCompany().getId())) {
                skippedParticipants.add(skipped(profileId, "PROFILE_NOT_IN_COMPANY"));
                continue;
            }

            if (!isEmployeeProfile(profile)) {
                skippedParticipants.add(skipped(profileId, "PROFILE_NOT_EMPLOYEE"));
                continue;
            }

            eligibleProfiles.add(profile);
        }

        Set<UUID> existingProfileIds = companyProgramParticipantRepository
                .findByCompanyProgram_IdAndProfile_IdIn(
                        companyProgramId,
                        eligibleProfiles.stream().map(Profile::getId).toList()
                )
                .stream()
                .map(participant -> participant.getProfile().getId())
                .collect(java.util.stream.Collectors.toSet());

        long enrolledCountBefore = companyProgramParticipantRepository.countByCompanyProgram_Id(companyProgramId);
        int remainingCapacity = companyProgram.getMaxParticipants() == null
                ? Integer.MAX_VALUE
                : Math.max(companyProgram.getMaxParticipants() - (int) enrolledCountBefore, 0);

        List<CompanyProgramParticipant> participantsToSave = new ArrayList<>();

        for (Profile eligibleProfile : eligibleProfiles) {
            UUID profileId = eligibleProfile.getId();

            if (existingProfileIds.contains(profileId)) {
                skippedParticipants.add(skipped(profileId, "ALREADY_ENROLLED"));
                continue;
            }

            if (remainingCapacity <= 0) {
                skippedParticipants.add(skipped(profileId, "CAPACITY_REACHED"));
                continue;
            }

            CompanyProgramParticipant participant = new CompanyProgramParticipant();
            participant.setCompanyProgram(companyProgram);
            participant.setProfile(eligibleProfile);
            participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ENROLLED);
            participant.setEnrolledByUserId(enrolledByUserId);
            participant.setEnrolledAt(LocalDateTime.now());
            participantsToSave.add(participant);
            remainingCapacity--;
        }

        List<CompanyProgramParticipant> savedParticipants = companyProgramParticipantRepository.saveAll(participantsToSave);
        journeyInstanceService.ensureJourneyInstancesForParticipants(savedParticipants);
        participantPulseService.ensureBaselinePulsesForParticipants(savedParticipants);
        matchWorkspaceService.initializeWorkspacesForParticipants(savedParticipants);

        Map<UUID, MatchWorkspaceSummaryDto> workspaceByParticipantId = matchWorkspaceService.getWorkspaceSummaries(
                savedParticipants.stream().map(CompanyProgramParticipant::getId).toList()
        );

        List<CompanyProgramParticipantDto> enrolledParticipants = savedParticipants.stream()
                .map(participant -> toDto(participant, null, workspaceByParticipantId.get(participant.getId()), null))
                .toList();

        long totalParticipants = companyProgramParticipantRepository.countByCompanyProgram_Id(companyProgramId);
        CompanyProgramParticipantEnrollmentResultDto result = CompanyProgramParticipantEnrollmentResultDto.builder()
                .companyProgramId(companyProgramId)
                .enrolledCount(enrolledParticipants.size())
                .skippedCount(skippedParticipants.size())
                .totalParticipants(totalParticipants)
                .enrolledParticipants(enrolledParticipants)
                .skippedParticipants(skippedParticipants)
                .build();

        String message = buildEnrollmentMessage(enrolledParticipants.size(), skippedParticipants.size());
        log.info("Processed {} employee enrollments for company program {} (enrolled={}, skipped={})",
                requestedProfileIds.size(), companyProgramId, enrolledParticipants.size(), skippedParticipants.size());

        return ApiResponse.success(message, result);
    }

    public ApiResponse<Void> removeParticipant(UUID participantId) {
        CompanyProgramParticipant participant = companyProgramParticipantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        companyProgramParticipantRepository.delete(participant);
        log.info("Removed employee {} from company program {}", participantId, participant.getCompanyProgram().getId());
        return ApiResponse.success("Employee removed from company program successfully");
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompanyProgramDto> getEnrolledProgramsForProfile(UUID profileId) {
        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByProfileIdAndStatusIn(profileId, VISIBLE_EMPLOYEE_STATUSES)
                .stream()
                .sorted(Comparator
                        .comparingInt((CompanyProgramParticipant participant) -> participantStatusPriority(participant.getStatus()))
                        .thenComparing(participant -> {
                            CompanyProgram companyProgram = participant.getCompanyProgram();
                            return companyProgram != null && companyProgram.getStartsAt() != null
                                    ? companyProgram.getStartsAt()
                                    : participant.getEnrolledAt();
                        }, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompanyProgramParticipant::getEnrolledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<UUID, ParticipantConsentSummaryDto> consentSummariesByParticipantId = participantConsentService.getConsentSummaries(
                participants.stream().map(CompanyProgramParticipant::getId).toList()
        );

        return participants.stream()
                .map(participant -> toEmployeeProgramDto(participant, consentSummariesByParticipantId.get(participant.getId())))
                .toList();
    }

    private void ensureCompanyProgramExists(UUID companyProgramId) {
        if (!companyProgramRepository.existsById(companyProgramId)) {
            throw new NoSuchElementException("Company program not found");
        }
    }

    private boolean isEmployeeProfile(Profile profile) {
        String normalizedRole = profile.getRole() == null ? "" : profile.getRole().trim().toLowerCase(Locale.ROOT);
        return "employee".equals(normalizedRole) || "mentee".equals(normalizedRole);
    }

    private CompanyProgramParticipantEnrollmentResultDto.SkippedParticipantDto skipped(UUID profileId, String reason) {
        return CompanyProgramParticipantEnrollmentResultDto.SkippedParticipantDto.builder()
                .profileId(profileId)
                .reason(reason)
                .build();
    }

    private String buildEnrollmentMessage(int enrolledCount, int skippedCount) {
        if (enrolledCount > 0 && skippedCount > 0) {
            return String.format("Enrolled %d employee(s). Skipped %d employee(s).", enrolledCount, skippedCount);
        }
        if (enrolledCount > 0) {
            return String.format("Enrolled %d employee(s) successfully.", enrolledCount);
        }
        return skippedCount > 0
                ? String.format("No employees were enrolled. Skipped %d employee(s).", skippedCount)
                : "No employees were enrolled.";
    }

    private int participantStatusPriority(CompanyProgramParticipant.ParticipantStatus status) {
        if (status == null) {
            return Integer.MAX_VALUE;
        }
        return switch (status) {
            case ACTIVE -> 0;
            case ENROLLED -> 1;
            case COMPLETED -> 2;
            case WITHDRAWN -> 3;
        };
    }

    private CompanyProgramParticipantDto toDto(CompanyProgramParticipant participant,
                                               MentorAssignmentSummaryDto mentorAssignment,
                                               MatchWorkspaceSummaryDto matchWorkspace,
                                               ParticipantConsentSummaryDto consentSummary) {
        Profile profile = participant.getProfile();
        return CompanyProgramParticipantDto.builder()
                .id(participant.getId())
                .companyProgramId(participant.getCompanyProgram() != null ? participant.getCompanyProgram().getId() : null)
                .profileId(profile != null ? profile.getId() : null)
                .profileName(buildDisplayName(profile))
                .profileEmail(profile != null ? profile.getEmail() : null)
                .profileRole(profile != null ? profile.getRole() : null)
                .department(buildDepartment(profile))
                .status(participant.getStatus())
                .consentSummary(consentSummary)
                .mentorAssignment(mentorAssignment)
                .matchWorkspace(matchWorkspace)
                .enrolledAt(participant.getEnrolledAt())
                .enrolledByUserId(participant.getEnrolledByUserId())
                .version(participant.getVersion())
                .createdAt(participant.getCreatedAt())
                .updatedAt(participant.getUpdatedAt())
                .build();
    }

    private EmployeeCompanyProgramDto toEmployeeProgramDto(CompanyProgramParticipant participant,
                                                           ParticipantConsentSummaryDto consentSummary) {
        CompanyProgram companyProgram = participant.getCompanyProgram();
        Program anchorProgram = CompanyProgramCatalogSupport.anchorProgram(companyProgram);

        return EmployeeCompanyProgramDto.builder()
                .participantId(participant.getId())
                .participantStatus(participant.getStatus())
                .enrolledAt(participant.getEnrolledAt())
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyId(companyProgram != null && companyProgram.getCompany() != null ? companyProgram.getCompany().getId() : null)
                .companyName(companyProgram != null && companyProgram.getCompany() != null ? companyProgram.getCompany().getName() : null)
                .templateProgramId(anchorProgram != null ? anchorProgram.getId() : null)
                .templateProgramName(anchorProgram != null ? anchorProgram.getName() : null)
                .catalogJourneySummary(CompanyProgramCatalogSupport.buildJourneySummary(companyProgram))
                .catalogStages(CompanyProgramCatalogSupport.toStageDtos(companyProgram))
                .journeyTemplateId(companyProgram != null && companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getId() : null)
                .journeyTemplateName(companyProgram != null && companyProgram.getJourneyTemplate() != null ? companyProgram.getJourneyTemplate().getName() : null)
                .name(companyProgram != null ? companyProgram.getName() : null)
                .objective(companyProgram != null ? companyProgram.getObjective() : null)
                .targetAudienceDescription(companyProgram != null ? companyProgram.getTargetAudienceDescription() : null)
                .status(companyProgram != null ? companyProgram.getStatus() : null)
                .matchingMode(companyProgram != null ? companyProgram.getMatchingMode() : null)
                .consentSummary(consentSummary)
                .maxParticipants(companyProgram != null ? companyProgram.getMaxParticipants() : null)
                .startsAt(companyProgram != null ? companyProgram.getStartsAt() : null)
                .endsAt(companyProgram != null ? companyProgram.getEndsAt() : null)
                .build();
    }

    private String buildDisplayName(Profile profile) {
        if (profile == null) {
            return "Employee";
        }

        String firstName = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }
        return "Employee";
    }

    private String buildDepartment(Profile profile) {
        if (profile == null) {
            return null;
        }
        if (profile.getIndustry() != null && !profile.getIndustry().isBlank()) {
            return profile.getIndustry();
        }
        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            return profile.getLocation();
        }
        return null;
    }

    private LocalDate resolveEndDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null) {
            return endDate;
        }
        if (startDate != null) {
            return startDate.plusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        return LocalDate.now(ZoneId.systemDefault());
    }

    private LocalDate resolveStartDate(LocalDate startDate, LocalDate resolvedEndDate) {
        LocalDate resolvedStartDate = startDate != null
                ? startDate
                : resolvedEndDate.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (resolvedEndDate.isBefore(resolvedStartDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        return resolvedStartDate;
    }
}
