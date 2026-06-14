package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyReportDtos;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMatchWorkspace;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.CompanyProgramMatchWorkspaceRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.ParticipantPulseRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyReportService {

    private static final LocalDate DEFAULT_START_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDate DEFAULT_END_DATE = LocalDate.of(9999, 12, 30);

    private final CompanyProgramRepository companyProgramRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramMentorAssignmentRepository mentorAssignmentRepository;
    private final CompanyProgramMatchWorkspaceRepository matchWorkspaceRepository;
    private final SessionRepository sessionRepository;
    private final ParticipantPulseRepository participantPulseRepository;
    private final ReviewAlertRepository reviewAlertRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final ReviewRequestRepository reviewRequestRepository;
    private final PaymentRepository paymentRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.ProgramReportRowDto> getProgramReport(
            UUID companyId,
            int page,
            int size,
            String search,
            CompanyProgram.CompanyProgramStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);

        Page<CompanyProgram> programs = companyProgramRepository.findByCompanyIdWithFilters(
                companyId,
                status,
                normalizeSearch(search),
                startAt(startDate),
                endExclusive(endDate),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return CompanyReportDtos.ReportListDto.<CompanyReportDtos.ProgramReportRowDto>builder()
                .rows(programs.getContent().stream().map(this::toProgramRow).toList())
                .count(programs.getNumberOfElements())
                .currentPage(programs.getNumber())
                .pageSize(programs.getSize())
                .totalPages(programs.getTotalPages())
                .totalItems(programs.getTotalElements())
                .hasNext(programs.hasNext())
                .hasPrevious(programs.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.ParticipantReportRowDto> getParticipantReport(
            UUID companyId,
            int page,
            int size,
            String search,
            CompanyProgramParticipant.ParticipantStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        ReportContext context = loadReportContext(companyId);
        String normalizedSearch = normalizeSearch(search);

        List<CompanyReportDtos.ParticipantReportRowDto> rows = context.participants().stream()
                .filter(participant -> status == null || participant.getStatus() == status)
                .filter(participant -> isWithin(participantDate(participant), startAt(startDate), endExclusive(endDate)))
                .map(participant -> toParticipantRow(
                        participant,
                        context.assignmentsByParticipantId().get(participant.getId()),
                        context.workspacesByParticipantId().get(participant.getId())
                ))
                .filter(row -> matchesSearch(normalizedSearch,
                        row.getCompanyProgramName(),
                        row.getProfileName(),
                        row.getProfileEmail(),
                        row.getProfileRole(),
                        row.getDepartment(),
                        row.getMentorName(),
                        row.getMatchStatus()))
                .toList();

        return paginate(rows, page, size);
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.MentorMatchReportRowDto> getMatchReport(
            UUID companyId,
            int page,
            int size,
            String search,
            CompanyProgramMatchWorkspace.MatchStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        ReportContext context = loadReportContext(companyId);
        String normalizedSearch = normalizeSearch(search);

        List<CompanyReportDtos.MentorMatchReportRowDto> rows = context.participants().stream()
                .filter(participant -> isWithin(participantDate(participant), startAt(startDate), endExclusive(endDate)))
                .map(participant -> toMatchRow(
                        participant,
                        context.assignmentsByParticipantId().get(participant.getId()),
                        context.workspacesByParticipantId().get(participant.getId())
                ))
                .filter(row -> status == null || Objects.equals(row.getMatchStatus(), status.name()))
                .filter(row -> matchesSearch(normalizedSearch,
                        row.getCompanyProgramName(),
                        row.getParticipantName(),
                        row.getParticipantEmail(),
                        row.getParticipantStatus(),
                        row.getMatchingMode(),
                        row.getMatchStatus(),
                        row.getMentorName(),
                        row.getMentorEmail()))
                .toList();

        return paginate(rows, page, size);
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.SessionReportRowDto> getSessionReport(
            UUID companyId,
            int page,
            int size,
            String search,
            Session.SessionStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_Company_Id(companyId);
        List<UUID> participantIds = participants.stream().map(CompanyProgramParticipant::getId).toList();

        if (participantIds.isEmpty()) {
            return paginate(List.of(), page, size);
        }

        List<Session> sessions = sessionRepository.findByCompanyProgramParticipantIdInOrderByScheduledStartDesc(participantIds);
        Map<UUID, CompanyProgramParticipant> participantsById = participants.stream()
                .collect(Collectors.toMap(CompanyProgramParticipant::getId, Function.identity(), (first, second) -> first));
        Map<UUID, Profile> mentorsById = loadProfilesById(sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, String> reviewStatusBySessionId = loadReviewStatuses(sessions);
        String normalizedSearch = normalizeSearch(search);
        ZonedDateTime start = startAt(startDate).atZone(ZoneId.systemDefault());
        ZonedDateTime end = endExclusive(endDate).atZone(ZoneId.systemDefault());

        List<CompanyReportDtos.SessionReportRowDto> rows = sessions.stream()
                .filter(session -> status == null || session.getStatus() == status)
                .filter(session -> session.getScheduledStart() == null
                        || (!session.getScheduledStart().isBefore(start) && session.getScheduledStart().isBefore(end)))
                .map(session -> toSessionRow(
                        session,
                        participantsById.get(session.getCompanyProgramParticipantId()),
                        mentorsById.get(session.getMentorId()),
                        reviewStatusBySessionId.getOrDefault(session.getId(), "NO_PENDING_FEEDBACK")
                ))
                .filter(row -> matchesSearch(normalizedSearch,
                        row.getEmployeeName(),
                        row.getEmployeeEmail(),
                        row.getDepartment(),
                        row.getMentorName(),
                        row.getTitle(),
                        row.getStatus(),
                        row.getPlatformDisplayName(),
                        row.getReviewStatus()))
                .toList();

        return paginate(rows, page, size);
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.PulseCoverageReportRowDto> getPulseCoverageReport(
            UUID companyId,
            int page,
            int size,
            String search,
            ParticipantPulse.PulseStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        String normalizedSearch = normalizeSearch(search);
        List<ParticipantPulse> pulses = participantPulseRepository.findByCompanyIdWithinCreatedAt(
                companyId,
                startAt(startDate),
                endExclusive(endDate)
        );

        Map<UUID, List<ParticipantPulse>> pulsesByProgram = pulses.stream()
                .filter(pulse -> status == null || pulse.getStatus() == status)
                .filter(pulse -> pulse.getParticipant() != null
                        && pulse.getParticipant().getCompanyProgram() != null
                        && pulse.getParticipant().getCompanyProgram().getId() != null)
                .collect(Collectors.groupingBy(
                        pulse -> pulse.getParticipant().getCompanyProgram().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CompanyReportDtos.PulseCoverageReportRowDto> rows = pulsesByProgram.values().stream()
                .map(this::toPulseCoverageRow)
                .filter(row -> matchesSearch(normalizedSearch, row.getCompanyProgramName()))
                .sorted(Comparator.comparing(
                        CompanyReportDtos.PulseCoverageReportRowDto::getCompanyProgramName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ))
                .toList();

        return paginate(rows, page, size);
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.RiskSignalReportRowDto> getRiskSignalsReport(
            UUID companyId,
            int page,
            int size,
            String search,
            ReviewAlert.ReviewAlertStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        String normalizedSearch = normalizeSearch(search);

        List<CompanyReportDtos.RiskSignalReportRowDto> rows = reviewAlertRepository
                .findCompanyAlertsForSummary(companyId, null, startAt(startDate), endExclusive(endDate))
                .stream()
                .filter(alert -> status == null || alert.getStatus() == status)
                .map(this::toRiskSignalRow)
                .filter(row -> matchesSearch(normalizedSearch,
                        row.getAlertType(),
                        row.getSeverity(),
                        row.getStatus(),
                        row.getCompanyProgramName(),
                        row.getParticipantName(),
                        row.getParticipantEmail(),
                        row.getMentorName(),
                        row.getQuestionCode(),
                        row.getDetails()))
                .toList();

        return paginate(rows, page, size);
    }

    @Transactional(readOnly = true)
    public CompanyReportDtos.ReportListDto<CompanyReportDtos.BillingTransactionReportRowDto> getBillingTransactionsReport(
            UUID companyId,
            int page,
            int size,
            String search,
            Payment.PaymentStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateReportRequest(page, size, startDate, endDate);
        String normalizedSearch = normalizeSearch(search);
        LocalDateTime start = startAt(startDate);
        LocalDateTime end = endExclusive(endDate);

        List<CompanyReportDtos.BillingTransactionReportRowDto> rows = paymentRepository
                .findAll(
                        PaymentSpecification.filter(null, status, null, null, companyId, null, null, null, search),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .filter(payment -> isWithin(payment.getCreatedAt(), start, end))
                .map(this::toBillingTransactionRow)
                .filter(row -> matchesSearch(normalizedSearch,
                        row.getPaymentType(),
                        row.getPaymentMethod(),
                        row.getStatus(),
                        row.getCurrency(),
                        row.getMpesaReceiptNumber(),
                        row.getGatewayReference()))
                .toList();

        return paginate(rows, page, size);
    }

    private ReportContext loadReportContext(UUID companyId) {
        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_Company_Id(companyId);
        List<UUID> participantIds = participants.stream().map(CompanyProgramParticipant::getId).toList();

        Map<UUID, CompanyProgramMentorAssignment> assignmentsByParticipantId = participantIds.isEmpty()
                ? Map.of()
                : mentorAssignmentRepository.findByParticipant_IdInAndJourneyInstanceStepIsNull(participantIds).stream()
                .filter(assignment -> assignment.getParticipant() != null && assignment.getParticipant().getId() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getParticipant().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        Map<UUID, CompanyProgramMatchWorkspace> workspacesByParticipantId = participantIds.isEmpty()
                ? Map.of()
                : matchWorkspaceRepository.findByParticipant_IdIn(participantIds).stream()
                .filter(workspace -> workspace.getParticipant() != null && workspace.getParticipant().getId() != null)
                .collect(Collectors.toMap(
                        workspace -> workspace.getParticipant().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        return new ReportContext(participants, assignmentsByParticipantId, workspacesByParticipantId);
    }

    private CompanyReportDtos.ProgramReportRowDto toProgramRow(CompanyProgram program) {
        return CompanyReportDtos.ProgramReportRowDto.builder()
                .id(program.getId())
                .name(program.getName())
                .status(program.getStatus())
                .matchingMode(program.getMatchingMode())
                .objective(program.getObjective())
                .targetAudienceDescription(program.getTargetAudienceDescription())
                .startsAt(program.getStartsAt())
                .endsAt(program.getEndsAt())
                .maxParticipants(program.getMaxParticipants())
                .createdAt(program.getCreatedAt())
                .build();
    }

    private CompanyReportDtos.ParticipantReportRowDto toParticipantRow(CompanyProgramParticipant participant,
                                                                       CompanyProgramMentorAssignment assignment,
                                                                       CompanyProgramMatchWorkspace workspace) {
        Profile profile = participant.getProfile();
        Profile mentor = assignment != null ? assignment.getMentor() : null;

        return CompanyReportDtos.ParticipantReportRowDto.builder()
                .id(participant.getId())
                .companyProgramId(programId(participant))
                .companyProgramName(programName(participant))
                .profileId(profile != null ? profile.getId() : null)
                .profileName(displayName(profile, "Employee"))
                .profileEmail(profile != null ? profile.getEmail() : null)
                .profileRole(profile != null ? profile.getRole() : null)
                .department(profileDepartment(profile))
                .status(participant.getStatus() != null ? participant.getStatus().name() : null)
                .mentorId(mentor != null ? mentor.getId() : null)
                .mentorName(mentor != null ? displayName(mentor, "Mentor") : null)
                .mentorEmail(mentor != null ? mentor.getEmail() : null)
                .matchStatus(resolveMatchStatus(workspace, assignment))
                .enrolledAt(participant.getEnrolledAt())
                .createdAt(participant.getCreatedAt())
                .build();
    }

    private CompanyReportDtos.MentorMatchReportRowDto toMatchRow(CompanyProgramParticipant participant,
                                                                 CompanyProgramMentorAssignment assignment,
                                                                 CompanyProgramMatchWorkspace workspace) {
        Profile profile = participant.getProfile();
        Profile mentor = assignment != null ? assignment.getMentor() : null;
        CompanyProgram program = participant.getCompanyProgram();

        return CompanyReportDtos.MentorMatchReportRowDto.builder()
                .participantId(participant.getId())
                .companyProgramId(programId(participant))
                .companyProgramName(programName(participant))
                .participantName(displayName(profile, "Employee"))
                .participantEmail(profile != null ? profile.getEmail() : null)
                .participantStatus(participant.getStatus() != null ? participant.getStatus().name() : null)
                .matchingMode(program != null && program.getMatchingMode() != null ? program.getMatchingMode().name() : null)
                .matchStatus(resolveMatchStatus(workspace, assignment))
                .mentorId(mentor != null ? mentor.getId() : null)
                .mentorName(mentor != null ? displayName(mentor, "Mentor") : null)
                .mentorEmail(mentor != null ? mentor.getEmail() : null)
                .shortlistCount(workspace != null && workspace.getOptions() != null ? workspace.getOptions().size() : null)
                .selectionDeadlineAt(workspace != null ? workspace.getSelectionDeadlineAt() : null)
                .assignedAt(assignment != null ? assignment.getAssignedAt() : null)
                .resolvedAt(workspace != null ? workspace.getResolvedAt() : null)
                .build();
    }

    private CompanyReportDtos.SessionReportRowDto toSessionRow(Session session,
                                                               CompanyProgramParticipant participant,
                                                               Profile mentor,
                                                               String reviewStatus) {
        Profile employee = participant != null ? participant.getProfile() : null;

        return CompanyReportDtos.SessionReportRowDto.builder()
                .id(session.getId())
                .employeeName(displayName(employee, "Employee"))
                .employeeEmail(employee != null ? employee.getEmail() : null)
                .department(profileDepartment(employee))
                .mentorName(displayName(mentor, "Mentor"))
                .title(session.getTitle())
                .status(session.getStatus() != null ? session.getStatus().name() : null)
                .platformDisplayName(session.getMeetingPlatform() != null ? session.getMeetingPlatform().getDisplayName() : null)
                .scheduledStart(session.getScheduledStart())
                .scheduledEnd(session.getScheduledEnd())
                .durationMin(session.getScheduledStart() != null && session.getScheduledEnd() != null ? session.getDurationMinutes() : null)
                .rating(session.getRating())
                .reviewStatus(reviewStatus)
                .build();
    }

    private CompanyReportDtos.PulseCoverageReportRowDto toPulseCoverageRow(List<ParticipantPulse> pulses) {
        ParticipantPulse firstPulse = pulses.get(0);
        CompanyProgram program = firstPulse.getParticipant().getCompanyProgram();
        int total = pulses.size();
        int completed = countPulsesByStatus(pulses, ParticipantPulse.PulseStatus.COMPLETED);

        return CompanyReportDtos.PulseCoverageReportRowDto.builder()
                .companyProgramId(program.getId())
                .companyProgramName(program.getName())
                .totalPulses(total)
                .completedPulses(completed)
                .pendingPulses(countPulsesByStatus(pulses, ParticipantPulse.PulseStatus.PENDING))
                .expiredPulses(countPulsesByStatus(pulses, ParticipantPulse.PulseStatus.EXPIRED))
                .baselinePulses(countPulsesByType(pulses, ParticipantPulse.PulseType.BASELINE))
                .programEndPulses(countPulsesByType(pulses, ParticipantPulse.PulseType.PROGRAM_END))
                .completionRate(total == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(completed * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP))
                .build();
    }

    private CompanyReportDtos.RiskSignalReportRowDto toRiskSignalRow(ReviewAlert alert) {
        Profile participantProfile = alert.getParticipant() != null ? alert.getParticipant().getProfile() : null;
        Profile mentor = alert.getMentorAssignment() != null ? alert.getMentorAssignment().getMentor() : null;

        return CompanyReportDtos.RiskSignalReportRowDto.builder()
                .id(alert.getId())
                .alertType(alert.getAlertType() != null ? alert.getAlertType().name() : null)
                .severity(alert.getSeverity() != null ? alert.getSeverity().name() : null)
                .status(alert.getStatus() != null ? alert.getStatus().name() : null)
                .companyProgramId(alert.getCompanyProgram() != null ? alert.getCompanyProgram().getId() : null)
                .companyProgramName(alert.getCompanyProgram() != null ? alert.getCompanyProgram().getName() : null)
                .participantId(alert.getParticipant() != null ? alert.getParticipant().getId() : null)
                .participantName(displayName(participantProfile, "Employee"))
                .participantEmail(participantProfile != null ? participantProfile.getEmail() : null)
                .mentorId(mentor != null ? mentor.getId() : null)
                .mentorName(mentor != null ? displayName(mentor, "Mentor") : null)
                .questionCode(alert.getQuestionCode())
                .scoreValue(alert.getScoreValue())
                .details(alert.getDetails())
                .createdAt(alert.getCreatedAt())
                .build();
    }

    private CompanyReportDtos.BillingTransactionReportRowDto toBillingTransactionRow(Payment payment) {
        return CompanyReportDtos.BillingTransactionReportRowDto.builder()
                .id(payment.getId())
                .paymentType(payment.getPaymentType() != null ? payment.getPaymentType().name() : null)
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null)
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .invoiceId(payment.getInvoiceId())
                .sessionId(payment.getSessionId())
                .mpesaReceiptNumber(payment.getMpesaReceiptNumber())
                .gatewayReference(payment.getGatewayReference())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }

    private Map<UUID, Profile> loadProfilesById(Collection<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Map.of();
        }

        return profileRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, String> loadReviewStatuses(List<Session> sessions) {
        List<UUID> sessionIds = sessions.stream()
                .map(Session::getId)
                .filter(Objects::nonNull)
                .toList();

        if (sessionIds.isEmpty()) {
            return Map.of();
        }

        List<ReviewCycle> cycles = reviewCycleRepository.findBySession_IdInAndTypeOrderByCreatedAtDesc(
                sessionIds,
                ReviewCycle.ReviewType.SESSION
        );
        List<UUID> cycleIds = cycles.stream().map(ReviewCycle::getId).filter(Objects::nonNull).toList();
        Map<UUID, List<ReviewRequest>> requestsByCycleId = cycleIds.isEmpty()
                ? Map.of()
                : reviewRequestRepository.findByReviewCycle_IdInOrderByCreatedAtAsc(cycleIds).stream()
                .filter(request -> request.getReviewCycle() != null && request.getReviewCycle().getId() != null)
                .collect(Collectors.groupingBy(request -> request.getReviewCycle().getId()));

        Map<UUID, String> statusesBySessionId = new LinkedHashMap<>();
        for (ReviewCycle cycle : cycles) {
            if (cycle.getSession() == null || cycle.getSession().getId() == null) {
                continue;
            }

            statusesBySessionId.putIfAbsent(
                    cycle.getSession().getId(),
                    resolveReviewStatus(cycle, requestsByCycleId.getOrDefault(cycle.getId(), List.of()))
            );
        }

        return statusesBySessionId;
    }

    private String resolveReviewStatus(ReviewCycle cycle, List<ReviewRequest> requests) {
        if (cycle.getStatus() == ReviewCycle.ReviewCycleStatus.REVEALED) {
            return "REVEALED";
        }

        if (cycle.getStatus() == ReviewCycle.ReviewCycleStatus.EXPIRED_PARTIAL
                || cycle.getStatus() == ReviewCycle.ReviewCycleStatus.EXPIRED_EMPTY) {
            return "EXPIRED";
        }

        boolean hasPendingRequest = requests.stream().anyMatch(request ->
                request.getStatus() == ReviewRequest.ReviewRequestStatus.PENDING
                        || request.getStatus() == ReviewRequest.ReviewRequestStatus.SENT
                        || request.getStatus() == ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED
        );

        return hasPendingRequest ? "PENDING_FEEDBACK" : "NO_PENDING_FEEDBACK";
    }

    private String resolveMatchStatus(CompanyProgramMatchWorkspace workspace, CompanyProgramMentorAssignment assignment) {
        if (workspace != null && workspace.getStatus() != null) {
            return workspace.getStatus().name();
        }

        return assignment != null ? "ASSIGNED" : "UNASSIGNED";
    }

    private UUID programId(CompanyProgramParticipant participant) {
        return participant.getCompanyProgram() != null ? participant.getCompanyProgram().getId() : null;
    }

    private String programName(CompanyProgramParticipant participant) {
        return participant.getCompanyProgram() != null ? participant.getCompanyProgram().getName() : null;
    }

    private LocalDateTime participantDate(CompanyProgramParticipant participant) {
        return participant.getEnrolledAt() != null ? participant.getEnrolledAt() : participant.getCreatedAt();
    }

    private int countPulsesByStatus(List<ParticipantPulse> pulses, ParticipantPulse.PulseStatus status) {
        return (int) pulses.stream().filter(pulse -> pulse.getStatus() == status).count();
    }

    private int countPulsesByType(List<ParticipantPulse> pulses, ParticipantPulse.PulseType type) {
        return (int) pulses.stream().filter(pulse -> pulse.getPulseType() == type).count();
    }

    private LocalDateTime startAt(LocalDate startDate) {
        return Optional.ofNullable(startDate).orElse(DEFAULT_START_DATE).atStartOfDay();
    }

    private LocalDateTime endExclusive(LocalDate endDate) {
        return Optional.ofNullable(endDate).orElse(DEFAULT_END_DATE).plusDays(1).atStartOfDay();
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value == null || (!value.isBefore(start) && value.isBefore(end));
    }

    private String displayName(Profile profile, String fallback) {
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

    private String profileDepartment(Profile profile) {
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

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(String normalizedSearch, String... values) {
        if (normalizedSearch == null || normalizedSearch.isBlank()) {
            return true;
        }

        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
                return true;
            }
        }

        return false;
    }

    private void validateReportRequest(int page, int size, LocalDate startDate, LocalDate endDate) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 500) {
            throw new IllegalArgumentException("size must be between 1 and 500");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }

    private <T> CompanyReportDtos.ReportListDto<T> paginate(List<T> rows, int page, int size) {
        int totalItems = rows.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<T> pageRows = fromIndex >= toIndex ? List.of() : new ArrayList<>(rows.subList(fromIndex, toIndex));

        return CompanyReportDtos.ReportListDto.<T>builder()
                .rows(pageRows)
                .count(pageRows.size())
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .totalItems(totalItems)
                .hasNext(page + 1 < totalPages)
                .hasPrevious(page > 0 && totalItems > 0)
                .build();
    }

    private record ReportContext(List<CompanyProgramParticipant> participants,
                                 Map<UUID, CompanyProgramMentorAssignment> assignmentsByParticipantId,
                                 Map<UUID, CompanyProgramMatchWorkspace> workspacesByParticipantId) {
    }
}
