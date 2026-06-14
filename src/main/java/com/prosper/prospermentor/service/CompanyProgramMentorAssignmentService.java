package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyProgramMentorCandidateDto;
import com.prosper.prospermentor.dto.EmployeeCompanyProgramMatchDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.*;
import com.prosper.prospermentor.service.support.CompanyProgramCatalogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CompanyProgramMentorAssignmentService {

    private static final EnumSet<CompanyProgram.CompanyProgramStatus> ASSIGNABLE_PROGRAM_STATUSES = EnumSet.of(
            CompanyProgram.CompanyProgramStatus.DRAFT,
            CompanyProgram.CompanyProgramStatus.LIVE,
            CompanyProgram.CompanyProgramStatus.PAUSED
    );

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> ASSIGNABLE_PARTICIPANT_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE
    );

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> VISIBLE_EMPLOYEE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final CompanyProgramMentorAssignmentRepository assignmentRepository;
    private final CompanyProgramParticipantRepository participantRepository;
    private final CompanyProgramRepository companyProgramRepository;
    private final ProgramMentorRepository programMentorRepository;
    private final ProfileRepository profileRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional(readOnly = true)
    public List<CompanyProgramMentorCandidateDto> getMentorCandidates(UUID companyProgramId, String search) {
        CompanyProgram companyProgram = companyProgramRepository.findById(companyProgramId)
                .orElseThrow(() -> new NoSuchElementException("Company program not found"));

        MentorCandidateSource source = companyProgram.getProgram() != null
                ? MentorCandidateSource.PROGRAM_POOL
                : MentorCandidateSource.GLOBAL_POOL;

        Set<UUID> candidateMentorIds = resolveCandidateMentorIds(companyProgram);
        if (candidateMentorIds.isEmpty()) {
            return List.of();
        }

        String normalizedSearch = normalizeSearch(search);
        return buildMentorCandidateDtos(candidateMentorIds, normalizedSearch, source);
    }

    public ApiResponse<MentorAssignmentSummaryDto> assignMentor(UUID participantId, UUID mentorId, UUID assignedByUserId) {
        return assignMentor(participantId, mentorId, assignedByUserId, true, null);
    }

    public ApiResponse<MentorAssignmentSummaryDto> assignMarketplaceMentor(UUID participantId, UUID mentorId, UUID assignedByUserId) {
        return assignMentor(participantId, mentorId, assignedByUserId, false, null);
    }

    public ApiResponse<MentorAssignmentSummaryDto> assignMarketplaceMentor(UUID participantId,
                                                                           UUID mentorId,
                                                                           UUID assignedByUserId,
                                                                           JourneyInstanceStep journeyInstanceStep) {
        return assignMentor(participantId, mentorId, assignedByUserId, false, journeyInstanceStep);
    }

    private ApiResponse<MentorAssignmentSummaryDto> assignMentor(UUID participantId,
                                                                 UUID mentorId,
                                                                 UUID assignedByUserId,
                                                                 boolean requireCompanyProgramCandidate,
                                                                 JourneyInstanceStep journeyInstanceStep) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        CompanyProgram companyProgram = participant.getCompanyProgram();
        if (!ASSIGNABLE_PROGRAM_STATUSES.contains(companyProgram.getStatus())) {
            return ApiResponse.error("Mentors can only be assigned for draft, live, or paused company programs");
        }

        if (!ASSIGNABLE_PARTICIPANT_STATUSES.contains(participant.getStatus())) {
            return ApiResponse.error("Mentors can only be assigned to enrolled or active participants");
        }

        if (requireCompanyProgramCandidate) {
            Set<UUID> candidateMentorIds = resolveCandidateMentorIds(companyProgram);
            if (!candidateMentorIds.contains(mentorId)) {
                return ApiResponse.error("Selected mentor is not available for this company program");
            }
        }

        Profile mentor = profileRepository.findById(mentorId)
                .orElseThrow(() -> new NoSuchElementException("Mentor not found"));
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentorId)
                .orElseThrow(() -> new NoSuchElementException("Mentor profile not found"));

        if (!isMentorProfile(mentor) || !Boolean.TRUE.equals(mentorProfile.getIsAvailable())) {
            return ApiResponse.error("Selected mentor is not currently assignable");
        }

        CompanyProgramMentorAssignment assignment = findAssignmentForScope(participantId, journeyInstanceStep)
                .orElseGet(CompanyProgramMentorAssignment::new);
        assignment.setParticipant(participant);
        assignment.setMentor(mentor);
        assignment.setJourneyInstanceStep(journeyInstanceStep);
        assignment.setAssignedByUserId(assignedByUserId);
        assignment.setAssignedAt(LocalDateTime.now());

        CompanyProgramMentorAssignment saved = assignmentRepository.save(assignment);
        log.info("Assigned mentor {} to participant {} for company program {}",
                mentorId, participantId, companyProgram.getId());

        return ApiResponse.success("Mentor assigned successfully", toAssignmentSummary(saved));
    }

    public ApiResponse<Void> removeMentorAssignment(UUID participantId) {
        CompanyProgramMentorAssignment assignment = assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId)
                .orElseThrow(() -> new NoSuchElementException("Mentor assignment not found"));

        assignmentRepository.delete(assignment);
        log.info("Removed mentor assignment {} for participant {}", assignment.getId(), participantId);
        return ApiResponse.success("Mentor assignment removed successfully");
    }

    @Transactional(readOnly = true)
    public Map<UUID, MentorAssignmentSummaryDto> getAssignmentSummaries(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return Map.of();
        }

        return assignmentRepository.findByParticipant_IdInAndJourneyInstanceStepIsNull(participantIds).stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getParticipant().getId(),
                        this::toAssignmentSummary,
                        (first, second) -> first
                ));
    }

    @Transactional(readOnly = true)
    public Map<UUID, MentorAssignmentSummaryDto> getStepAssignmentSummaries(Collection<UUID> participantIds,
                                                                            Collection<UUID> journeyInstanceStepIds) {
        if (participantIds == null || participantIds.isEmpty()
                || journeyInstanceStepIds == null || journeyInstanceStepIds.isEmpty()) {
            return Map.of();
        }

        return assignmentRepository.findByParticipant_IdInAndJourneyInstanceStep_IdIn(participantIds, journeyInstanceStepIds).stream()
                .filter(assignment -> assignment.getJourneyInstanceStep() != null
                        && assignment.getJourneyInstanceStep().getId() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getJourneyInstanceStep().getId(),
                        this::toAssignmentSummary,
                        (first, second) -> first
                ));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompanyProgramMatchDto> getEmployeeProgramMatches(UUID profileId) {
        List<CompanyProgramParticipant> participants = participantRepository.findByProfileIdAndStatusIn(
                profileId,
                VISIBLE_EMPLOYEE_STATUSES
        );

        Map<UUID, MentorAssignmentSummaryDto> assignmentsByParticipantId = getAssignmentSummaries(
                participants.stream().map(CompanyProgramParticipant::getId).toList()
        );

        return participants.stream()
                .sorted(Comparator.comparing(
                        (CompanyProgramParticipant participant) -> {
                            CompanyProgram companyProgram = participant.getCompanyProgram();
                            return companyProgram != null && companyProgram.getStartsAt() != null
                                    ? companyProgram.getStartsAt()
                                    : participant.getEnrolledAt();
                        },
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(participant -> toEmployeeProgramMatchDto(
                        participant,
                        assignmentsByParticipantId.get(participant.getId())
                ))
                .toList();
    }

    private Set<UUID> resolveCandidateMentorIds(CompanyProgram companyProgram) {
        List<UUID> catalogProgramIds = CompanyProgramCatalogSupport.orderedProgramIds(companyProgram);
        if (!catalogProgramIds.isEmpty()) {
            List<UUID> templateMentorIds = programMentorRepository.findMentorIdsByProgramIdIn(catalogProgramIds);
            if (!templateMentorIds.isEmpty()) {
                return templateMentorIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }

        return mentorProfileRepository.findByIsAvailableTrue().stream()
                .map(MentorProfile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<CompanyProgramMentorCandidateDto> buildMentorCandidateDtos(Set<UUID> candidateMentorIds,
                                                                            String search,
                                                                            MentorCandidateSource source) {
        List<Profile> mentorProfiles = profileRepository.findAllById(candidateMentorIds).stream()
                .filter(this::isMentorProfile)
                .toList();

        Map<UUID, MentorProfile> mentorDetailsById = mentorProfileRepository.findAllById(candidateMentorIds).stream()
                .filter(details -> Boolean.TRUE.equals(details.getIsAvailable()))
                .collect(Collectors.toMap(MentorProfile::getId, details -> details));

        return mentorProfiles.stream()
                .filter(profile -> mentorDetailsById.containsKey(profile.getId()))
                .filter(profile -> matchesSearch(profile, mentorDetailsById.get(profile.getId()), search))
                .map(profile -> toMentorCandidateDto(profile, mentorDetailsById.get(profile.getId()), source))
                .sorted(Comparator
                        .comparing((CompanyProgramMentorCandidateDto candidate) -> candidate.getRating() == null ? java.math.BigDecimal.ZERO : candidate.getRating())
                        .reversed()
                        .thenComparing(candidate -> Optional.ofNullable(candidate.getTotalSessions()).orElse(0), Comparator.reverseOrder())
                        .thenComparing(CompanyProgramMentorCandidateDto::getMentorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private boolean matchesSearch(Profile mentor, MentorProfile mentorProfile, String search) {
        if (search == null) {
            return true;
        }

        List<String> fields = new ArrayList<>();
        fields.add(buildDisplayName(mentor));
        fields.add(mentor.getEmail());
        fields.add(mentorProfile != null ? mentorProfile.getTitle() : null);
        fields.add(mentorProfile != null ? mentorProfile.getCompany() : null);
        if (mentorProfile != null && mentorProfile.getSpecializations() != null) {
            fields.addAll(mentorProfile.getSpecializations());
        }

        return fields.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(search));
    }

    private boolean isMentorProfile(Profile profile) {
        return profile != null
                && profile.getRole() != null
                && "mentor".equalsIgnoreCase(profile.getRole().trim());
    }

    private String normalizeSearch(String search) {
        return search != null && !search.trim().isBlank()
                ? search.trim().toLowerCase(Locale.ROOT)
                : null;
    }

    private String buildDisplayName(Profile profile) {
        if (profile == null) {
            return "Mentor";
        }
        String fullName = String.join(" ",
                Optional.ofNullable(profile.getFirstName()).orElse("").trim(),
                Optional.ofNullable(profile.getLastName()).orElse("").trim()
        ).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }
        return "Mentor";
    }

    private CompanyProgramMentorCandidateDto toMentorCandidateDto(Profile mentor,
                                                                  MentorProfile mentorProfile,
                                                                  MentorCandidateSource source) {
        return CompanyProgramMentorCandidateDto.builder()
                .mentorId(mentor.getId())
                .mentorName(buildDisplayName(mentor))
                .mentorEmail(mentor.getEmail())
                .title(mentorProfile != null ? mentorProfile.getTitle() : null)
                .company(mentorProfile != null ? mentorProfile.getCompany() : null)
                .yearsExperience(mentorProfile != null ? mentorProfile.getYearsExperience() : null)
                .rating(mentorProfile != null ? mentorProfile.getRating() : null)
                .totalSessions(mentorProfile != null ? mentorProfile.getTotalSessions() : null)
                .avatarUrl(mentorProfile != null && mentorProfile.getAvatarUrl() != null ? mentorProfile.getAvatarUrl() : mentor.getAvatarUrl())
                .specializations(mentorProfile != null ? mentorProfile.getSpecializations() : List.of())
                .isAvailable(mentorProfile != null ? mentorProfile.getIsAvailable() : null)
                .source(source.name())
                .build();
    }

    public MentorAssignmentSummaryDto toAssignmentSummary(CompanyProgramMentorAssignment assignment) {
        Profile mentor = assignment.getMentor();
        MentorProfile mentorProfile = assignment.getMentorProfile();

        return MentorAssignmentSummaryDto.builder()
                .assignmentId(assignment.getId())
                .journeyInstanceStepId(assignment.getJourneyInstanceStep() != null ? assignment.getJourneyInstanceStep().getId() : null)
                .mentorId(mentor != null ? mentor.getId() : null)
                .mentorName(buildDisplayName(mentor))
                .mentorEmail(mentor != null ? mentor.getEmail() : null)
                .title(mentorProfile != null ? mentorProfile.getTitle() : null)
                .company(mentorProfile != null ? mentorProfile.getCompany() : null)
                .yearsExperience(mentorProfile != null ? mentorProfile.getYearsExperience() : null)
                .rating(mentorProfile != null ? mentorProfile.getRating() : null)
                .totalSessions(mentorProfile != null ? mentorProfile.getTotalSessions() : null)
                .avatarUrl(mentorProfile != null && mentorProfile.getAvatarUrl() != null ? mentorProfile.getAvatarUrl() : mentor != null ? mentor.getAvatarUrl() : null)
                .specializations(mentorProfile != null ? mentorProfile.getSpecializations() : List.of())
                .isAvailable(mentorProfile != null ? mentorProfile.getIsAvailable() : null)
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private Optional<CompanyProgramMentorAssignment> findAssignmentForScope(UUID participantId,
                                                                            JourneyInstanceStep journeyInstanceStep) {
        if (journeyInstanceStep != null && journeyInstanceStep.getId() != null) {
            return assignmentRepository.findByParticipant_IdAndJourneyInstanceStep_Id(
                    participantId,
                    journeyInstanceStep.getId()
            );
        }
        return assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(participantId);
    }

    private EmployeeCompanyProgramMatchDto toEmployeeProgramMatchDto(CompanyProgramParticipant participant,
                                                                     MentorAssignmentSummaryDto assignmentSummary) {
        CompanyProgram companyProgram = participant.getCompanyProgram();
        Program anchorProgram = CompanyProgramCatalogSupport.anchorProgram(companyProgram);

        return EmployeeCompanyProgramMatchDto.builder()
                .participantId(participant.getId())
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyId(companyProgram != null && companyProgram.getCompany() != null ? companyProgram.getCompany().getId() : null)
                .companyName(companyProgram != null && companyProgram.getCompany() != null ? companyProgram.getCompany().getName() : null)
                .programName(companyProgram != null ? companyProgram.getName() : null)
                .templateProgramName(anchorProgram != null ? anchorProgram.getName() : null)
                .catalogJourneySummary(CompanyProgramCatalogSupport.buildJourneySummary(companyProgram))
                .catalogStages(CompanyProgramCatalogSupport.toStageDtos(companyProgram))
                .programStatus(companyProgram != null ? companyProgram.getStatus() : null)
                .matchingMode(companyProgram != null ? companyProgram.getMatchingMode() : null)
                .participantStatus(participant.getStatus())
                .startsAt(companyProgram != null ? companyProgram.getStartsAt() : null)
                .endsAt(companyProgram != null ? companyProgram.getEndsAt() : null)
                .mentorAssignment(assignmentSummary)
                .build();
    }

    private enum MentorCandidateSource {
        PROGRAM_POOL,
        GLOBAL_POOL
    }
}
