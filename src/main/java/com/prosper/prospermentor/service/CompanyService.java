package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyEmployeeWhitelistRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.ProgramRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.service.notification.CompanyNotificationService;
import com.prosper.prospermentor.specification.CompanyEmployeeWhitelistSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service for managing companies
 */
@Service
@Slf4j
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ProgramRepository programRepository;
    private final ProfileRepository profileRepository;
    private final CompanyNotificationService companyNotificationService;
    private final CompanyEmployeeWhitelistRepository whitelistRepository;
    private final ProfileService profileService;
    private final SessionRepository sessionRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final ReviewRequestRepository reviewRequestRepository;

    public CompanyService(CompanyRepository companyRepository,
                         ProgramRepository programRepository,
                         ProfileRepository profileRepository,
                         CompanyNotificationService companyNotificationService,
                         CompanyEmployeeWhitelistRepository whitelistRepository,
                         ProfileService profileService,
                         SessionRepository sessionRepository,
                         ReviewCycleRepository reviewCycleRepository,
                         ReviewRequestRepository reviewRequestRepository) {
        this.companyRepository = companyRepository;
        this.programRepository = programRepository;
        this.profileRepository = profileRepository;
        this.companyNotificationService = companyNotificationService;
        this.whitelistRepository = whitelistRepository;
        this.profileService = profileService;
        this.sessionRepository = sessionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.reviewRequestRepository = reviewRequestRepository;
    }

    /**
     * Get all companies
     */
    @Transactional(readOnly = true)
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    /**
     * Get all active companies
     */
    @Transactional(readOnly = true)
    public List<Company> getActiveCompanies() {
        return companyRepository.findByIsActive(true);
    }

    /**
     * Get company by ID
     */
    @Transactional(readOnly = true)
    public Optional<Company> getCompanyById(UUID id) {
        return companyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public ApiResponse<CompanyOnboardingStatusDto> getCompanyOnboardingStatus(UUID companyId) {
        return companyRepository.findById(companyId)
                .map(company -> ApiResponse.success(
                        "Company onboarding status retrieved successfully",
                        buildCompanyOnboardingStatus(company)
                ))
                .orElseGet(() -> ApiResponse.error("Company not found"));
    }

    public ApiResponse<CompanyOnboardingStatusDto> updateCompanyOnboarding(UUID companyId,
                                                                            UpdateCompanyOnboardingRequest request) {
        Optional<Company> companyOpt = companyRepository.findByIdWithRecommendedPrograms(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        company.setIndustry(normalizeNullable(request.getIndustry()));
        company.setCompanySizeBand(normalizeNullable(request.getCompanySizeBand()));
        company.setCountry(normalizeNullable(request.getCountry()));
        company.setTimezone(normalizeNullable(request.getTimezone()));
        if (request.getMentorshipObjective() != null) {
            company.setMentorshipObjective(normalizeNullable(request.getMentorshipObjective()));
        }
        if (request.getTargetAudienceDescription() != null) {
            company.setTargetAudienceDescription(normalizeNullable(request.getTargetAudienceDescription()));
        }
        if (request.getProgramDesignPreference() != null) {
            company.setProgramDesignPreference(normalizeNullable(request.getProgramDesignPreference()));
        }

        if (request.getRecommendedProgramIds() != null) {
            List<UUID> normalizedProgramIds = request.getRecommendedProgramIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<Program> programs = normalizedProgramIds.isEmpty()
                    ? List.of()
                    : programRepository.findByIdInOrderByOrderIdAsc(normalizedProgramIds);

            if (programs.size() != normalizedProgramIds.size()) {
                return ApiResponse.error("One or more selected programs were not found");
            }

            List<String> invalidPrograms = programs.stream()
                    .filter(program -> program.getStatus() != Program.ProgramStatus.LIVE)
                    .map(Program::getName)
                    .toList();

            if (!invalidPrograms.isEmpty()) {
                return ApiResponse.error("Only LIVE programs can be selected. Invalid programs: " + String.join(", ", invalidPrograms));
            }

            company.getRecommendedPrograms().clear();
            company.getRecommendedPrograms().addAll(sortProgramsByOrderId(programs));
        }

        List<String> missingFields = missingRequiredOnboardingFields(company);
        company.setOnboardingCompleted(missingFields.isEmpty());
        company.setOnboardingCompletedAt(missingFields.isEmpty() ? LocalDateTime.now() : null);

        Company savedCompany = companyRepository.save(company);

        return ApiResponse.success(
                missingFields.isEmpty()
                        ? "Company onboarding completed successfully"
                        : "Company onboarding saved with missing fields",
                buildCompanyOnboardingStatus(savedCompany)
        );
    }

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getRecommendedPrograms(UUID companyId) {
        Optional<Company> companyOpt = companyRepository.findByIdWithRecommendedPrograms(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        List<Program> programs = sortProgramsByOrderId(
                company.getRecommendedPrograms().stream()
                        .filter(program -> program.getStatus() == Program.ProgramStatus.LIVE)
                        .toList()
        );

        return ApiResponse.success("Recommended programs retrieved successfully",
                buildRecommendedProgramsPayload(company, programs));
    }

    public ApiResponse<Map<String, Object>> updateRecommendedPrograms(UUID companyId, List<UUID> programIds) {
        Optional<Company> companyOpt = companyRepository.findByIdWithRecommendedPrograms(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        List<UUID> normalizedProgramIds = programIds == null
                ? List.of()
                : programIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Program> programs = normalizedProgramIds.isEmpty()
                ? new ArrayList<>()
                : programRepository.findByIdInOrderByOrderIdAsc(normalizedProgramIds);

        if (programs.size() != normalizedProgramIds.size()) {
            return ApiResponse.error("One or more selected programs were not found");
        }

        List<String> invalidPrograms = programs.stream()
                .filter(program -> program.getStatus() != Program.ProgramStatus.LIVE)
                .map(Program::getName)
                .toList();

        if (!invalidPrograms.isEmpty()) {
            return ApiResponse.error("Only LIVE programs can be recommended. Invalid programs: " + String.join(", ", invalidPrograms));
        }

        company.getRecommendedPrograms().clear();
        company.getRecommendedPrograms().addAll(sortProgramsByOrderId(programs));

        Company updatedCompany = companyRepository.save(company);

        return ApiResponse.success("Recommended programs updated successfully",
                buildRecommendedProgramsPayload(updatedCompany, updatedCompany.getRecommendedPrograms()));
    }

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getCompanySessions(UUID companyId) {
        return getCompanySessions(companyId, null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getCompanySessions(UUID companyId,
                                                               List<String> statuses,
                                                               List<String> departments,
                                                               ZonedDateTime startDate,
                                                               ZonedDateTime endDate,
                                                               String search,
                                                               Integer page,
                                                               Integer size) {
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        List<Profile> companyProfiles = new ArrayList<>(profileRepository.findByCompanyId(companyId));
        List<Profile> employeeProfiles = companyProfiles.stream()
                .filter(this::isEmployeeProfile)
                .toList();

        List<UUID> employeeIds = employeeProfiles.stream()
                .map(Profile::getId)
                .toList();

        List<Session> companySessions = employeeIds.isEmpty()
                ? List.of()
                : sessionRepository.findByMenteeIdIn(employeeIds);

        CompanySessionsContext context = buildCompanySessionsContext(employeeProfiles, companySessions);
        CompanySessionsQuery query = CompanySessionsQuery.of(statuses, departments, startDate, endDate, search);

        List<CompanySessionDto> filteredSessions = context.sessions().stream()
                .filter(session -> matchesSessionQuery(session, query))
                .toList();

        PageSlice<CompanySessionDto> pageSlice = paginate(filteredSessions, page, size);

        Set<UUID> visibleSessionIds = filteredSessions.stream()
                .map(CompanySessionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Session> filteredDomainSessions = companySessions.stream()
                .filter(session -> visibleSessionIds.contains(session.getId()))
                .toList();

        FeedbackQueueSummary feedbackQueue = buildFeedbackQueueSummary(filteredDomainSessions, context.employeesById(), context.mentorsById());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", company.getId());
        data.put("companyName", company.getName());
        data.put("sessions", pageSlice.items());
        data.put("count", filteredSessions.size());
        data.put("totalCount", filteredSessions.size());
        data.put("displayedCount", pageSlice.items().size());
        data.put("pagination", pageSlice.toMap());
        data.put("summary", buildSessionSummaryMetrics(filteredDomainSessions));
        data.put("recentCancellations", buildRecentCancellations(filteredDomainSessions, context.employeesById(), context.mentorsById()));
        data.put("pendingFeedback", Map.of(
                "requiredCount", feedbackQueue.requiredCount(),
                "items", feedbackQueue.items()
        ));
        data.put("appliedFilters", query.toMap());

        return ApiResponse.success("Company sessions retrieved successfully", data);
    }

    private CompanySessionsContext buildCompanySessionsContext(List<Profile> employeeProfiles, List<Session> sessions) {
        Map<UUID, Profile> employeesById = employeeProfiles.stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        Set<UUID> mentorIds = sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Profile> mentorsById = mentorIds.isEmpty()
                ? Map.of()
                : profileRepository.findAllById(mentorIds).stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        List<CompanySessionDto> normalizedSessions = sessions.stream()
                .sorted(Comparator.comparing(
                        (Session session) -> Optional.ofNullable(session.getScheduledStart()).orElse(ZonedDateTime.parse("1970-01-01T00:00:00Z")),
                        Comparator.reverseOrder()
                ))
                .map(session -> toCompanySessionDto(session, employeesById.get(session.getMenteeId()), mentorsById.get(session.getMentorId())))
                .toList();

        return new CompanySessionsContext(normalizedSessions, employeesById, mentorsById);
    }

    private boolean matchesSessionQuery(CompanySessionDto session, CompanySessionsQuery query) {
        if (session == null) {
            return false;
        }

        if (!query.statuses().isEmpty()) {
            String status = normalizeFilterToken(session.getStatus());
            if (!query.statuses().contains(status)) {
                return false;
            }
        }

        if (!query.departments().isEmpty()) {
            String department = normalizeFilterToken(session.getDepartment());
            if (!query.departments().contains(department)) {
                return false;
            }
        }

        if (query.startDate() != null) {
            ZonedDateTime scheduledStart = session.getScheduledStart();
            if (scheduledStart == null || scheduledStart.isBefore(query.startDate())) {
                return false;
            }
        }

        if (query.endDate() != null) {
            ZonedDateTime scheduledStart = session.getScheduledStart();
            if (scheduledStart == null || scheduledStart.isAfter(query.endDate())) {
                return false;
            }
        }

        if (query.search() != null && !query.search().isBlank()) {
            String normalizedSearch = query.search();
            Predicate<String> containsTerm = value ->
                    value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);

            return containsTerm.test(session.getEmployeeName())
                    || containsTerm.test(session.getEmployeeEmail())
                    || containsTerm.test(session.getMentorName())
                    || containsTerm.test(session.getDepartment())
                    || containsTerm.test(session.getTitle())
                    || containsTerm.test(session.getDescription());
        }

        return true;
    }

    private PageSlice<CompanySessionDto> paginate(List<CompanySessionDto> sessions, Integer page, Integer size) {
        int totalItems = sessions.size();
        boolean paginationApplied = page != null && size != null && size > 0;

        if (!paginationApplied) {
            return new PageSlice<>(
                    sessions,
                    0,
                    Math.max(totalItems, 1),
                    totalItems,
                    1,
                    false,
                    false
            );
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safeSize));
        int fromIndex = Math.min(safePage * safeSize, totalItems);
        int toIndex = Math.min(fromIndex + safeSize, totalItems);
        boolean hasPrevious = safePage > 0;
        boolean hasNext = safePage + 1 < totalPages;

        return new PageSlice<>(
                sessions.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                totalItems,
                totalPages,
                hasNext,
                hasPrevious
        );
    }

    private Map<String, Object> buildSessionSummaryMetrics(List<Session> sessions) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime sevenDaysLater = now.plusDays(7);
        ZonedDateTime thirtyDaysAgo = now.minusDays(30);
        int currentYear = now.getYear();

        List<Session> upcoming = sessions.stream()
                .filter(session -> session.getScheduledStart() != null)
                .filter(session -> !session.getScheduledStart().isBefore(now) && !session.getScheduledStart().isAfter(sevenDaysLater))
                .filter(session -> session.getStatus() != null)
                .filter(session -> session.getStatus() == Session.SessionStatus.SCHEDULED
                        || session.getStatus() == Session.SessionStatus.CONFIRMED
                        || session.getStatus() == Session.SessionStatus.PENDING
                        || session.getStatus() == Session.SessionStatus.IN_PROGRESS)
                .toList();

        List<Session> completedThisYear = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .filter(session -> session.getScheduledStart() != null)
                .filter(session -> session.getScheduledStart().getYear() == currentYear)
                .toList();

        List<Session> cancelledLast30Days = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.CANCELLED
                        || session.getStatus() == Session.SessionStatus.NO_SHOW)
                .filter(session -> {
                    if (session.getCancelledAt() != null) {
                        return !session.getCancelledAt().atZone(now.getZone()).isBefore(thirtyDaysAgo);
                    }
                    return session.getScheduledStart() != null && !session.getScheduledStart().isBefore(thirtyDaysAgo);
                })
                .toList();

        List<Integer> ratings = sessions.stream()
                .map(Session::getRating)
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .toList();

        double averageRating = ratings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0d);

        int roundedAverageRating = (int) Math.round(averageRating * 10);
        int cancellationRate = sessions.isEmpty()
                ? 0
                : (int) Math.round((cancelledLast30Days.size() * 100.0d) / sessions.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("upcomingCount", upcoming.size());
        summary.put("completedCount", completedThisYear.size());
        summary.put("cancelledCount", cancelledLast30Days.size());
        summary.put("avgRating", BigDecimal.valueOf(roundedAverageRating).movePointLeft(1));
        summary.put("cancellationRate", cancellationRate);
        summary.put("totalSessions", sessions.size());
        return summary;
    }

    private List<Map<String, Object>> buildRecentCancellations(List<Session> sessions,
                                                               Map<UUID, Profile> employeesById,
                                                               Map<UUID, Profile> mentorsById) {
        return sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.CANCELLED
                        || session.getStatus() == Session.SessionStatus.NO_SHOW)
                .sorted(Comparator
                        .comparing((Session session) -> Optional.ofNullable(session.getCancelledAt()).orElse(LocalDateTime.MIN), Comparator.reverseOrder())
                        .thenComparing(session -> Optional.ofNullable(session.getScheduledStart()).orElse(ZonedDateTime.parse("1970-01-01T00:00:00Z")), Comparator.reverseOrder()))
                .limit(10)
                .map(session -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("sessionId", session.getId());
                    entry.put("employeeName", buildDisplayName(employeesById.get(session.getMenteeId()), "Employee"));
                    entry.put("mentorName", buildDisplayName(mentorsById.get(session.getMentorId()), "Mentor"));
                    entry.put("title", session.getTitle());
                    entry.put("status", session.getStatus() != null ? session.getStatus().name() : null);
                    entry.put("cancelledAt", session.getCancelledAt());
                    entry.put("cancellationReason", session.getCancellationReason());
                    entry.put("cancelledBy", session.getCancelledBy() != null ? session.getCancelledBy().name() : null);
                    return entry;
                })
                .toList();
    }

    private FeedbackQueueSummary buildFeedbackQueueSummary(List<Session> sessions,
                                                           Map<UUID, Profile> employeesById,
                                                           Map<UUID, Profile> mentorsById) {
        List<UUID> completedSessionIds = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .map(Session::getId)
                .filter(Objects::nonNull)
                .toList();

        if (completedSessionIds.isEmpty()) {
            return new FeedbackQueueSummary(0, List.of());
        }

        List<ReviewCycle> cycles = reviewCycleRepository.findBySession_IdInAndTypeOrderByCreatedAtDesc(
                completedSessionIds,
                ReviewCycle.ReviewType.SESSION
        );

        if (cycles.isEmpty()) {
            return new FeedbackQueueSummary(0, List.of());
        }

        List<UUID> cycleIds = cycles.stream()
                .map(ReviewCycle::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, List<ReviewRequest>> requestsByCycleId = reviewRequestRepository
                .findByReviewCycle_IdInOrderByCreatedAtAsc(cycleIds)
                .stream()
                .filter(request -> request.getReviewCycle() != null && request.getReviewCycle().getId() != null)
                .collect(Collectors.groupingBy(request -> request.getReviewCycle().getId()));

        List<Map<String, Object>> pendingItems = new ArrayList<>();
        int requiredCount = 0;

        for (ReviewCycle cycle : cycles) {
            if (cycle == null || cycle.getId() == null || cycle.getSession() == null) {
                continue;
            }

            if (cycle.getStatus() != ReviewCycle.ReviewCycleStatus.OPEN
                    && cycle.getStatus() != ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED) {
                continue;
            }

            List<ReviewRequest> requests = requestsByCycleId.getOrDefault(cycle.getId(), List.of());
            List<ReviewRequest> pendingRequests = requests.stream()
                    .filter(request -> request.getStatus() == ReviewRequest.ReviewRequestStatus.PENDING
                            || request.getStatus() == ReviewRequest.ReviewRequestStatus.SENT
                            || request.getStatus() == ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED)
                    .toList();

            if (pendingRequests.isEmpty()) {
                continue;
            }

            requiredCount += pendingRequests.size();

            Session session = cycle.getSession();
            Profile mentorProfile = cycle.getMentorProfile() != null
                    ? cycle.getMentorProfile()
                    : mentorsById.get(session.getMentorId());
            Profile menteeProfile = cycle.getMenteeProfile() != null
                    ? cycle.getMenteeProfile()
                    : employeesById.get(session.getMenteeId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reviewCycleId", cycle.getId());
            item.put("sessionId", session.getId());
            item.put("mentorName", buildDisplayName(mentorProfile, "Mentor"));
            item.put("menteeName", buildDisplayName(menteeProfile, "Employee"));
            item.put("sessionTitle", session.getTitle());
            item.put("completedAt", session.getScheduledEnd());
            item.put("feedbackWindowExpiresAt", cycle.getExpiresAt());
            item.put("pendingRequestCount", pendingRequests.size());
            item.put("pendingRoles", pendingRequests.stream()
                    .map(ReviewRequest::getReviewerRole)
                    .filter(Objects::nonNull)
                    .map(Enum::name)
                    .distinct()
                    .toList());
            pendingItems.add(item);
        }

        pendingItems.sort(Comparator.comparing(item -> {
            Object expires = item.get("feedbackWindowExpiresAt");
            return expires instanceof LocalDateTime value ? value : LocalDateTime.MAX;
        }));

        return new FeedbackQueueSummary(requiredCount, pendingItems.stream().limit(20).toList());
    }

    private String normalizeFilterToken(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record CompanySessionsContext(List<CompanySessionDto> sessions,
                                          Map<UUID, Profile> employeesById,
                                          Map<UUID, Profile> mentorsById) {
    }

    private record FeedbackQueueSummary(int requiredCount, List<Map<String, Object>> items) {
    }

    private record PageSlice<T>(List<T> items,
                                int page,
                                int size,
                                int totalItems,
                                int totalPages,
                                boolean hasNext,
                                boolean hasPrevious) {
        Map<String, Object> toMap() {
            Map<String, Object> pagination = new LinkedHashMap<>();
            pagination.put("page", page);
            pagination.put("size", size);
            pagination.put("totalItems", totalItems);
            pagination.put("totalPages", totalPages);
            pagination.put("hasNext", hasNext);
            pagination.put("hasPrevious", hasPrevious);
            return pagination;
        }
    }

    private record CompanySessionsQuery(Set<String> statuses,
                                        Set<String> departments,
                                        ZonedDateTime startDate,
                                        ZonedDateTime endDate,
                                        String search) {
        static CompanySessionsQuery of(List<String> statuses,
                                       List<String> departments,
                                       ZonedDateTime startDate,
                                       ZonedDateTime endDate,
                                       String search) {
            Set<String> normalizedStatuses = statuses == null
                    ? Set.of()
                    : statuses.stream()
                    .filter(Objects::nonNull)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Set<String> normalizedDepartments = departments == null
                    ? Set.of()
                    : departments.stream()
                    .filter(Objects::nonNull)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

            return new CompanySessionsQuery(
                    normalizedStatuses,
                    normalizedDepartments,
                    startDate,
                    endDate,
                    normalizedSearch
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("statuses", statuses);
            filters.put("departments", departments);
            filters.put("startDate", startDate);
            filters.put("endDate", endDate);
            filters.put("search", search);
            return filters;
        }
    }

    private CompanyOnboardingStatusDto buildCompanyOnboardingStatus(Company company) {
        List<String> missingFields = missingRequiredOnboardingFields(company);
        boolean completed = missingFields.isEmpty();

        return CompanyOnboardingStatusDto.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .completed(completed)
                .missingFields(missingFields)
                .industry(company.getIndustry())
                .companySizeBand(company.getCompanySizeBand())
                .country(company.getCountry())
                .timezone(company.getTimezone())
                .mentorshipObjective(company.getMentorshipObjective())
                .targetAudienceDescription(company.getTargetAudienceDescription())
                .programDesignPreference(company.getProgramDesignPreference())
                .recommendedProgramIds(company.getRecommendedPrograms().stream()
                        .map(Program::getId)
                        .toList())
                .completedAt(company.getOnboardingCompletedAt())
                .build();
    }

    private List<String> missingRequiredOnboardingFields(Company company) {
        List<String> missingFields = new ArrayList<>();
        addMissingIfBlank(missingFields, "industry", company.getIndustry());
        addMissingIfBlank(missingFields, "companySizeBand", company.getCompanySizeBand());
        addMissingIfBlank(missingFields, "country", company.getCountry());
        addMissingIfBlank(missingFields, "timezone", company.getTimezone());

        return missingFields;
    }

    private void addMissingIfBlank(List<String> missingFields, String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            missingFields.add(fieldName);
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Get company by email address
     */
    @Transactional(readOnly = true)
    public Optional<Company> getCompanyByEmail(String emailAddress) {
        return companyRepository.findByEmailAddress(emailAddress);
    }

    /**
     * Search companies by name
     */
    @Transactional(readOnly = true)
    public List<Company> searchCompaniesByName(String name) {
        return companyRepository.findByNameContainingIgnoreCase(name);
    }

    private Map<String, Object> buildRecommendedProgramsPayload(Company company, List<Program> programs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", company.getId());
        data.put("companyName", company.getName());
        data.put("programs", programs);
        data.put("count", programs.size());
        return data;
    }

    private List<Program> sortProgramsByOrderId(List<Program> programs) {
        return programs.stream()
                .sorted(Comparator.comparing(program -> Optional.ofNullable(program.getOrderId()).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private List<CompanySessionDto> buildCompanySessionsPayload(List<Profile> employeeProfiles, List<Session> sessions) {
        Map<UUID, Profile> employeesById = employeeProfiles.stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        Set<UUID> mentorIds = sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Profile> mentorsById = mentorIds.isEmpty()
                ? Map.of()
                : profileRepository.findAllById(mentorIds).stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        return sessions.stream()
                .sorted(Comparator.comparing(
                        (Session session) -> Optional.ofNullable(session.getScheduledStart()).orElse(ZonedDateTime.parse("1970-01-01T00:00:00Z")),
                        Comparator.reverseOrder()
                ))
                .map(session -> toCompanySessionDto(session, employeesById.get(session.getMenteeId()), mentorsById.get(session.getMentorId())))
                .toList();
    }

    private CompanySessionDto toCompanySessionDto(Session session, Profile employeeProfile, Profile mentorProfile) {
        return CompanySessionDto.builder()
                .id(session.getId())
                .employeeId(session.getMenteeId())
                .employeeName(buildDisplayName(employeeProfile, "Employee"))
                .employeeEmail(employeeProfile != null ? employeeProfile.getEmail() : null)
                .department(buildProfileDepartment(employeeProfile))
                .mentorId(session.getMentorId())
                .mentorName(buildDisplayName(mentorProfile, "Mentor"))
                .title(session.getTitle())
                .description(session.getDescription())
                .status(session.getStatus() != null ? session.getStatus().name() : null)
                .platform(session.getMeetingPlatform() != null ? session.getMeetingPlatform().name() : null)
                .platformDisplayName(session.getMeetingPlatform() != null ? session.getMeetingPlatform().getDisplayName() : null)
                .scheduledStart(session.getScheduledStart())
                .scheduledEnd(session.getScheduledEnd())
                .durationMin(session.getScheduledStart() != null && session.getScheduledEnd() != null
                        ? session.getDurationMinutes()
                        : 0L)
                .cancelledAt(session.getCancelledAt())
                .cancellationReason(session.getCancellationReason())
                .cancelledBy(session.getCancelledBy() != null ? session.getCancelledBy().name() : null)
                .rating(session.getRating())
                .cost(session.getPrice())
                .currency(session.getCurrency())
                .paid(session.getPaid())
                .build();
    }

    private boolean isEmployeeProfile(Profile profile) {
        String normalizedRole = normalizeRole(profile != null ? profile.getRole() : null);
        return "employee".equals(normalizedRole) || "mentee".equals(normalizedRole);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private String buildDisplayName(Profile profile, String fallback) {
        if (profile == null) {
            return fallback;
        }

        String firstName = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String fullName = String.join(" ", List.of(firstName, lastName)).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }

        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }

        return fallback;
    }

    private String buildProfileDepartment(Profile profile) {
        if (profile == null) {
            return "General";
        }

        if (profile.getIndustry() != null && !profile.getIndustry().isBlank()) {
            return profile.getIndustry();
        }

        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            return profile.getLocation();
        }

        return "General";
    }

    /**
     * Get companies with pagination and optional search/active filters.
     */
    @Transactional(readOnly = true)
    public Page<Company> getCompaniesPaginated(boolean activeOnly, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = search != null ? search.trim() : "";

        if (!normalizedSearch.isEmpty()) {
            return activeOnly
                    ? companyRepository.findByIsActiveAndNameContainingIgnoreCase(true, normalizedSearch, pageable)
                    : companyRepository.findByNameContainingIgnoreCase(normalizedSearch, pageable);
        }

        return activeOnly
                ? companyRepository.findByIsActive(true, pageable)
                : companyRepository.findAll(pageable);
    }

    /**
     * Create a new company
     */
    public ApiResponse<Company> createCompany(CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());

        // Check if email already exists
        if (companyRepository.existsByEmailAddress(request.getEmailAddress())) {
            log.warn("Company with email {} already exists", request.getEmailAddress());
            return ApiResponse.error("A company with this email address already exists");
        }

        // Check if name already exists
        Optional<Company> existingCompany = companyRepository.findByName(request.getName());
        if (existingCompany.isPresent()) {
            log.warn("Company with name {} already exists", request.getName());
            return ApiResponse.error("A company with this name already exists");
        }

        Company company = new Company();
        company.setName(request.getName());
        company.setEmailAddress(request.getEmailAddress());
        company.setPhoneNumber(request.getPhoneNumber());
        company.setLogoUrl(request.getLogoUrl());
        company.setWebsite(request.getWebsite());
        company.setDescription(request.getDescription());
        company.setAddress(request.getAddress());
        company.setCity(request.getCity());
        company.setCountry(request.getCountry());
        company.setIndustry(request.getIndustry());
        company.setCompanySizeBand(request.getCompanySizeBand());
        company.setTimezone(request.getTimezone());
        company.setMentorshipObjective(request.getMentorshipObjective());
        company.setTargetAudienceDescription(request.getTargetAudienceDescription());
        company.setProgramDesignPreference(request.getProgramDesignPreference());
        company.setPrimaryColor(request.getPrimaryColor());
        company.setSecondaryColor(request.getSecondaryColor());
        company.setIsActive(true);

        // Generate registration token
        String registrationToken = UUID.randomUUID().toString();
        company.setRegistrationToken(registrationToken);
        company.setRegistrationTokenExpiry(LocalDateTime.now().plusDays(7)); // Token expires in 7 days
        company.setRegistrationCompleted(false);

        Company savedCompany = companyRepository.save(company);
        log.info("Company created successfully with ID: {}", savedCompany.getId());

        // Send welcome email with registration link
        try {
            companyNotificationService.sendCompanyWelcomeEmail(savedCompany, registrationToken);
            log.info("Welcome email sent to company: {}", savedCompany.getEmailAddress());
        } catch (Exception e) {
            log.error("Failed to send welcome email to company {}: {}", savedCompany.getEmailAddress(), e.getMessage());
            // Don't fail the company creation if email fails
        }

        return ApiResponse.success("Company created successfully. Welcome email sent to " + savedCompany.getEmailAddress(), savedCompany);
    }

    public Company createPendingCompanyRegistration(CreateCompanyRequest request) {
        Optional<Company> existingCompany = companyRepository.findByEmailAddress(request.getEmailAddress());
        if (existingCompany.isPresent()) {
            Company company = existingCompany.get();
            if (Boolean.TRUE.equals(company.getRegistrationCompleted())) {
                throw new IllegalStateException("A company with this email address already exists");
            }
            if (company.getRegistrationToken() == null || company.getRegistrationToken().isBlank()) {
                company.setRegistrationToken(UUID.randomUUID().toString());
                company.setRegistrationTokenExpiry(LocalDateTime.now().plusDays(7));
            }
            return companyRepository.save(company);
        }

        Company company = new Company();
        company.setName(request.getName());
        company.setEmailAddress(request.getEmailAddress());
        company.setPhoneNumber(request.getPhoneNumber());
        company.setLogoUrl(request.getLogoUrl());
        company.setWebsite(request.getWebsite());
        company.setDescription(request.getDescription());
        company.setAddress(request.getAddress());
        company.setCity(request.getCity());
        company.setCountry(request.getCountry());
        company.setIndustry(request.getIndustry());
        company.setCompanySizeBand(request.getCompanySizeBand());
        company.setTimezone(request.getTimezone());
        company.setMentorshipObjective(request.getMentorshipObjective());
        company.setTargetAudienceDescription(request.getTargetAudienceDescription());
        company.setProgramDesignPreference(request.getProgramDesignPreference());
        company.setPrimaryColor(request.getPrimaryColor());
        company.setSecondaryColor(request.getSecondaryColor());
        company.setRegistrationToken(UUID.randomUUID().toString());
        company.setRegistrationTokenExpiry(LocalDateTime.now().plusDays(7));
        company.setRegistrationCompleted(false);
        company.setIsActive(true);
        return companyRepository.save(company);
    }

    /**
     * Update an existing company
     */
    public ApiResponse<Company> updateCompany(UUID id, UpdateCompanyRequest request) {
        log.info("Updating company: {}", id);

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", id);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();

        // Check if email is being updated and if it already exists
        if (request.getEmailAddress() != null &&
            !request.getEmailAddress().equals(company.getEmailAddress())) {
            if (companyRepository.existsByEmailAddress(request.getEmailAddress())) {
                log.warn("Email {} already exists for another company", request.getEmailAddress());
                return ApiResponse.error("A company with this email address already exists");
            }
            company.setEmailAddress(request.getEmailAddress());
        }

        // Check if name is being updated and if it already exists
        if (request.getName() != null && !request.getName().equals(company.getName())) {
            Optional<Company> existingCompany = companyRepository.findByName(request.getName());
            if (existingCompany.isPresent() && !existingCompany.get().getId().equals(id)) {
                log.warn("Name {} already exists for another company", request.getName());
                return ApiResponse.error("A company with this name already exists");
            }
            company.setName(request.getName());
        }

        if (request.getPhoneNumber() != null) {
            company.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getLogoUrl() != null) {
            company.setLogoUrl(request.getLogoUrl());
        }

        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }

        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }

        if (request.getAddress() != null) {
            company.setAddress(request.getAddress());
        }

        if (request.getCity() != null) {
            company.setCity(request.getCity());
        }

        if (request.getCountry() != null) {
            company.setCountry(request.getCountry());
        }

        if (request.getIndustry() != null) {
            company.setIndustry(request.getIndustry());
        }

        if (request.getCompanySizeBand() != null) {
            company.setCompanySizeBand(request.getCompanySizeBand());
        }

        if (request.getTimezone() != null) {
            company.setTimezone(request.getTimezone());
        }

        if (request.getMentorshipObjective() != null) {
            company.setMentorshipObjective(request.getMentorshipObjective());
        }

        if (request.getTargetAudienceDescription() != null) {
            company.setTargetAudienceDescription(request.getTargetAudienceDescription());
        }

        if (request.getProgramDesignPreference() != null) {
            company.setProgramDesignPreference(request.getProgramDesignPreference());
        }

        if (request.getPrimaryColor() != null) {
            company.setPrimaryColor(request.getPrimaryColor());
        }

        if (request.getSecondaryColor() != null) {
            company.setSecondaryColor(request.getSecondaryColor());
        }

        if (request.getIsActive() != null) {
            company.setIsActive(request.getIsActive());
        }

        Company updatedCompany = companyRepository.save(company);
        log.info("Company updated successfully: {}", id);

        return ApiResponse.success("Company updated successfully", updatedCompany);
    }

    /**
     * Delete a company (soft delete by setting isActive to false)
     */
    public ApiResponse<Void> deleteCompany(UUID id) {
        log.info("Deleting company: {}", id);

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", id);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        company.setIsActive(false);
        companyRepository.save(company);

        log.info("Company soft deleted successfully: {}", id);
        return ApiResponse.success("Company deleted successfully", null);
    }

    /**
     * Permanently delete a company
     */
    public ApiResponse<Void> permanentlyDeleteCompany(UUID id) {
        log.info("Permanently deleting company: {}", id);

        if (!companyRepository.existsById(id)) {
            log.warn("Company not found: {}", id);
            return ApiResponse.error("Company not found");
        }

        companyRepository.deleteById(id);
        log.info("Company permanently deleted: {}", id);

        return ApiResponse.success("Company permanently deleted successfully", null);
    }

    /**
     * Toggle company active status
     */
    public ApiResponse<Company> toggleCompanyActiveStatus(UUID id) {
        log.info("Toggling active status for company: {}", id);

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", id);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        company.setIsActive(!company.getIsActive());
        Company updatedCompany = companyRepository.save(company);

        log.info("Company active status toggled to {}: {}", updatedCompany.getIsActive(), id);
        return ApiResponse.success(
            String.format("Company %s successfully", updatedCompany.getIsActive() ? "activated" : "deactivated"),
            updatedCompany
        );
    }

    /**
     * Link a profile to a company
     */
    public ApiResponse<Profile> linkProfileToCompany(UUID companyId, UUID profileId) {
        log.info("Linking profile {} to company {}", profileId, companyId);

        // Verify company exists
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        // Verify profile exists
        Optional<Profile> profileOpt = profileRepository.findById(profileId);
        if (profileOpt.isEmpty()) {
            log.warn("Profile not found: {}", profileId);
            return ApiResponse.error("Profile not found");
        }

        Company company = companyOpt.get();
        Profile profile = profileOpt.get();

        // Check if company is active
        if (!company.getIsActive()) {
            log.warn("Cannot link profile to inactive company: {}", companyId);
            return ApiResponse.error("Cannot link profile to an inactive company");
        }

        // Link profile to company using native update query to avoid role casting issues
        int updated = profileRepository.updateCompanyId(profileId, companyId, java.time.ZonedDateTime.now());

        if (updated == 0) {
            log.error("Failed to update profile {} with company {}", profileId, companyId);
            return ApiResponse.error("Failed to link profile to company");
        }

        // Fetch updated profile
        Profile updatedProfile = profileRepository.findById(profileId).orElse(profile);

        log.info("Profile {} successfully linked to company {}", profileId, companyId);
        return ApiResponse.success("Profile successfully linked to company", updatedProfile);
    }

    /**
     * Unlink a profile from a company
     */
    public ApiResponse<Profile> unlinkProfileFromCompany(UUID profileId) {
        log.info("Unlinking profile {} from company", profileId);

        // Verify profile exists
        Optional<Profile> profileOpt = profileRepository.findById(profileId);
        if (profileOpt.isEmpty()) {
            log.warn("Profile not found: {}", profileId);
            return ApiResponse.error("Profile not found");
        }

        Profile profile = profileOpt.get();

        if (profile.getCompany() == null) {
            log.warn("Profile {} is not linked to any company", profileId);
            return ApiResponse.error("Profile is not linked to any company");
        }

        // Unlink profile from company using native update query to avoid role casting issues
        int updated = profileRepository.removeCompanyId(profileId, java.time.ZonedDateTime.now());

        if (updated == 0) {
            log.error("Failed to remove company from profile {}", profileId);
            return ApiResponse.error("Failed to unlink profile from company");
        }

        // Fetch updated profile
        Profile updatedProfile = profileRepository.findById(profileId).orElse(profile);

        log.info("Profile {} successfully unlinked from company", profileId);
        return ApiResponse.success("Profile successfully unlinked from company", updatedProfile);
    }

    /**
     * Get all profiles linked to a company
     */
    @Transactional(readOnly = true)
    public List<Profile> getCompanyProfiles(UUID companyId) {
        log.info("Getting profiles for company: {}", companyId);

        // Verify company exists
        if (!companyRepository.existsById(companyId)) {
            log.warn("Company not found: {}", companyId);
            return List.of();
        }

        return profileRepository.findAll().stream()
                .filter(profile -> profile.getCompany() != null && profile.getCompany().getId().equals(companyId))
                .toList();
    }

    /**
     * Search company profiles by name or email with pagination
     * Uses JPA Specifications for dynamic query building
     */
    @Transactional(readOnly = true)
    public ApiResponse<Page<Profile>> searchCompanyProfiles(
            String companyId, String search, int page, int size) {
        log.info("Searching profiles for company: {} with search: {}, page: {}, size: {}",
                companyId, search, page, size);

        // Validate and convert companyId
        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid company ID format: {}", companyId);
            return ApiResponse.error("Invalid company ID format");
        }

        // Verify company exists
        if (!companyRepository.existsById(companyUuid)) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        // Build specification using the specification class
        var specification = com.prosper.prospermentor.specification.ProfileSpecification.searchCompanyProfiles(
                companyUuid, search);

        // Create pageable
        Pageable pageable = PageRequest.of(page, size);

        // Execute query with specification
        Page<Profile> resultPage = profileRepository.findAll(specification, pageable);

        log.info("Found {} profiles on page {} of {} (total: {})",
                resultPage.getNumberOfElements(),
                resultPage.getNumber() + 1,
                resultPage.getTotalPages(),
                resultPage.getTotalElements());

        return ApiResponse.success("Company profiles retrieved successfully", resultPage);
    }

    /**
     * Complete company registration using token
     */
    public ApiResponse<Company> completeRegistration(String token) {
        log.info("Completing registration with token: {}", token);

        Optional<Company> companyOpt = companyRepository.findAll().stream()
                .filter(c -> token.equals(c.getRegistrationToken()))
                .findFirst();

        if (companyOpt.isEmpty()) {
            log.warn("Invalid registration token: {}", token);
            return ApiResponse.error("Invalid registration token");
        }

        Company company = companyOpt.get();

        // Check if token has expired
        if (company.getRegistrationTokenExpiry() != null &&
            company.getRegistrationTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Registration token expired for company: {}", company.getId());
            return ApiResponse.error("Registration token has expired. Please contact support for a new link.");
        }

        // Check if already registered
        if (Boolean.TRUE.equals(company.getRegistrationCompleted())) {
            log.info("Company already registered: {}", company.getId());
            return ApiResponse.success("Registration already completed", company);
        }

        // Complete registration
        company.setRegistrationCompleted(true);
        company.setRegistrationToken(null); // Clear token after use
        company.setRegistrationTokenExpiry(null);

        Company updatedCompany = companyRepository.save(company);
        log.info("Registration completed for company: {}", updatedCompany.getId());

        // Send confirmation email
        try {
            companyNotificationService.sendRegistrationCompletedEmail(updatedCompany);
        } catch (Exception e) {
            log.error("Failed to send registration confirmation email: {}", e.getMessage());
        }

        return ApiResponse.success("Registration completed successfully", updatedCompany);
    }

    /**
     * Resend welcome email with new registration token
     */
    public ApiResponse<Void> resendWelcomeEmail(UUID companyId) {
        log.info("Resending welcome email for company: {}", companyId);

        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();

        // Check if already registered
        if (Boolean.TRUE.equals(company.getRegistrationCompleted())) {
            log.warn("Cannot resend welcome email - company already registered: {}", companyId);
            return ApiResponse.error("Company registration is already completed");
        }

        // Generate new registration token
        String registrationToken = UUID.randomUUID().toString();
        company.setRegistrationToken(registrationToken);
        company.setRegistrationTokenExpiry(LocalDateTime.now().plusDays(7));

        companyRepository.save(company);

        // Send welcome email
        try {
            companyNotificationService.sendCompanyWelcomeEmail(company, registrationToken);
            log.info("Welcome email resent to company: {}", company.getEmailAddress());
            return ApiResponse.success("Welcome email has been resent to " + company.getEmailAddress(), null);
        } catch (Exception e) {
            log.error("Failed to resend welcome email to company {}: {}", company.getEmailAddress(), e.getMessage());
            return ApiResponse.error("Failed to send welcome email. Please try again later.");
        }
    }

    /**
     * Get company by registration token
     */
    @Transactional(readOnly = true)
    public ApiResponse<Company> getCompanyByToken(String token) {
        log.info("Getting company by registration token");

        Optional<Company> companyOpt = companyRepository.findAll().stream()
                .filter(c -> token.equals(c.getRegistrationToken()))
                .findFirst();

        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Invalid registration token");
        }

        Company company = companyOpt.get();

        // Check if token has expired
        if (company.getRegistrationTokenExpiry() != null &&
            company.getRegistrationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Registration token has expired");
        }

        return ApiResponse.success("Company found", company);
    }

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> verifyCompanyRegistrationToken(String token) {
        log.info("Verifying company registration token");

        var companyResponse = getCompanyByToken(token);
        if (!companyResponse.isSuccess() || companyResponse.getData() == null) {
            return ApiResponse.error(companyResponse.getMessage());
        }

        Company company = companyResponse.getData();
        if (Boolean.TRUE.equals(company.getRegistrationCompleted())) {
            return ApiResponse.error("Company registration is already completed");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", company.getId());
        data.put("companyName", company.getName());
        data.put("email", company.getEmailAddress());
        data.put("phoneNumber", company.getPhoneNumber());
        data.put("registrationCompleted", company.getRegistrationCompleted());

        return ApiResponse.success("Company registration token verified successfully", data);
    }

    public ApiResponse<Map<String, Object>> completeCompanyRegistrationWithProfile(
            String token,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String dateOfBirth) {
        return completeCompanyRegistrationWithProfile(
                token,
                userId,
                email,
                firstName,
                lastName,
                phoneNumber,
                dateOfBirth,
                true
        );
    }

    public ApiResponse<Map<String, Object>> completeCompanyRegistrationWithProfile(
            String token,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String dateOfBirth,
            boolean sendRegistrationCompletedEmail) {

        log.info("Starting company registration completion for user: {}", userId);

        var verificationResponse = verifyCompanyRegistrationToken(token);
        if (!verificationResponse.isSuccess() || verificationResponse.getData() == null) {
            return ApiResponse.error(verificationResponse.getMessage());
        }

        UUID companyId = (UUID) verificationResponse.getData().get("companyId");
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();
        Profile profile = profileRepository.findById(userId).orElseGet(Profile::new);
        String resolvedUsername = profile.getUsername();

        if (resolvedUsername == null || resolvedUsername.isBlank()) {
            resolvedUsername = profileService.generateUniqueUsername(email, firstName, lastName);
        }

        if (profile.getId() == null) {
            profile.setId(userId);
            profile.setIsVerified(false);
            profile.setUsername(resolvedUsername);
        }

        if (profile.getCompany() != null && profile.getCompany().getId() != null
                && !company.getId().equals(profile.getCompany().getId())) {
            return ApiResponse.error("Profile is already linked to another company");
        }

        profile.setEmail(email);
        if (profile.getUsername() == null || profile.getUsername().isBlank()) {
            profile.setUsername(resolvedUsername);
        }
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setPhone(phoneNumber);
        profile.setRole("company");
        profile.setCompany(company);

        if (dateOfBirth != null && !dateOfBirth.trim().isEmpty()) {
            try {
                profile.setDob(java.time.LocalDate.parse(dateOfBirth));
            } catch (Exception e) {
                log.warn("Failed to parse company admin date of birth: {}", dateOfBirth);
            }
        }

        Profile savedProfile = profileRepository.save(profile);

        company.setRegistrationCompleted(true);
        company.setRegistrationToken(null);
        company.setRegistrationTokenExpiry(null);
        Company updatedCompany = companyRepository.save(company);

        if (sendRegistrationCompletedEmail) {
            try {
                companyNotificationService.sendRegistrationCompletedEmail(updatedCompany);
            } catch (Exception e) {
                log.error("Failed to send registration confirmation email to company {}: {}", updatedCompany.getId(), e.getMessage());
            }
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("profile", profileService.getCompleteProfile(savedProfile.getId()).orElseGet(() -> Map.of(
                "id", savedProfile.getId(),
                "email", savedProfile.getEmail(),
                "role", savedProfile.getRole()
        )));
        responseData.put("company", Map.of(
                "linked", true,
                "companyId", updatedCompany.getId(),
                "companyName", updatedCompany.getName()
        ));

        return ApiResponse.success("Company registration completed successfully", responseData);
    }

    // ==================== Employee Whitelist Methods ====================

    /**
     * Add a single employee to whitelist
     */
    public ApiResponse<CompanyEmployeeWhitelistDto> addEmployeeToWhitelist(String companyId, AddEmployeeToWhitelistRequest request, String addedBy) {
        log.info("Adding employee {} to whitelist for company {}", request.getEmailAddress(), companyId);

        // Validate and convert companyId
        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid company ID format: {}", companyId);
            return ApiResponse.error("Invalid company ID format");
        }

        // Verify company exists
        Optional<Company> companyOpt = companyRepository.findById(companyUuid);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();

        // Check if email already exists in whitelist
        if (whitelistRepository.existsByCompanyIdAndEmail(companyUuid, request.getEmailAddress().toLowerCase().trim())) {
            log.warn("Email {} already exists in whitelist for company {}", request.getEmailAddress(), companyId);
            return ApiResponse.error("Email already exists in whitelist");
        }

        // Create whitelist entry
        CompanyEmployeeWhitelist whitelist = new CompanyEmployeeWhitelist();
        whitelist.setCompany(company);
        whitelist.setEmail(request.getEmailAddress().toLowerCase().trim());
        whitelist.setAddedBy(addedBy);

        // Generate invitation token
        String invitationToken = UUID.randomUUID().toString();
        whitelist.setInvitationToken(invitationToken);
        whitelist.setInvitationTokenExpiry(LocalDateTime.now().plusDays(7));
        whitelist.setInvitationSent(false);
        whitelist.setInvitationAccepted(false);

        CompanyEmployeeWhitelist saved = whitelistRepository.save(whitelist);

        // Send invitation email
        try {
            companyNotificationService.sendEmployeeInvitationEmail(company, saved.getEmail(), invitationToken);
            saved.setInvitationSent(true);
            saved = whitelistRepository.save(saved);
            log.info("Invitation email sent to: {}", saved.getEmail());
        } catch (Exception emailError) {
            log.error("Failed to send invitation email to {}: {}", saved.getEmail(), emailError.getMessage());
            // Continue even if email fails - whitelist entry is still created
        }

        log.info("Employee {} added to whitelist successfully", request.getEmailAddress());

        return ApiResponse.success("Employee added to whitelist and invitation sent", convertToDto(saved));
    }

    /**
     * Bulk upload employees to whitelist from Excel file
     */
    public ApiResponse<BulkWhitelistUploadResponse> bulkUploadWhitelist(UUID companyId, MultipartFile file, String addedBy) {
        log.info("Processing bulk whitelist upload for company {}", companyId);

        // Verify company exists
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        Company company = companyOpt.get();

        BulkWhitelistUploadResponse response = new BulkWhitelistUploadResponse();
        response.setTotalProcessed(0);
        response.setSuccessCount(0);
        response.setFailureCount(0);
        response.setDuplicateCount(0);

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowNum = 0;

            for (Row row : sheet) {
                rowNum++;

                // Skip header row
                if (rowNum == 1) {
                    continue;
                }

                response.setTotalProcessed(response.getTotalProcessed() + 1);

                try {
                    // Read email from first column
                    String email = getCellValueAsString(row.getCell(0));

                    // Validate email
                    if (email == null || email.trim().isEmpty()) {
                        response.setFailureCount(response.getFailureCount() + 1);
                        response.getErrors().add(new BulkWhitelistUploadResponse.WhitelistError(
                                rowNum, "", "Email is required"));
                        continue;
                    }

                    email = email.toLowerCase().trim();

                    // Check if email is valid
                    if (!isValidEmail(email)) {
                        response.setFailureCount(response.getFailureCount() + 1);
                        response.getErrors().add(new BulkWhitelistUploadResponse.WhitelistError(
                                rowNum, email, "Invalid email format"));
                        continue;
                    }

                    // Check if email already exists
                    if (whitelistRepository.existsByCompanyIdAndEmail(companyId, email)) {
                        response.setDuplicateCount(response.getDuplicateCount() + 1);
                        response.getErrors().add(new BulkWhitelistUploadResponse.WhitelistError(
                                rowNum, email, "Email already exists in whitelist"));
                        continue;
                    }

                    // Create whitelist entry
                    CompanyEmployeeWhitelist whitelist = new CompanyEmployeeWhitelist();
                    whitelist.setCompany(company);
                    whitelist.setEmail(email);
                    whitelist.setAddedBy(addedBy);

                    // Generate invitation token
                    String invitationToken = UUID.randomUUID().toString();
                    whitelist.setInvitationToken(invitationToken);
                    whitelist.setInvitationTokenExpiry(LocalDateTime.now().plusDays(7));
                    whitelist.setInvitationSent(false);
                    whitelist.setInvitationAccepted(false);

                    whitelistRepository.save(whitelist);

                    // Send invitation email
                    try {
                        companyNotificationService.sendEmployeeInvitationEmail(company, email, invitationToken);
                        whitelist.setInvitationSent(true);
                        whitelistRepository.save(whitelist);
                    } catch (Exception emailError) {
                        log.error("Failed to send invitation email to {}: {}", email, emailError.getMessage());
                        // Continue even if email fails
                    }

                    response.setSuccessCount(response.getSuccessCount() + 1);
                    response.getSuccessEmails().add(email);

                } catch (Exception e) {
                    log.error("Error processing row {}: {}", rowNum, e.getMessage());
                    response.setFailureCount(response.getFailureCount() + 1);
                    response.getErrors().add(new BulkWhitelistUploadResponse.WhitelistError(
                            rowNum, "", "Error processing row: " + e.getMessage()));
                }
            }

            log.info("Bulk upload completed: {} success, {} failures, {} duplicates",
                    response.getSuccessCount(), response.getFailureCount(), response.getDuplicateCount());

            return ApiResponse.success("Bulk upload completed", response);

        } catch (IOException e) {
            log.error("Error reading Excel file: {}", e.getMessage());
            return ApiResponse.error("Error reading Excel file: " + e.getMessage());
        }
    }

    /**
     * Get all whitelist entries for a company
     */
    @Transactional(readOnly = true)
    public List<CompanyEmployeeWhitelist> getCompanyWhitelist(UUID companyId, Boolean activeOnly, Boolean unusedOnly) {
        log.info("Getting whitelist for company {}", companyId);

        List<CompanyEmployeeWhitelist> whitelists = new ArrayList<>();

        if (activeOnly != null && activeOnly) {
            whitelists = whitelistRepository.findByCompanyId(companyId);
        } else if (unusedOnly != null && unusedOnly) {
            whitelists = whitelistRepository.findByCompanyId(companyId);
        } else {
            whitelists = whitelistRepository.findByCompanyId(companyId);
        }

        return whitelists;
    }

    /**
     * Get a specific whitelist entry by ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<CompanyEmployeeWhitelistDto> getWhitelistEntryById(UUID whitelistId) {
        log.info("Getting whitelist entry by ID: {}", whitelistId);

        Optional<CompanyEmployeeWhitelist> whitelistOpt = whitelistRepository.findById(whitelistId);

        if (whitelistOpt.isEmpty()) {
            log.warn("Whitelist entry not found: {}", whitelistId);
            return ApiResponse.error("Whitelist entry not found");
        }

        CompanyEmployeeWhitelistDto dto = convertToDto(whitelistOpt.get());
        return ApiResponse.success("Whitelist entry retrieved successfully", dto);
    }

    /**
     * Check if email is whitelisted for a company
     */
    @Transactional(readOnly = true)
    public ApiResponse<Boolean> checkEmailWhitelisted(UUID companyId, String email) {
        log.info("Checking if email {} is whitelisted for company {}", email, companyId);

        boolean isWhitelisted = whitelistRepository.existsByCompanyIdAndEmail(
                companyId, email.toLowerCase().trim());

        return ApiResponse.success("Whitelist check completed", isWhitelisted);
    }

    /**
     * Mark whitelist entry as used
     */
    public ApiResponse<CompanyEmployeeWhitelistDto> markWhitelistAsUsed(UUID whitelistId, UUID profileId) {
        log.info("Marking whitelist entry {} as used", whitelistId);

        Optional<CompanyEmployeeWhitelist> whitelistOpt = whitelistRepository.findById(whitelistId);
        if (whitelistOpt.isEmpty()) {
            log.warn("Whitelist entry not found: {}", whitelistId);
            return ApiResponse.error("Whitelist entry not found");
        }

        CompanyEmployeeWhitelist whitelist = whitelistOpt.get();

        CompanyEmployeeWhitelist updated = whitelistRepository.save(whitelist);
        log.info("Whitelist entry marked as used");

        return ApiResponse.success("Whitelist entry marked as used", convertToDto(updated));
    }

    /**
     * Remove employee from whitelist (delete)
     */
    public ApiResponse<Void> removeFromWhitelist(String whitelistId) {
        log.info("Removing employee from whitelist: {}", whitelistId);

        // Validate and convert whitelistId
        UUID whitelistUuid;
        try {
            whitelistUuid = UUID.fromString(whitelistId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid whitelist ID format: {}", whitelistId);
            return ApiResponse.error("Invalid whitelist ID format");
        }

        if (!whitelistRepository.existsById(whitelistUuid)) {
            log.warn("Whitelist entry not found: {}", whitelistId);
            return ApiResponse.error("Whitelist entry not found");
        }

        whitelistRepository.deleteById(whitelistUuid);

        log.info("Employee removed from whitelist successfully");
        return ApiResponse.success("Employee removed from whitelist", null);
    }

    /**
     * Permanently delete whitelist entry
     */
    public ApiResponse<Void> deleteWhitelistEntry(UUID whitelistId) {
        log.info("Permanently deleting whitelist entry: {}", whitelistId);

        if (!whitelistRepository.existsById(whitelistId)) {
            log.warn("Whitelist entry not found: {}", whitelistId);
            return ApiResponse.error("Whitelist entry not found");
        }

        whitelistRepository.deleteById(whitelistId);
        log.info("Whitelist entry permanently deleted");

        return ApiResponse.success("Whitelist entry permanently deleted", null);
    }

    /**
     * Get whitelist statistics for a company
     */
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getWhitelistStats(UUID companyId) {
        log.info("Getting whitelist statistics for company {}", companyId);

        if (!companyRepository.existsById(companyId)) {
            return ApiResponse.error("Company not found");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", whitelistRepository.countByCompanyId(companyId));
        stats.put("activeEntries", whitelistRepository.countByCompanyId(companyId));
        stats.put("unusedEntries", whitelistRepository.countByCompanyId(companyId));

        return ApiResponse.success("Statistics retrieved successfully", stats);
    }

    /**
     * Search whitelisted employees by name or email with pagination
     * Uses JPA Specifications for dynamic query building
     */
    @Transactional(readOnly = true)
    public ApiResponse<Page<CompanyEmployeeWhitelist>> searchWhitelistedEmployees(
            String companyId, String search, int page, int size) {
        log.info("Searching whitelisted employees for company: {} with search: {}, page: {}, size: {}",
                companyId, search, page, size);

        // Validate and convert companyId
        UUID companyUuid;
        try {
            companyUuid = UUID.fromString(companyId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid company ID format: {}", companyId);
            return ApiResponse.error("Invalid company ID format");
        }

        // Verify company exists
        if (!companyRepository.existsById(companyUuid)) {
            log.warn("Company not found: {}", companyId);
            return ApiResponse.error("Company not found");
        }

        // Build specification using the specification class
        var specification = CompanyEmployeeWhitelistSpecification.searchWhitelistedEmployees(
                companyUuid, search);

        // Create pageable
        Pageable pageable = PageRequest.of(page, size);

        // Execute query with specification
        Page<CompanyEmployeeWhitelist> resultPage = whitelistRepository.findAll(specification, pageable);

        log.info("Found {} whitelisted employees on page {} of {} (total: {})",
                resultPage.getNumberOfElements(),
                resultPage.getNumber() + 1,
                resultPage.getTotalPages(),
                resultPage.getTotalElements());

        return ApiResponse.success("Whitelist retrieved successfully", resultPage);
    }

    // ==================== Helper Methods ====================

    /**
     * Convert entity to DTO
     */
    private CompanyEmployeeWhitelistDto convertToDto(CompanyEmployeeWhitelist whitelist) {
        CompanyEmployeeWhitelistDto dto = new CompanyEmployeeWhitelistDto();
        dto.setId(whitelist.getId());
        dto.setCompanyId(whitelist.getCompany().getId());
        dto.setCompanyName(whitelist.getCompany().getName());
        dto.setEmail(whitelist.getEmail());
        dto.setCreatedAt(whitelist.getCreatedAt());
        dto.setUpdatedAt(whitelist.getUpdatedAt());
        return dto;
    }

    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    /**
     * Verify invitation token
     */
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> verifyInvitationToken(String token) {
        log.info("Verifying invitation token");

        Optional<CompanyEmployeeWhitelist> whitelistOpt = whitelistRepository.findAll().stream()
                .filter(w -> token.equals(w.getInvitationToken()))
                .findFirst();

        if (whitelistOpt.isEmpty()) {
            return ApiResponse.error("Invalid invitation token");
        }

        CompanyEmployeeWhitelist whitelist = whitelistOpt.get();

        // Check if token has expired
        if (whitelist.getInvitationTokenExpiry() != null &&
                whitelist.getInvitationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Invitation has expired");
        }

        // Check if already accepted
        if (Boolean.TRUE.equals(whitelist.getInvitationAccepted())) {
            return ApiResponse.error("Invitation has already been accepted");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("email", whitelist.getEmail());
        data.put("companyName", whitelist.getCompany().getName());
        data.put("companyId", whitelist.getCompany().getId());

        return ApiResponse.success("Invitation verified successfully", data);
    }

    /**
     * Complete invitation signup with profile creation, company linking, and subscription
     * This orchestrates the entire signup flow after user creation in Supabase
     */
    public ApiResponse<Map<String, Object>> completeInvitationSignupWithProfile(
            String token,
            UUID userId,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String dateOfBirth) {

        log.info("Starting complete invitation signup for user: {}", userId);

        // Verify invitation token
        var verificationResponse = verifyInvitationToken(token);
        if (!verificationResponse.isSuccess()) {
            return ApiResponse.error(verificationResponse.getMessage());
        }

        // Create profile with details
        Optional<Map<String, Object>> profileOpt = profileService.createProfileWithDetails(
                userId,
                email,
                "mentee",
                firstName,
                lastName,
                phoneNumber,
                dateOfBirth
        );

        if (profileOpt.isEmpty()) {
            log.error("Failed to create profile for user: {}", email);
            return ApiResponse.error("Failed to create profile");
        }

        log.info("Profile created successfully for user: {}", userId);

        // Link profile to company
        var linkResponse = completeInvitationSignup(token, userId);
        if (!linkResponse.isSuccess()) {
            log.error("Failed to link profile to company: {}", linkResponse.getMessage());
            return ApiResponse.error(linkResponse.getMessage());
        }

        log.info("Profile linked to company successfully for user: {}", userId);

        // Prepare response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("profile", profileOpt.get());
        responseData.put("company", Map.of(
                "linked", true,
                "companyId", verificationResponse.getData().get("companyId")
        ));

        log.info("Complete invitation signup finished successfully for user: {}", userId);
        return ApiResponse.success("Signup completed successfully", responseData);
    }

    /**
     * Complete signup process after invitation acceptance.
     * Links profile to company only. Corporate seat assignment happens separately.
     */
    public ApiResponse<Profile> completeInvitationSignup(String token, UUID profileId) {
        log.info("Completing invitation signup for profile: {}", profileId);

        // Find whitelist entry by token
        Optional<CompanyEmployeeWhitelist> whitelistOpt = whitelistRepository.findAll().stream()
                .filter(w -> token.equals(w.getInvitationToken()))
                .findFirst();

        if (whitelistOpt.isEmpty()) {
            return ApiResponse.error("Invalid invitation token");
        }

        CompanyEmployeeWhitelist whitelist = whitelistOpt.get();

        // Check if token has expired
        if (whitelist.getInvitationTokenExpiry() != null &&
                whitelist.getInvitationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Invitation has expired");
        }

        // Get profile
        Optional<Profile> profileOpt = profileRepository.findById(profileId);
        if (profileOpt.isEmpty()) {
            return ApiResponse.error("Profile not found");
        }

        Profile profile = profileOpt.get();

        // Link profile to company
        int updated = profileRepository.updateCompanyId(profileId, whitelist.getCompany().getId(), java.time.ZonedDateTime.now());

        if (updated == 0) {
            log.error("Failed to update profile {} with company {}", profileId, whitelist.getCompany().getId());
            return ApiResponse.error("Failed to link profile to company");
        }

        // Mark whitelist entry as used
        whitelist.setInvitationAccepted(true);
        whitelist.setIsUsed(true);
        whitelist.setProfile(profile);
        whitelist.setInvitationToken(null); // Clear token after use
        whitelist.setInvitationTokenExpiry(null);
        whitelistRepository.save(whitelist);

        // Fetch updated profile
        Profile updatedProfile = profileRepository.findById(profileId).orElse(profile);

        log.info("Invitation signup completed successfully for profile: {}", profileId);
        return ApiResponse.success("Signup completed successfully", updatedProfile);
    }

    /**
     * Resend invitation email to whitelisted employee
     */
    public ApiResponse<CompanyEmployeeWhitelistDto> resendInvitationEmail(String whitelistId) {
        log.info("Resending invitation email for whitelist entry: {}", whitelistId);

        // Validate and convert whitelistId
        UUID whitelistUuid;
        try {
            whitelistUuid = UUID.fromString(whitelistId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid whitelist ID format: {}", whitelistId);
            return ApiResponse.error("Invalid whitelist ID format");
        }

        // Find whitelist entry
        Optional<CompanyEmployeeWhitelist> whitelistOpt = whitelistRepository.findById(whitelistUuid);
        if (whitelistOpt.isEmpty()) {
            log.warn("Whitelist entry not found: {}", whitelistId);
            return ApiResponse.error("Whitelist entry not found");
        }

        CompanyEmployeeWhitelist whitelist = whitelistOpt.get();

        // Check if already accepted
        if (Boolean.TRUE.equals(whitelist.getInvitationAccepted())) {
            log.warn("Cannot resend invitation - already accepted for: {}", whitelist.getEmail());
            return ApiResponse.error("Invitation has already been accepted");
        }

        // Check if already used
        if (Boolean.TRUE.equals(whitelist.getIsUsed())) {
            log.warn("Cannot resend invitation - already used for: {}", whitelist.getEmail());
            return ApiResponse.error("Invitation has already been used");
        }

        // Generate new invitation token
        String newInvitationToken = UUID.randomUUID().toString();
        whitelist.setInvitationToken(newInvitationToken);
        whitelist.setInvitationTokenExpiry(LocalDateTime.now().plusDays(7));
        whitelist.setInvitationSent(false);

        whitelistRepository.save(whitelist);

        // Send invitation email
        try {
            companyNotificationService.sendEmployeeInvitationEmail(
                    whitelist.getCompany(),
                    whitelist.getEmail(),
                    newInvitationToken
            );
            whitelist.setInvitationSent(true);
            whitelist = whitelistRepository.save(whitelist);
            log.info("Invitation email resent successfully to: {}", whitelist.getEmail());
        } catch (Exception emailError) {
            log.error("Failed to resend invitation email to {}: {}", whitelist.getEmail(), emailError.getMessage());
            return ApiResponse.error("Failed to send invitation email");
        }

        return ApiResponse.success("Invitation email resent successfully", convertToDto(whitelist));
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * Generate Excel template for bulk whitelist upload
     */
    public Workbook generateWhitelistTemplate() {
        log.info("Generating whitelist upload template");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Employee Whitelist");

        // Create header row with styling
        Row headerRow = sheet.createRow(0);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // Create header cell - only Email column
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Email");
        headerCell.setCellStyle(headerStyle);
        sheet.setColumnWidth(0, 8000); // Set column width for email

        // Add sample data rows
        Row sampleRow1 = sheet.createRow(1);
        sampleRow1.createCell(0).setCellValue("employee1@example.com");

        Row sampleRow2 = sheet.createRow(2);
        sampleRow2.createCell(0).setCellValue("employee2@example.com");

        Row sampleRow3 = sheet.createRow(3);
        sampleRow3.createCell(0).setCellValue("employee3@example.com");

        // Add instructions in a separate row
        Row instructionRow = sheet.createRow(5);
        Cell instructionCell = instructionRow.createCell(0);
        instructionCell.setCellValue("Instructions: Enter employee email addresses (one per row). Remove the sample emails before uploading.");

        CellStyle instructionStyle = workbook.createCellStyle();
        Font instructionFont = workbook.createFont();
        instructionFont.setItalic(true);
        instructionFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        instructionStyle.setFont(instructionFont);
        instructionCell.setCellStyle(instructionStyle);

        log.info("Whitelist template generated successfully");
        return workbook;
    }
}
