package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyEmployeeWhitelist;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.CompanySessionWallet;
import com.prosper.prospermentor.entity.CompanySessionWalletTransaction;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.MenteeProfile;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.repository.AccessAuditLogRepository;
import com.prosper.prospermentor.repository.CompanyEmployeeWhitelistRepository;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletTransactionRepository;
import com.prosper.prospermentor.repository.EmployeeSessionAllocationRepository;
import com.prosper.prospermentor.repository.MenteeProfileRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ParticipantPulseRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a mentee dashboard payload from profile and session data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final int DEFAULT_COMPANY_DASHBOARD_DAYS = 30;

    private static final Set<Session.SessionStatus> UPCOMING_STATUSES = EnumSet.of(
            Session.SessionStatus.PENDING,
            Session.SessionStatus.CONFIRMED,
            Session.SessionStatus.SCHEDULED,
            Session.SessionStatus.IN_PROGRESS
    );

    private final CompanyRepository companyRepository;
    private final CompanyEmployeeWhitelistRepository companyEmployeeWhitelistRepository;
    private final ProfileRepository profileRepository;
    private final MenteeProfileRepository menteeProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final SessionRepository sessionRepository;
    private final ParticipantPulseRepository participantPulseRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;
    private final CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    private final CompanySessionWalletRepository companySessionWalletRepository;
    private final CompanySessionWalletTransactionRepository companySessionWalletTransactionRepository;
    private final EmployeeSessionAllocationRepository employeeSessionAllocationRepository;
    private final ReviewAlertRepository reviewAlertRepository;
    private final AccessAuditLogRepository accessAuditLogRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> buildMenteeDashboard(UUID userId, String period) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        Optional<MenteeProfile> menteeProfileOpt = menteeProfileRepository.findById(userId);

        List<Session> sessions = new ArrayList<>(sessionRepository.findByMenteeId(userId));
        sessions.sort(Comparator.comparing(Session::getScheduledStart, Comparator.nullsLast(Comparator.reverseOrder())));

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        int periodDays = resolvePeriodDays(period);

        List<Session> upcomingSessions = sessions.stream()
                .filter(session -> isUpcomingSession(session, now))
                .sorted(Comparator.comparing(Session::getScheduledStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Set<UUID> mentorIds = sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Profile> mentorsById = profileRepository.findAllById(mentorIds).stream()
                .collect(Collectors.toMap(Profile::getId, mentor -> mentor));

        Map<UUID, MentorProfile> mentorDetailsById = mentorProfileRepository.findAllById(mentorIds).stream()
                .collect(Collectors.toMap(MentorProfile::getId, mentor -> mentor));

        List<Map<String, Object>> currentGoals = buildCurrentGoals(menteeProfileOpt.orElse(null), sessions, now);
        Map<String, Integer> goalCompletion = buildGoalCompletion(currentGoals, now);

        int totalSessions = sessions.size();
        double totalHours = roundOneDecimal(
                sessions.stream().mapToLong(this::safeDurationMinutes).sum() / 60.0
        );

        long activeMentors = sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double averageRating = roundOneDecimal(
                sessions.stream()
                        .map(Session::getRating)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0)
        );

        long currentSessionCount = countSessionsWithinDays(sessions, now, periodDays, 0);
        long previousSessionCount = countSessionsWithinDays(sessions, now, periodDays * 2, periodDays);
        int monthlyGrowth = calculatePercentGrowth(currentSessionCount, previousSessionCount);

        long currentMinutes = sumMinutesWithinDays(sessions, now, periodDays, 0);
        long previousMinutes = sumMinutesWithinDays(sessions, now, periodDays * 2, periodDays);
        int hoursGrowth = calculatePercentGrowth(currentMinutes, previousMinutes);

        double currentRating = averageRatingWithinDays(sessions, now, periodDays, 0);
        double previousRating = averageRatingWithinDays(sessions, now, periodDays * 2, periodDays);
        double ratingGrowth = roundOneDecimal(currentRating - previousRating);

        int completedGoals = goalCompletion.getOrDefault("completed", 0);
        int trackedGoals = completedGoals + goalCompletion.getOrDefault("inProgress", 0) + goalCompletion.getOrDefault("planned", 0);
        int goalCompletionRate = trackedGoals > 0
                ? (int) Math.round((completedGoals * 100.0) / trackedGoals)
                : 0;

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalSessions", totalSessions);
        overview.put("totalHours", totalHours);
        overview.put("activeMentors", activeMentors);
        overview.put("goalsCompleted", completedGoals);
        overview.put("averageRating", averageRating);
        overview.put("monthlyGrowth", monthlyGrowth);
        overview.put("goalCompletionRate", goalCompletionRate);
        overview.put("hoursGrowth", hoursGrowth);
        overview.put("ratingGrowth", ratingGrowth);
        overview.put("goalsGrowth", 0);

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("sessionTrends", buildSessionTrends(sessions, now));
        analytics.put("skillProgress", buildSkillProgress(sessions, now, periodDays));
        analytics.put("mentorRatings", buildMentorRatings(sessions, mentorsById, mentorDetailsById));
        analytics.put("goalCompletion", goalCompletion);
        analytics.put("timeDistribution", buildTimeDistribution(sessions));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("employee", buildEmployeeData(profile, menteeProfileOpt.orElse(null)));
        response.put("overview", overview);
        response.put("analytics", analytics);
        response.put("currentMentors", buildCurrentMentors(sessions, upcomingSessions, mentorsById, mentorDetailsById, now));
        response.put("upcomingSessions", buildUpcomingSessions(upcomingSessions, mentorsById));
        response.put("currentGoals", currentGoals);
        response.put("recentActivity", buildRecentActivity(sessions, mentorsById));
        response.put("recommendations", buildRecommendations(
                activeMentors,
                upcomingSessions.size(),
                currentGoals.size(),
                averageRating,
                totalSessions
        ));

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildCompanyDashboard(UUID companyId, String period) {
        return buildCompanyDashboard(companyId, period, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildCompanyDashboard(UUID companyId,
                                                     String period,
                                                     LocalDate startDate,
                                                     LocalDate endDate) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DashboardDateRange dateRange = resolveCompanyDashboardDateRange(period, startDate, endDate, zoneId, now);

        List<Profile> companyProfiles = new ArrayList<>(profileRepository.findByCompanyId(companyId));
        List<Profile> employeeProfiles = companyProfiles.stream()
                .filter(this::isEmployeeProfile)
                .toList();

        List<CompanyEmployeeWhitelist> whitelistEntries = new ArrayList<>(
                companyEmployeeWhitelistRepository.findByCompanyId(companyId)
        );
        List<CompanyEmployeeWhitelist> filteredWhitelistEntries = whitelistEntries.stream()
                .filter(entry -> isWithin(whitelistEventTime(entry, zoneId), dateRange.currentStart(), dateRange.currentEnd()))
                .toList();

        List<UUID> employeeIds = employeeProfiles.stream()
                .map(Profile::getId)
                .toList();

        List<Session> allSessions = employeeIds.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(sessionRepository.findByMenteeIdIn(employeeIds));

        List<Session> currentPeriodSessions = allSessions.stream()
                .filter(session -> isWithin(sessionActivityTime(session, zoneId), dateRange.currentStart(), dateRange.currentEnd()))
                .toList();

        List<Session> previousPeriodSessions = allSessions.stream()
                .filter(session -> isWithin(sessionActivityTime(session, zoneId), dateRange.previousStart(), dateRange.currentStart()))
                .toList();

        List<Session> sessions = new ArrayList<>(currentPeriodSessions);
        sessions.sort(Comparator.comparing(this::sessionDisplayTime, Comparator.nullsLast(Comparator.reverseOrder())));

        int periodDays = dateRange.periodDays();

        Map<UUID, Profile> employeeProfilesById = employeeProfiles.stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        Set<UUID> mentorIds = sessions.stream()
                .map(Session::getMentorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Profile> mentorsById = profileRepository.findAllById(mentorIds).stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));

        List<Session> completedSessions = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .toList();

        List<Session> ratedCompletedSessions = completedSessions.stream()
                .filter(session -> session.getRating() != null)
                .toList();

        long participatingEmployees = sessions.stream()
                .map(Session::getMenteeId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long activeEmployeesCurrentPeriod = currentPeriodSessions.stream()
                .map(Session::getMenteeId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long activeEmployeesPreviousPeriod = previousPeriodSessions.stream()
                .map(Session::getMenteeId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        int completedCount = completedSessions.size();
        int upcomingCount = (int) sessions.stream()
                .filter(session -> isCompanyUpcomingSession(session, now))
                .count();
        int pendingCount = (int) sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.PENDING)
                .count();
        int cancelledCount = (int) sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.CANCELLED)
                .count();

        int paidSessionsCount = (int) sessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.getPaid()))
                .count();

        int unpaidSessionsCount = (int) sessions.stream()
                .filter(session -> !Boolean.TRUE.equals(session.getPaid()))
                .filter(session -> session.getPrice() != null && session.getPrice().doubleValue() > 0)
                .count();

        int verifiedEmployees = (int) employeeProfiles.stream()
                .filter(profile -> Boolean.TRUE.equals(profile.getIsVerified()))
                .count();

        int invitesSentCount = (int) filteredWhitelistEntries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getInvitationSent()))
                .count();

        int acceptedInvitesCount = (int) filteredWhitelistEntries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getInvitationAccepted()))
                .count();

        int pendingInvitationSendCount = (int) filteredWhitelistEntries.stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getInvitationSent()))
                .count();

        int awaitingAcceptanceCount = (int) filteredWhitelistEntries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getInvitationSent()) && !Boolean.TRUE.equals(entry.getInvitationAccepted()))
                .count();

        int sessionsWithFeedback = (int) completedSessions.stream()
                .filter(session -> session.getFeedback() != null && !session.getFeedback().isBlank() || session.getRating() != null)
                .count();
        List<ParticipantPulse> pulses = participantPulseRepository.findByParticipant_CompanyProgram_Company_IdOrderByCreatedAtDesc(companyId)
                .stream()
                .filter(pulse -> isWithin(pulseActivityTime(pulse, zoneId), dateRange.currentStart(), dateRange.currentEnd()))
                .toList();
        long completedPulses = pulses.stream()
                .filter(pulse -> pulse.getStatus() == ParticipantPulse.PulseStatus.COMPLETED)
                .count();
        long pendingPulses = pulses.stream()
                .filter(pulse -> pulse.getStatus() == ParticipantPulse.PulseStatus.PENDING)
                .count();

        List<Map<String, Object>> employeeAggregates = buildCompanyEmployeeAggregates(
                employeeProfiles,
                employeeProfilesById,
                sessions,
                now,
                zoneId
        );

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("periodDays", periodDays);
        stats.put("totalSessions", sessions.size());
        stats.put("registeredEmployees", employeeProfiles.size());
        stats.put("newEmployeesLastPeriod", employeeProfiles.stream()
                .filter(profile -> isWithin(profile.getCreatedAt(), dateRange.currentStart(), dateRange.currentEnd()))
                .count());
        stats.put("participatingEmployees", participatingEmployees);
        stats.put("participationRate", percentage(participatingEmployees, employeeProfiles.size()));
        stats.put("activeEmployeesCurrentPeriod", activeEmployeesCurrentPeriod);
        stats.put("activeEmployeesPreviousPeriod", activeEmployeesPreviousPeriod);
        stats.put("sessionsCurrentPeriod", currentPeriodSessions.size());
        stats.put("sessionsPreviousPeriod", previousPeriodSessions.size());
        stats.put("hoursCurrentPeriod", roundOneDecimal(currentPeriodSessions.stream()
                .mapToLong(this::safeDurationMinutes)
                .sum() / 60.0));
        stats.put("hoursPreviousPeriod", roundOneDecimal(previousPeriodSessions.stream()
                .mapToLong(this::safeDurationMinutes)
                .sum() / 60.0));
        stats.put("totalHours", roundOneDecimal(sessions.stream()
                .mapToLong(this::safeDurationMinutes)
                .sum() / 60.0));
        stats.put("averageRating", roundOneDecimal(ratedCompletedSessions.stream()
                .map(Session::getRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0)));
        stats.put("completionRate", percentage(completedCount, sessions.size()));
        stats.put("feedbackCoverage", percentage(sessionsWithFeedback, completedCount));
        stats.put("pulseCompletionRate", percentage(completedPulses, pulses.size()));
        stats.put("totalPulses", pulses.size());
        stats.put("completedPulses", completedPulses);
        stats.put("pendingPulses", pendingPulses);
        stats.put("completedSessions", completedCount);
        stats.put("upcomingSessions", upcomingCount);
        stats.put("pendingSessions", pendingCount);
        stats.put("cancelledSessions", cancelledCount);
        stats.put("paidSessionsCount", paidSessionsCount);
        stats.put("unpaidSessionsCount", unpaidSessionsCount);
        stats.put("verifiedEmployees", verifiedEmployees);
        stats.put("verificationRate", percentage(verifiedEmployees, employeeProfiles.size()));
        stats.put("whitelistTotal", filteredWhitelistEntries.size());
        stats.put("invitesSentCount", invitesSentCount);
        stats.put("acceptedInvitesCount", acceptedInvitesCount);
        stats.put("pendingInvitationSendCount", pendingInvitationSendCount);
        stats.put("awaitingAcceptanceCount", awaitingAcceptanceCount);
        stats.put("invitationAcceptanceRate", percentage(acceptedInvitesCount, invitesSentCount));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("company", Map.of(
                "id", company.getId().toString(),
                "name", defaultString(company.getName(), "Your company")
        ));
        response.put("snapshotAt", now.toString());
        response.put("period", dateRange.periodLabel());
        response.put("startDate", dateRange.startDate().toString());
        response.put("endDate", dateRange.endDate().toString());
        response.put("loadSummary", Map.of(
                "requestedEmployees", employeeProfiles.size(),
                "loadedEmployees", employeeProfiles.size(),
                "failedEmployees", 0
        ));
        response.put("stats", stats);
        response.put("insights", buildCompanyInsights(
                companyId,
                filteredWhitelistEntries,
                sessions,
                dateRange,
                zoneId,
                now
        ));
        response.put("employeeAggregates", employeeAggregates);
        response.put("recentRegistrations", buildCompanyRecentRegistrations(
                employeeProfiles.stream()
                        .filter(profile -> isWithin(profile.getCreatedAt(), dateRange.currentStart(), dateRange.currentEnd()))
                        .toList()
        ));
        response.put("monthlyTrends", buildCompanyMonthlyTrends(sessions, now, zoneId));
        response.put("topTopics", buildCompanyTopTopics(sessions));
        response.put("mentorLeaderboard", buildCompanyMentorLeaderboard(sessions, mentorsById));
        response.put("recentActivity", buildCompanyRecentActivity(
                employeeProfiles,
                filteredWhitelistEntries,
                sessions,
                employeeProfilesById,
                mentorsById,
                zoneId
        ));
        response.put("statusBreakdown", buildCompanyStatusBreakdown(
                sessions.size(),
                completedCount,
                upcomingCount,
                pendingCount,
                cancelledCount
        ));

        return response;
    }

    private List<Map<String, Object>> buildCompanyEmployeeAggregates(
            List<Profile> employeeProfiles,
            Map<UUID, Profile> employeeProfilesById,
            List<Session> sessions,
            ZonedDateTime now,
            ZoneId zoneId
    ) {
        Map<UUID, Map<String, Object>> aggregates = new LinkedHashMap<>();

        employeeProfiles.forEach(profile -> aggregates.put(profile.getId(), createEmployeeAggregate(profile)));

        for (Session session : sessions) {
            UUID employeeId = session.getMenteeId();
            if (employeeId == null) {
                continue;
            }

            Profile employeeProfile = employeeProfilesById.get(employeeId);
            Map<String, Object> aggregate = aggregates.computeIfAbsent(employeeId, ignored -> createEmployeeAggregate(employeeProfile));

            aggregate.put("sessions", numberValue(aggregate.get("sessions")) + 1);
            aggregate.put("hours", roundOneDecimal(
                    decimalValue(aggregate.get("hours")) + (safeDurationMinutes(session) / 60.0)
            ));

            if (session.getStatus() == Session.SessionStatus.COMPLETED) {
                aggregate.put("completed", numberValue(aggregate.get("completed")) + 1);
            }

            if (isCompanyUpcomingSession(session, now)) {
                aggregate.put("upcoming", numberValue(aggregate.get("upcoming")) + 1);
            }

            if (session.getRating() != null) {
                int ratingCount = numberValue(aggregate.get("ratingCount")) + 1;
                int ratingSum = numberValue(aggregate.get("ratingSum")) + session.getRating();

                aggregate.put("ratingCount", ratingCount);
                aggregate.put("ratingSum", ratingSum);
                aggregate.put("averageRating", roundOneDecimal(ratingSum / (double) ratingCount));
            }

            ZonedDateTime candidateTime = sessionActivityTime(session, zoneId);
            String currentLastSessionAt = (String) aggregate.get("lastSessionAt");
            if (candidateTime != null && (currentLastSessionAt == null || candidateTime.isAfter(ZonedDateTime.parse(currentLastSessionAt)))) {
                aggregate.put("lastSessionAt", candidateTime.toString());
            }
        }

        return aggregates.values().stream()
                .sorted((left, right) -> {
                    int sessionsDiff = Integer.compare(numberValue(right.get("sessions")), numberValue(left.get("sessions")));
                    if (sessionsDiff != 0) {
                        return sessionsDiff;
                    }

                    int completedDiff = Integer.compare(numberValue(right.get("completed")), numberValue(left.get("completed")));
                    if (completedDiff != 0) {
                        return completedDiff;
                    }

                    return Double.compare(decimalValue(right.get("hours")), decimalValue(left.get("hours")));
                })
                .map(aggregate -> {
                    aggregate.remove("ratingCount");
                    aggregate.remove("ratingSum");
                    return aggregate;
                })
                .toList();
    }

    private Map<String, Object> createEmployeeAggregate(Profile profile) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("id", profile != null && profile.getId() != null ? profile.getId().toString() : "");
        aggregate.put("name", profile != null ? buildDisplayName(profile) : "Employee");
        aggregate.put("email", profile != null ? defaultString(profile.getEmail(), "") : "");
        aggregate.put("meta", buildProfileMeta(profile));
        aggregate.put("sessions", 0);
        aggregate.put("completed", 0);
        aggregate.put("upcoming", 0);
        aggregate.put("hours", 0.0);
        aggregate.put("ratingCount", 0);
        aggregate.put("ratingSum", 0);
        aggregate.put("averageRating", null);
        aggregate.put("lastSessionAt", null);
        return aggregate;
    }

    private List<Map<String, Object>> buildCompanyRecentRegistrations(List<Profile> employeeProfiles) {
        return employeeProfiles.stream()
                .sorted(Comparator.comparing(Profile::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::buildProfileSummary)
                .toList();
    }

    private Map<String, Object> buildProfileSummary(Profile profile) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", profile.getId().toString());
        summary.put("email", defaultString(profile.getEmail(), ""));
        summary.put("username", defaultString(profile.getUsername(), ""));
        summary.put("firstName", defaultString(profile.getFirstName(), ""));
        summary.put("lastName", defaultString(profile.getLastName(), ""));
        summary.put("isVerified", Boolean.TRUE.equals(profile.getIsVerified()));
        summary.put("createdAt", profile.getCreatedAt() != null ? profile.getCreatedAt().toString() : null);
        summary.put("location", profile.getLocation());
        summary.put("industry", profile.getIndustry());
        return summary;
    }

    private List<Map<String, Object>> buildCompanyMonthlyTrends(List<Session> sessions, ZonedDateTime now, ZoneId zoneId) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int offset = 5; offset >= 0; offset--) {
            ZonedDateTime monthStart = now.minusMonths(offset)
                    .withDayOfMonth(1)
                    .truncatedTo(ChronoUnit.DAYS);
            ZonedDateTime monthEnd = monthStart.plusMonths(1);

            List<Session> monthSessions = sessions.stream()
                    .filter(session -> isWithin(sessionActivityTime(session, zoneId), monthStart, monthEnd))
                    .toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", monthStart.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
            row.put("sessions", monthSessions.size());
            row.put("completed", monthSessions.stream()
                    .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                    .count());
            row.put("hours", roundOneDecimal(monthSessions.stream()
                    .mapToLong(this::safeDurationMinutes)
                    .sum() / 60.0));
            rows.add(row);
        }

        int maxSessions = rows.stream()
                .mapToInt(row -> numberValue(row.get("sessions")))
                .max()
                .orElse(1);

        rows.forEach(row -> row.put("width", maxSessions > 0
                ? (int) Math.round((numberValue(row.get("sessions")) * 100.0) / maxSessions)
                : 0));

        return rows;
    }

    private List<Map<String, Object>> buildCompanyTopTopics(List<Session> sessions) {
        Map<String, Map<String, Object>> topics = new LinkedHashMap<>();

        for (Session session : sessions) {
            String label = resolveSkillName(session);
            Map<String, Object> topic = topics.computeIfAbsent(label, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("label", label);
                value.put("sessions", 0);
                value.put("hours", 0.0);
                value.put("completed", 0);
                return value;
            });

            topic.put("sessions", numberValue(topic.get("sessions")) + 1);
            topic.put("hours", roundOneDecimal(decimalValue(topic.get("hours")) + (safeDurationMinutes(session) / 60.0)));

            if (session.getStatus() == Session.SessionStatus.COMPLETED) {
                topic.put("completed", numberValue(topic.get("completed")) + 1);
            }
        }

        return topics.values().stream()
                .sorted((left, right) -> {
                    int sessionsDiff = Integer.compare(numberValue(right.get("sessions")), numberValue(left.get("sessions")));
                    if (sessionsDiff != 0) {
                        return sessionsDiff;
                    }
                    return Double.compare(decimalValue(right.get("hours")), decimalValue(left.get("hours")));
                })
                .limit(6)
                .toList();
    }

    private List<Map<String, Object>> buildCompanyMentorLeaderboard(
            List<Session> sessions,
            Map<UUID, Profile> mentorsById
    ) {
        Map<String, Map<String, Object>> mentors = new LinkedHashMap<>();

        for (Session session : sessions) {
            String mentorKey = session.getMentorId() != null ? session.getMentorId().toString() : "mentor";
            Profile mentorProfile = session.getMentorId() != null ? mentorsById.get(session.getMentorId()) : null;

            Map<String, Object> mentor = mentors.computeIfAbsent(mentorKey, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", mentorKey);
                value.put("name", mentorProfile != null ? buildDisplayName(mentorProfile) : "Mentor");
                value.put("sessions", 0);
                value.put("completed", 0);
                value.put("ratingCount", 0);
                value.put("ratingSum", 0);
                value.put("averageRating", null);
                return value;
            });

            mentor.put("sessions", numberValue(mentor.get("sessions")) + 1);

            if (session.getStatus() == Session.SessionStatus.COMPLETED) {
                mentor.put("completed", numberValue(mentor.get("completed")) + 1);
            }

            if (session.getRating() != null) {
                int ratingCount = numberValue(mentor.get("ratingCount")) + 1;
                int ratingSum = numberValue(mentor.get("ratingSum")) + session.getRating();
                mentor.put("ratingCount", ratingCount);
                mentor.put("ratingSum", ratingSum);
                mentor.put("averageRating", roundOneDecimal(ratingSum / (double) ratingCount));
            }
        }

        return mentors.values().stream()
                .sorted((left, right) -> {
                    int sessionsDiff = Integer.compare(numberValue(right.get("sessions")), numberValue(left.get("sessions")));
                    if (sessionsDiff != 0) {
                        return sessionsDiff;
                    }
                    return Double.compare(decimalValue(right.get("averageRating")), decimalValue(left.get("averageRating")));
                })
                .limit(6)
                .map(mentor -> {
                    mentor.remove("ratingCount");
                    mentor.remove("ratingSum");
                    return mentor;
                })
                .toList();
    }

    private List<Map<String, Object>> buildCompanyRecentActivity(
            List<Profile> employeeProfiles,
            List<CompanyEmployeeWhitelist> whitelistEntries,
            List<Session> sessions,
            Map<UUID, Profile> employeeProfilesById,
            Map<UUID, Profile> mentorsById,
            ZoneId zoneId
    ) {
        List<Map<String, Object>> items = new ArrayList<>();

        employeeProfiles.stream()
                .sorted(Comparator.comparing(Profile::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .forEach(profile -> {
                    if (profile.getCreatedAt() == null) {
                        return;
                    }

                    items.add(createActivity(
                            "profile-" + profile.getId(),
                            "employee_registered",
                            "Employee registered",
                            buildDisplayName(profile) + " joined the company workspace",
                            profile.getCreatedAt(),
                            "medium"
                    ));
                });

        whitelistEntries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getInvitationAccepted()))
                .sorted(Comparator.comparing(
                        entry -> whitelistEventTime(entry, zoneId),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(6)
                .forEach(entry -> {
                    ZonedDateTime eventTime = whitelistEventTime(entry, zoneId);
                    if (eventTime == null) {
                        return;
                    }

                    String employeeName = entry.getProfile() != null
                            ? buildDisplayName(entry.getProfile())
                            : defaultString(entry.getEmail(), "Employee");

                    items.add(createActivity(
                            "invite-" + entry.getId(),
                            "invite_accepted",
                            "Invitation accepted",
                            employeeName + " completed the company invite flow",
                            eventTime,
                            "low"
                    ));
                });

        sessions.stream()
                .sorted(Comparator.comparing(
                        session -> sessionRecentActivityTime(session, zoneId),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(10)
                .forEach(session -> {
                    ZonedDateTime eventTime = sessionRecentActivityTime(session, zoneId);
                    if (eventTime == null) {
                        return;
                    }

                    String employeeName = resolveProfileName(employeeProfilesById.get(session.getMenteeId()), "Employee");
                    String mentorName = resolveProfileName(
                            session.getMentorId() != null ? mentorsById.get(session.getMentorId()) : null,
                            "Mentor"
                    );

                    if (session.getStatus() == Session.SessionStatus.COMPLETED) {
                        items.add(createActivity(
                                "session-completed-" + session.getId(),
                                "session_completed",
                                "Session completed",
                                employeeName + " met with " + mentorName,
                                eventTime,
                                "medium"
                        ));
                        return;
                    }

                    if (session.getStatus() == Session.SessionStatus.CANCELLED) {
                        items.add(createActivity(
                                "session-cancelled-" + session.getId(),
                                "session_cancelled",
                                "Session cancelled",
                                employeeName + "'s session with " + mentorName + " was cancelled",
                                eventTime,
                                "high"
                        ));
                        return;
                    }

                    if (session.getStatus() == Session.SessionStatus.PENDING) {
                        items.add(createActivity(
                                "session-pending-" + session.getId(),
                                "session_requested",
                                "Session awaiting review",
                                employeeName + " requested time with " + mentorName,
                                eventTime,
                                "high"
                        ));
                        return;
                    }

                    items.add(createActivity(
                            "session-confirmed-" + session.getId(),
                            "session_confirmed",
                            "Session scheduled",
                            employeeName + " has a confirmed session with " + mentorName,
                            eventTime,
                            "low"
                    ));
                });

        return items.stream()
                .sorted(Comparator.comparing(
                        activity -> ZonedDateTime.parse((String) activity.get("timestamp")),
                        Comparator.reverseOrder()
                ))
                .limit(8)
                .toList();
    }

    private Map<String, Object> createActivity(
            String id,
            String type,
            String title,
            String description,
            ZonedDateTime timestamp,
            String priority
    ) {
        Map<String, Object> activity = new LinkedHashMap<>();
        String canonicalTimestamp = formatActivityTimestamp(timestamp);
        activity.put("id", id);
        activity.put("type", type);
        activity.put("title", title);
        activity.put("description", description);
        activity.put("timestamp", canonicalTimestamp);
        activity.put("createdAt", canonicalTimestamp);
        activity.put("priority", priority);
        return activity;
    }

    private List<Map<String, Object>> buildCompanyStatusBreakdown(
            int totalSessions,
            int completedCount,
            int upcomingCount,
            int pendingCount,
            int cancelledCount
    ) {
        int denominator = Math.max(totalSessions, 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createStatusBreakdownRow("completed", "Completed", completedCount, denominator));
        rows.add(createStatusBreakdownRow("upcoming", "Upcoming", upcomingCount, denominator));
        rows.add(createStatusBreakdownRow("pending", "Pending", pendingCount, denominator));
        rows.add(createStatusBreakdownRow("cancelled", "Cancelled", cancelledCount, denominator));
        return rows;
    }

    private Map<String, Object> createStatusBreakdownRow(String key, String label, int count, int totalSessions) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        row.put("count", count);
        row.put("percentage", percentage(count, totalSessions));
        return row;
    }

    private Map<String, Object> buildCompanyInsights(UUID companyId,
                                                     List<CompanyEmployeeWhitelist> filteredWhitelistEntries,
                                                     List<Session> currentPeriodSessions,
                                                     DashboardDateRange dateRange,
                                                     ZoneId zoneId,
                                                     ZonedDateTime now) {
        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository
                .findByCompanyProgram_Company_Id(companyId);
        List<CompanyProgramParticipant> filteredParticipants = participants.stream()
                .filter(participant -> isWithin(
                        toZoned(participant.getEnrolledAt(), zoneId),
                        dateRange.currentStart(),
                        dateRange.currentEnd()
                ))
                .toList();

        List<CompanyProgramMentorAssignment> assignments = companyProgramMentorAssignmentRepository
                .findByParticipant_CompanyProgram_Company_Id(companyId);
        Map<UUID, CompanyProgramMentorAssignment> assignmentByParticipantId = assignments.stream()
                .filter(assignment -> assignment.getParticipant() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getParticipant().getId(),
                        assignment -> assignment,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("employeeFunnel", buildEmployeeFunnelInsights(
                filteredWhitelistEntries,
                filteredParticipants,
                assignmentByParticipantId,
                currentPeriodSessions
        ));
        insights.put("matchingSla", buildMatchingSlaInsights(
                companyId,
                filteredParticipants,
                assignmentByParticipantId,
                dateRange
        ));
        insights.put("walletUtilization", buildWalletUtilizationInsights(companyId, dateRange, zoneId));
        insights.put("riskBacklog", buildRiskBacklogInsights(companyId, dateRange, now, zoneId));
        return insights;
    }

    private Map<String, Object> buildEmployeeFunnelInsights(List<CompanyEmployeeWhitelist> whitelistEntries,
                                                            List<CompanyProgramParticipant> participants,
                                                            Map<UUID, CompanyProgramMentorAssignment> assignmentByParticipantId,
                                                            List<Session> sessions) {
        Map<UUID, Long> completedSessionsByParticipantId = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .filter(session -> session.getCompanyProgramParticipantId() != null)
                .collect(Collectors.groupingBy(
                        Session::getCompanyProgramParticipantId,
                        Collectors.counting()
                ));

        long invitedCount = distinctWhitelistCount(whitelistEntries, CompanyEmployeeWhitelist::getInvitationSent);
        long acceptedCount = distinctWhitelistCount(whitelistEntries, CompanyEmployeeWhitelist::getInvitationAccepted);
        long enrolledCount = participants.size();
        long activeCount = participants.stream()
                .filter(participant -> participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ACTIVE)
                .count();
        long mentorAssignedCount = participants.stream()
                .filter(participant -> assignmentByParticipantId.containsKey(participant.getId()))
                .count();
        long firstSessionCompletedCount = participants.stream()
                .filter(participant -> completedSessionsByParticipantId.getOrDefault(participant.getId(), 0L) >= 1)
                .count();
        long threePlusSessionsCount = participants.stream()
                .filter(participant -> completedSessionsByParticipantId.getOrDefault(participant.getId(), 0L) >= 3)
                .count();
        long programCompletedCount = participants.stream()
                .filter(participant -> participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.COMPLETED)
                .count();

        List<Map<String, Object>> stages = List.of(
                createFunnelStage("invited", "Invited", invitedCount),
                createFunnelStage("acceptedInvite", "Accepted Invite", acceptedCount),
                createFunnelStage("enrolled", "Enrolled", enrolledCount),
                createFunnelStage("active", "Active", activeCount),
                createFunnelStage("mentorAssigned", "Mentor Assigned", mentorAssignedCount),
                createFunnelStage("firstSessionCompleted", "1st Session Completed", firstSessionCompletedCount),
                createFunnelStage("threePlusSessions", "3+ Sessions", threePlusSessionsCount),
                createFunnelStage("programCompleted", "Program Completed", programCompletedCount)
        );

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("stages", stages);
        funnel.put("inviteToEnrollmentRate", percentage(enrolledCount, invitedCount));
        funnel.put("assignmentCoverageRate", percentage(mentorAssignedCount, enrolledCount));
        funnel.put("completionRate", percentage(programCompletedCount, enrolledCount));
        return funnel;
    }

    private Map<String, Object> buildMatchingSlaInsights(UUID companyId,
                                                         List<CompanyProgramParticipant> participants,
                                                         Map<UUID, CompanyProgramMentorAssignment> assignmentByParticipantId,
                                                         DashboardDateRange dateRange) {
        List<CompanyProgramParticipant> assignableParticipants = participants.stream()
                .filter(participant -> participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ENROLLED
                        || participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ACTIVE)
                .toList();

        long assignedParticipants = assignableParticipants.stream()
                .filter(participant -> assignmentByParticipantId.containsKey(participant.getId()))
                .count();

        List<Double> assignmentDelaysInDays = assignableParticipants.stream()
                .map(participant -> {
                    CompanyProgramMentorAssignment assignment = assignmentByParticipantId.get(participant.getId());
                    if (assignment == null || assignment.getAssignedAt() == null || participant.getEnrolledAt() == null) {
                        return null;
                    }
                    long hours = ChronoUnit.HOURS.between(participant.getEnrolledAt(), assignment.getAssignedAt());
                    return Math.max(0, hours) / 24.0;
                })
                .filter(Objects::nonNull)
                .toList();

        long assignedWithin7Days = assignmentDelaysInDays.stream()
                .filter(delay -> delay <= 7.0)
                .count();

        LocalDateTime rangeStart = dateRange.startDate().atStartOfDay();
        LocalDateTime rangeEndExclusive = dateRange.endDate().plusDays(1).atStartOfDay();
        long rematchActions = accessAuditLogRepository.countByCompanyActionInRange(
                companyId,
                AccessAuditLog.ActionType.REMATCH,
                rangeStart,
                rangeEndExclusive
        );

        Map<String, Object> matchingSla = new LinkedHashMap<>();
        matchingSla.put("eligibleParticipants", assignableParticipants.size());
        matchingSla.put("assignedParticipants", assignedParticipants);
        matchingSla.put("unassignedParticipants", Math.max(assignableParticipants.size() - (int) assignedParticipants, 0));
        matchingSla.put("coverageRate", percentage(assignedParticipants, assignableParticipants.size()));
        matchingSla.put("medianAssignmentDays", median(assignmentDelaysInDays));
        matchingSla.put("assignedWithin7DaysRate", percentage(assignedWithin7Days, assignmentDelaysInDays.size()));
        matchingSla.put("rematchActions", rematchActions);
        matchingSla.put("rematchRate", percentage(rematchActions, assignedParticipants));
        return matchingSla;
    }

    private Map<String, Object> buildWalletUtilizationInsights(UUID companyId,
                                                               DashboardDateRange dateRange,
                                                               ZoneId zoneId) {
        CompanySessionWallet wallet = companySessionWalletRepository.findByCompany_Id(companyId).orElse(null);

        List<EmployeeSessionAllocation> allocations = employeeSessionAllocationRepository.findByCompany_Id(companyId);
        long consumedSessions = allocations.stream()
                .mapToLong(allocation -> allocation.getConsumedTotal() != null ? allocation.getConsumedTotal() : 0)
                .sum();
        long employeeAvailableBalance = allocations.stream()
                .mapToLong(allocation -> allocation.getAvailableBalance() != null ? allocation.getAvailableBalance() : 0)
                .sum();

        int purchasedSessions = wallet != null ? safeInteger(wallet.getSessionsPurchasedTotal()) : 0;
        int allocatedSessions = wallet != null ? safeInteger(wallet.getSessionsAllocatedTotal()) : 0;
        int returnedSessions = wallet != null ? safeInteger(wallet.getSessionsReturnedTotal()) : 0;
        int availableSessions = wallet != null ? safeInteger(wallet.getSessionsAvailable()) : 0;

        List<CompanySessionWalletTransaction> transactions = wallet != null
                ? companySessionWalletTransactionRepository.findByWallet_IdOrderByCreatedAtDesc(wallet.getId())
                : List.of();

        List<Map<String, Object>> dailyFlow = buildWalletDailyFlow(transactions, dateRange, zoneId);

        Map<String, Object> walletUtilization = new LinkedHashMap<>();
        walletUtilization.put("purchasedSessions", purchasedSessions);
        walletUtilization.put("allocatedSessions", allocatedSessions);
        walletUtilization.put("consumedSessions", consumedSessions);
        walletUtilization.put("returnedSessions", returnedSessions);
        walletUtilization.put("availableSessions", availableSessions);
        walletUtilization.put("employeeAvailableBalance", employeeAvailableBalance);
        walletUtilization.put("utilizationRate", percentage(consumedSessions, purchasedSessions));
        walletUtilization.put("allocationRate", percentage(allocatedSessions, purchasedSessions));
        walletUtilization.put("employeeBalanceRate", percentage(employeeAvailableBalance, allocatedSessions));
        walletUtilization.put("dailyFlow", dailyFlow);
        return walletUtilization;
    }

    private List<Map<String, Object>> buildWalletDailyFlow(List<CompanySessionWalletTransaction> transactions,
                                                           DashboardDateRange dateRange,
                                                           ZoneId zoneId) {
        Map<LocalDate, Map<String, Integer>> byDate = new LinkedHashMap<>();
        LocalDate cursor = dateRange.startDate();
        while (!cursor.isAfter(dateRange.endDate())) {
            Map<String, Integer> row = new LinkedHashMap<>();
            row.put("purchased", 0);
            row.put("allocatedOut", 0);
            row.put("allocationReturn", 0);
            row.put("manualAdjustment", 0);
            byDate.put(cursor, row);
            cursor = cursor.plusDays(1);
        }

        for (CompanySessionWalletTransaction transaction : transactions) {
            ZonedDateTime eventTime = toZoned(transaction.getCreatedAt(), zoneId);
            if (!isWithin(eventTime, dateRange.currentStart(), dateRange.currentEnd())) {
                continue;
            }

            LocalDate eventDate = eventTime.toLocalDate();
            Map<String, Integer> row = byDate.get(eventDate);
            if (row == null) {
                continue;
            }

            int quantity = safeInteger(transaction.getQuantity());
            switch (transaction.getTransactionType()) {
                case PURCHASE -> row.put("purchased", row.get("purchased") + quantity);
                case ALLOCATION_OUT -> row.put("allocatedOut", row.get("allocatedOut") + quantity);
                case ALLOCATION_RETURN -> row.put("allocationReturn", row.get("allocationReturn") + quantity);
                case MANUAL_ADJUSTMENT -> row.put("manualAdjustment", row.get("manualAdjustment") + quantity);
            }
        }

        return byDate.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", entry.getKey().toString());
                    row.putAll(entry.getValue());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildRiskBacklogInsights(UUID companyId,
                                                         DashboardDateRange dateRange,
                                                         ZonedDateTime now,
                                                         ZoneId zoneId) {
        LocalDateTime rangeStart = dateRange.startDate().atStartOfDay();
        LocalDateTime rangeEndExclusive = dateRange.endDate().plusDays(1).atStartOfDay();
        List<ReviewAlert> alerts = reviewAlertRepository.findCompanyAlertsForSummary(
                companyId,
                null,
                rangeStart,
                rangeEndExclusive
        );

        List<ReviewAlert> unresolved = alerts.stream()
                .filter(alert -> alert.getStatus() == ReviewAlert.ReviewAlertStatus.OPEN
                        || alert.getStatus() == ReviewAlert.ReviewAlertStatus.ACKNOWLEDGED)
                .toList();

        Map<String, Long> ageBuckets = new LinkedHashMap<>();
        ageBuckets.put("0-3 days", 0L);
        ageBuckets.put("4-7 days", 0L);
        ageBuckets.put("8-14 days", 0L);
        ageBuckets.put("15+ days", 0L);

        for (ReviewAlert alert : unresolved) {
            long ageDays = alert.getCreatedAt() == null
                    ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(alert.getCreatedAt().toLocalDate(), now.toLocalDate()));

            if (ageDays <= 3) {
                ageBuckets.put("0-3 days", ageBuckets.get("0-3 days") + 1);
            } else if (ageDays <= 7) {
                ageBuckets.put("4-7 days", ageBuckets.get("4-7 days") + 1);
            } else if (ageDays <= 14) {
                ageBuckets.put("8-14 days", ageBuckets.get("8-14 days") + 1);
            } else {
                ageBuckets.put("15+ days", ageBuckets.get("15+ days") + 1);
            }
        }

        Map<ReviewAlert.ReviewAlertType, Long> unresolvedByType = unresolved.stream()
                .collect(Collectors.groupingBy(ReviewAlert::getAlertType, Collectors.counting()));

        List<Map<String, Object>> byType = unresolvedByType.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", entry.getKey().name());
                    row.put("count", entry.getValue());
                    return row;
                })
                .sorted((left, right) -> Long.compare(
                        ((Number) right.get("count")).longValue(),
                        ((Number) left.get("count")).longValue()
                ))
                .toList();

        List<Map<String, Object>> ageBreakdown = ageBuckets.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bucket", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();

        long highSeverityOpen = unresolved.stream()
                .filter(alert -> alert.getSeverity() == ReviewAlert.Severity.HIGH)
                .count();
        long resolvedCount = alerts.stream()
                .filter(alert -> alert.getStatus() == ReviewAlert.ReviewAlertStatus.RESOLVED)
                .count();

        Map<String, Object> riskBacklog = new LinkedHashMap<>();
        riskBacklog.put("openAlerts", unresolved.size());
        riskBacklog.put("highSeverityOpenAlerts", highSeverityOpen);
        riskBacklog.put("resolvedAlerts", resolvedCount);
        riskBacklog.put("resolutionRate", percentage(resolvedCount, alerts.size()));
        riskBacklog.put("byType", byType);
        riskBacklog.put("ageBuckets", ageBreakdown);
        return riskBacklog;
    }

    private Map<String, Object> createFunnelStage(String key, String label, long count) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("key", key);
        stage.put("label", label);
        stage.put("count", count);
        return stage;
    }

    private long distinctWhitelistCount(List<CompanyEmployeeWhitelist> entries,
                                        java.util.function.Function<CompanyEmployeeWhitelist, Boolean> predicate) {
        return entries.stream()
                .filter(entry -> Boolean.TRUE.equals(predicate.apply(entry)))
                .map(entry -> {
                    if (entry.getEmail() != null && !entry.getEmail().isBlank()) {
                        return entry.getEmail().trim().toLowerCase(Locale.ROOT);
                    }
                    return entry.getId() != null ? entry.getId().toString() : "";
                })
                .filter(value -> !value.isBlank())
                .distinct()
                .count();
    }

    private Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<Double> sorted = values.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }

        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return roundOneDecimal((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
        }
        return roundOneDecimal(sorted.get(middle));
    }

    private int safeInteger(Integer value) {
        return value != null ? value : 0;
    }

    private boolean isEmployeeProfile(Profile profile) {
        return "employee".equals(normalizeRole(profile != null ? profile.getRole() : null));
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);

        if ("company".equals(normalized) || "corporate_admin".equals(normalized)) {
            return "corporate_admin";
        }

        if ("mentee".equals(normalized) || "employee".equals(normalized)) {
            return "employee";
        }

        if ("mentor".equals(normalized)) {
            return "mentor";
        }

        return normalized;
    }

    private String buildProfileMeta(Profile profile) {
        if (profile == null) {
            return "Employee profile";
        }

        if (profile.getIndustry() != null && !profile.getIndustry().isBlank()) {
            return profile.getIndustry();
        }

        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            return profile.getLocation();
        }

        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }

        return "Employee profile";
    }

    private int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double decimalValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private int percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }

    private String resolveProfileName(Profile profile, String fallback) {
        return profile != null ? buildDisplayName(profile) : fallback;
    }

    private boolean isCompanyUpcomingSession(Session session, ZonedDateTime now) {
        return isUpcomingSession(session, now);
    }

    private ZonedDateTime sessionDisplayTime(Session session) {
        return sessionRecentActivityTime(session, ZoneId.systemDefault());
    }

    private ZonedDateTime sessionActivityTime(Session session, ZoneId zoneId) {
        if (session.getScheduledStart() != null) {
            return session.getScheduledStart();
        }

        if (session.getCreatedAt() != null) {
            return session.getCreatedAt().atZone(zoneId);
        }

        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt().atZone(zoneId);
        }

        return null;
    }

    private ZonedDateTime sessionRecentActivityTime(Session session, ZoneId zoneId) {
        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt().atZone(zoneId);
        }

        if (session.getConfirmedAt() != null) {
            return session.getConfirmedAt().atZone(zoneId);
        }

        if (session.getCancelledAt() != null) {
            return session.getCancelledAt().atZone(zoneId);
        }

        if (session.getScheduledStart() != null) {
            return session.getScheduledStart();
        }

        if (session.getCreatedAt() != null) {
            return session.getCreatedAt().atZone(zoneId);
        }

        return null;
    }

    private ZonedDateTime whitelistEventTime(CompanyEmployeeWhitelist entry, ZoneId zoneId) {
        LocalDateTime timestamp = entry.getUpdatedAt() != null ? entry.getUpdatedAt() : entry.getCreatedAt();
        return timestamp != null ? timestamp.atZone(zoneId) : null;
    }

    private ZonedDateTime toZoned(LocalDateTime timestamp, ZoneId zoneId) {
        return timestamp != null ? timestamp.atZone(zoneId) : null;
    }

    private ZonedDateTime pulseActivityTime(ParticipantPulse pulse, ZoneId zoneId) {
        if (pulse.getCreatedAt() != null) {
            return pulse.getCreatedAt().atZone(zoneId);
        }
        if (pulse.getUpdatedAt() != null) {
            return pulse.getUpdatedAt().atZone(zoneId);
        }
        return null;
    }

    private Map<String, Object> buildEmployeeData(Profile profile, MenteeProfile menteeProfile) {
        Map<String, Object> employee = new LinkedHashMap<>();

        employee.put("name", buildDisplayName(profile));
        employee.put("role", defaultString(profile.getRole(), "Mentee"));
        employee.put("department", defaultString(
                profile.getIndustry() != null ? profile.getIndustry() : menteeProfile != null ? menteeProfile.getIndustry() : null,
                "Professional Development"
        ));
        employee.put("joinDate", profile.getCreatedAt() != null
                ? profile.getCreatedAt().toLocalDate().toString()
                : LocalDate.now().toString());
        employee.put("avatar", defaultString(profile.getAvatarUrl(), ""));

        return employee;
    }

    private List<Map<String, Object>> buildSessionTrends(List<Session> sessions, ZonedDateTime now) {
        List<Map<String, Object>> trends = new ArrayList<>();

        for (int index = 3; index >= 0; index--) {
            YearMonth month = YearMonth.from(now.minusMonths(index));

            List<Session> monthlySessions = sessions.stream()
                    .filter(session -> session.getScheduledStart() != null)
                    .filter(session -> YearMonth.from(session.getScheduledStart()).equals(month))
                    .toList();

            int count = monthlySessions.size();
            double hours = roundOneDecimal(
                    monthlySessions.stream().mapToLong(this::safeDurationMinutes).sum() / 60.0
            );

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", month.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
            entry.put("sessions", count);
            entry.put("hours", hours);
            trends.add(entry);
        }

        return trends;
    }

    private List<Map<String, Object>> buildSkillProgress(List<Session> sessions, ZonedDateTime now, int periodDays) {
        ZonedDateTime currentStart = now.minusDays(periodDays);
        ZonedDateTime previousStart = now.minusDays(periodDays * 2L);

        Map<String, Long> currentCounts = sessions.stream()
                .filter(session -> isWithin(session.getScheduledStart(), currentStart, now))
                .collect(Collectors.groupingBy(this::resolveSkillName, Collectors.counting()));

        Map<String, Long> previousCounts = sessions.stream()
                .filter(session -> isWithin(session.getScheduledStart(), previousStart, currentStart))
                .collect(Collectors.groupingBy(this::resolveSkillName, Collectors.counting()));

        Set<String> allSkills = currentCounts.keySet().stream()
                .filter(skill -> !skill.isBlank())
                .collect(Collectors.toSet());
        allSkills.addAll(previousCounts.keySet().stream().filter(skill -> !skill.isBlank()).toList());

        long maxCount = allSkills.stream()
                .mapToLong(skill -> Math.max(currentCounts.getOrDefault(skill, 0L), previousCounts.getOrDefault(skill, 0L)))
                .max()
                .orElse(0);

        if (maxCount == 0) {
            return List.of();
        }

        return allSkills.stream()
                .map(skill -> {
                    long current = currentCounts.getOrDefault(skill, 0L);
                    long previous = previousCounts.getOrDefault(skill, 0L);

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("skill", skill);
                    entry.put("current", (int) Math.round((current * 100.0) / maxCount));
                    entry.put("previous", (int) Math.round((previous * 100.0) / maxCount));
                    entry.put("category", classifyCategory(skill));
                    return entry;
                })
                .sorted((left, right) -> {
                    Integer leftCurrent = (Integer) left.get("current");
                    Integer rightCurrent = (Integer) right.get("current");
                    return rightCurrent.compareTo(leftCurrent);
                })
                .limit(8)
                .toList();
    }

    private List<Map<String, Object>> buildMentorRatings(
            List<Session> sessions,
            Map<UUID, Profile> mentorsById,
            Map<UUID, MentorProfile> mentorDetailsById
    ) {
        Map<UUID, List<Session>> sessionsByMentor = sessions.stream()
                .filter(session -> session.getMentorId() != null)
                .collect(Collectors.groupingBy(Session::getMentorId));

        return sessionsByMentor.entrySet().stream()
                .map(entry -> {
                    UUID mentorId = entry.getKey();
                    List<Session> mentorSessions = entry.getValue();

                    Profile mentor = mentorsById.get(mentorId);
                    MentorProfile mentorDetails = mentorDetailsById.get(mentorId);

                    double averageRating = mentorSessions.stream()
                            .map(Session::getRating)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .average()
                            .orElseGet(() -> mentorDetails != null && mentorDetails.getRating() != null
                                    ? mentorDetails.getRating().doubleValue()
                                    : 0);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("mentorName", mentor != null ? buildDisplayName(mentor) : "Mentor");
                    response.put("sessions", mentorSessions.size());
                    response.put("averageRating", roundOneDecimal(averageRating));
                    response.put("skills", extractMentorSkills(mentor, mentorDetails));
                    return response;
                })
                .sorted((left, right) -> {
                    Integer leftSessions = (Integer) left.get("sessions");
                    Integer rightSessions = (Integer) right.get("sessions");
                    return rightSessions.compareTo(leftSessions);
                })
                .limit(5)
                .toList();
    }

    private Map<String, Integer> buildTimeDistribution(List<Session> sessions) {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("technical", 0L);
        totals.put("leadership", 0L);
        totals.put("communication", 0L);
        totals.put("other", 0L);

        for (Session session : sessions) {
            String category = classifyCategory(resolveSkillName(session)).toLowerCase(Locale.ROOT);
            String key = switch (category) {
                case "technical" -> "technical";
                case "leadership" -> "leadership";
                case "communication" -> "communication";
                default -> "other";
            };

            totals.put(key, totals.get(key) + Math.max(1, safeDurationMinutes(session)));
        }

        long grandTotal = totals.values().stream().mapToLong(Long::longValue).sum();
        if (grandTotal == 0) {
            return Map.of("technical", 0, "leadership", 0, "communication", 0, "other", 0);
        }

        Map<String, Integer> percentages = new LinkedHashMap<>();
        int runningSum = 0;
        String highestCategory = "technical";
        long highestValue = -1;

        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            int percentage = (int) Math.round((entry.getValue() * 100.0) / grandTotal);
            percentages.put(entry.getKey(), percentage);
            runningSum += percentage;

            if (entry.getValue() > highestValue) {
                highestValue = entry.getValue();
                highestCategory = entry.getKey();
            }
        }

        if (runningSum != 100) {
            percentages.put(highestCategory, percentages.get(highestCategory) + (100 - runningSum));
        }

        return percentages;
    }

    private List<Map<String, Object>> buildCurrentMentors(
            List<Session> sessions,
            List<Session> upcomingSessions,
            Map<UUID, Profile> mentorsById,
            Map<UUID, MentorProfile> mentorDetailsById,
            ZonedDateTime now
    ) {
        Map<UUID, List<Session>> sessionsByMentor = sessions.stream()
                .filter(session -> session.getMentorId() != null)
                .collect(Collectors.groupingBy(Session::getMentorId));

        Map<UUID, ZonedDateTime> nextSessionByMentor = upcomingSessions.stream()
                .filter(session -> session.getMentorId() != null && session.getScheduledStart() != null)
                .collect(Collectors.toMap(
                        Session::getMentorId,
                        Session::getScheduledStart,
                        (left, right) -> left.isBefore(right) ? left : right
                ));

        return sessionsByMentor.entrySet().stream()
                .map(entry -> {
                    UUID mentorId = entry.getKey();
                    List<Session> mentorSessions = entry.getValue();

                    Profile mentor = mentorsById.get(mentorId);
                    MentorProfile mentorDetails = mentorDetailsById.get(mentorId);

                    ZonedDateTime firstSession = mentorSessions.stream()
                            .map(Session::getScheduledStart)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder())
                            .orElse(now);

                    ZonedDateTime fallbackSession = mentorSessions.stream()
                            .map(Session::getScheduledStart)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(now);

                    ZonedDateTime nextSession = Optional.ofNullable(nextSessionByMentor.get(mentorId)).orElse(fallbackSession);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", mentorId.toString());
                    response.put("name", mentor != null ? buildDisplayName(mentor) : "Mentor");
                    response.put("role", mentorDetails != null ? defaultString(mentorDetails.getTitle(), "Mentor") : "Mentor");
                    response.put("company", mentorDetails != null ? defaultString(mentorDetails.getCompany(), "Prosper Mentor") : "Prosper Mentor");
                    response.put("avatar", mentor != null ? defaultString(mentor.getAvatarUrl(), "") : "");
                    response.put("expertise", extractMentorSkills(mentor, mentorDetails));
                    response.put("nextSession", nextSession.toString());
                    response.put("relationshipDuration", formatRelationshipDuration(firstSession, now));
                    response.put("sessionCount", mentorSessions.size());
                    return response;
                })
                .sorted((left, right) -> {
                    Integer leftCount = (Integer) left.get("sessionCount");
                    Integer rightCount = (Integer) right.get("sessionCount");
                    return rightCount.compareTo(leftCount);
                })
                .limit(5)
                .peek(map -> map.remove("sessionCount"))
                .toList();
    }

    private List<Map<String, Object>> buildUpcomingSessions(List<Session> upcomingSessions, Map<UUID, Profile> mentorsById) {
        return upcomingSessions.stream()
                .limit(8)
                .map(session -> {
                    Profile mentor = mentorsById.get(session.getMentorId());

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", session.getId().toString());
                    response.put("mentorName", mentor != null ? buildDisplayName(mentor) : "Mentor");
                    response.put("title", defaultString(session.getTitle(), "Mentorship Session"));
                    response.put("date", session.getScheduledStart() != null ? session.getScheduledStart().toString() : ZonedDateTime.now().toString());
                    response.put("duration", safeDurationMinutes(session));
                    response.put("type", resolveSessionType(session));
                    response.put("status", session.getStatus() != null
                            ? session.getStatus().name().toLowerCase(Locale.ROOT)
                            : "pending");
                    return response;
                })
                .toList();
    }

    private List<Map<String, Object>> buildCurrentGoals(MenteeProfile menteeProfile, List<Session> sessions, ZonedDateTime now) {
        List<String> goals = menteeProfile != null && menteeProfile.getGoals() != null
                ? menteeProfile.getGoals()
                : List.of();

        List<Map<String, Object>> response = new ArrayList<>();

        for (int index = 0; index < goals.size(); index++) {
            String goal = goals.get(index);
            if (goal == null || goal.isBlank()) {
                continue;
            }

            long relatedSessionCount = sessions.stream()
                    .filter(session -> matchesGoal(goal, session))
                    .count();

            int progress = (int) Math.min(100, relatedSessionCount * 25);
            String status = progress >= 100 ? "Completed" : progress > 0 ? "In Progress" : "Planned";
            String priority = index == 0 ? "High" : index < 3 ? "Medium" : "Low";

            Map<String, Object> goalResponse = new LinkedHashMap<>();
            goalResponse.put("id", "goal-" + String.format("%03d", index + 1));
            goalResponse.put("title", goal);
            goalResponse.put("description", "Progress is inferred from your completed and scheduled mentorship sessions.");
            goalResponse.put("progress", progress);
            goalResponse.put("targetDate", now.plusDays(45L + (long) index * 14).toLocalDate().toString());
            goalResponse.put("category", classifyGoalCategory(goal));
            goalResponse.put("priority", priority);
            goalResponse.put("status", status);

            response.add(goalResponse);
        }

        return response;
    }

    private Map<String, Integer> buildGoalCompletion(List<Map<String, Object>> goals, ZonedDateTime now) {
        int completed = 0;
        int inProgress = 0;
        int planned = 0;
        int overdue = 0;

        for (Map<String, Object> goal : goals) {
            int progress = (Integer) goal.getOrDefault("progress", 0);
            String targetDate = (String) goal.getOrDefault("targetDate", now.toLocalDate().toString());
            LocalDate dueDate = LocalDate.parse(targetDate);

            if (progress >= 100) {
                completed++;
            } else if (progress > 0) {
                inProgress++;
            } else {
                planned++;
            }

            if (dueDate.isBefore(now.toLocalDate()) && progress < 100) {
                overdue++;
            }
        }

        Map<String, Integer> completion = new LinkedHashMap<>();
        completion.put("completed", completed);
        completion.put("inProgress", inProgress);
        completion.put("planned", planned);
        completion.put("overdue", overdue);
        return completion;
    }

    private List<Map<String, Object>> buildRecentActivity(List<Session> sessions, Map<UUID, Profile> mentorsById) {
        return sessions.stream()
                .sorted(Comparator.comparing(this::activitySortTime, Comparator.reverseOrder()))
                .limit(6)
                .map(session -> {
                    Profile mentor = mentorsById.get(session.getMentorId());
                    String mentorName = mentor != null ? buildDisplayName(mentor) : "your mentor";

                    String type;
                    String title;
                    String description;

                    if (session.getStatus() == Session.SessionStatus.COMPLETED) {
                        type = "session_completed";
                        title = "Completed session with " + mentorName;
                        description = defaultString(session.getTitle(), "Mentorship session") + " has been completed.";
                    } else if (session.getStatus() == Session.SessionStatus.CONFIRMED || session.getStatus() == Session.SessionStatus.SCHEDULED) {
                        type = "session_scheduled";
                        title = "Session confirmed with " + mentorName;
                        description = "Upcoming: " + defaultString(session.getTitle(), "Mentorship session");
                    } else if (session.getStatus() == Session.SessionStatus.CANCELLED) {
                        type = "session_cancelled";
                        title = "Session cancelled";
                        description = defaultString(session.getCancellationReason(), "A mentorship session was cancelled.");
                    } else if (session.getStatus() == Session.SessionStatus.IN_PROGRESS) {
                        type = "session_in_progress";
                        title = "Session in progress";
                        description = defaultString(session.getTitle(), "Mentorship session") + " is currently underway.";
                    } else {
                        type = "session_requested";
                        title = "Session requested";
                        description = "You requested " + defaultString(session.getTitle(), "a mentorship session") + ".";
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", "activity-" + session.getId());
                    response.put("type", type);
                    response.put("title", title);
                    response.put("description", description);
                    String canonicalTimestamp = formatActivityTimestamp(activitySortTime(session));
                    response.put("timestamp", canonicalTimestamp);
                    response.put("createdAt", canonicalTimestamp);
                    return response;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRecommendations(
            long activeMentors,
            int upcomingSessionCount,
            int goalsCount,
            double averageRating,
            int totalSessions
    ) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        if (activeMentors < 2) {
            recommendations.add(createRecommendation(
                    "mentor",
                    "Connect with another mentor",
                    "Diversifying mentorship relationships usually improves learning velocity and career exposure.",
                    "Find Mentors",
                    "/app/mentors",
                    "High"
            ));
        }

        if (upcomingSessionCount == 0) {
            recommendations.add(createRecommendation(
                    "session",
                    "Book your next session",
                    "You currently have no upcoming sessions. Scheduling the next one keeps momentum high.",
                    "Schedule Session",
                    "/app/mentors/sessions",
                    "High"
            ));
        }

        if (goalsCount == 0) {
            recommendations.add(createRecommendation(
                    "goal",
                    "Add professional goals",
                    "Set measurable goals so the platform can track your progress more accurately.",
                    "Set Goals",
                    "/app/development/goals",
                    "Medium"
            ));
        }

        if (totalSessions >= 3 && averageRating > 0 && averageRating < 4.0) {
            recommendations.add(createRecommendation(
                    "quality",
                    "Review session outcomes",
                    "Your average rating suggests room to improve session outcomes. Share clearer goals with mentors before each session.",
                    "View Sessions",
                    "/app/mentors/sessions",
                    "Medium"
            ));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(createRecommendation(
                    "growth",
                    "Keep the momentum",
                    "Your dashboard is healthy. Continue regular sessions and update goals to sustain growth.",
                    "Open Sessions",
                    "/app/mentors/sessions",
                    "Low"
            ));
        }

        for (int index = 0; index < recommendations.size(); index++) {
            recommendations.get(index).put("id", "rec-" + String.format("%03d", index + 1));
        }

        return recommendations;
    }

    private Map<String, Object> createRecommendation(
            String type,
            String title,
            String description,
            String action,
            String actionUrl,
            String priority
    ) {
        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("type", type);
        recommendation.put("title", title);
        recommendation.put("description", description);
        recommendation.put("action", action);
        recommendation.put("actionUrl", actionUrl);
        recommendation.put("priority", priority);
        return recommendation;
    }

    private ZonedDateTime activitySortTime(Session session) {
        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt().atZone(ZoneId.systemDefault());
        }
        if (session.getCreatedAt() != null) {
            return session.getCreatedAt().atZone(ZoneId.systemDefault());
        }
        if (session.getScheduledStart() != null) {
            return session.getScheduledStart();
        }
        return ZonedDateTime.now();
    }

    private String formatActivityTimestamp(ZonedDateTime timestamp) {
        ZonedDateTime normalized = (timestamp != null ? timestamp : ZonedDateTime.now(ZoneId.systemDefault()))
                .withZoneSameInstant(ZoneOffset.UTC);
        return normalized.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String resolveSkillName(Session session) {
        if (session.getSkill() != null && session.getSkill().getName() != null) {
            return session.getSkill().getName();
        }
        return defaultString(session.getTitle(), "Mentorship");
    }

    private boolean isUpcomingSession(Session session, ZonedDateTime now) {
        return session.getScheduledStart() != null
                && session.getScheduledStart().isAfter(now)
                && session.getStatus() != null
                && UPCOMING_STATUSES.contains(session.getStatus());
    }

    private boolean isWithin(ZonedDateTime value, ZonedDateTime startInclusive, ZonedDateTime endExclusive) {
        return value != null && !value.isBefore(startInclusive) && value.isBefore(endExclusive);
    }

    private long countSessionsWithinDays(List<Session> sessions, ZonedDateTime now, int fromDaysAgo, int toDaysAgo) {
        ZonedDateTime start = now.minusDays(fromDaysAgo);
        ZonedDateTime end = now.minusDays(toDaysAgo);

        return sessions.stream()
                .map(Session::getScheduledStart)
                .filter(date -> isWithin(date, start, end))
                .count();
    }

    private long sumMinutesWithinDays(List<Session> sessions, ZonedDateTime now, int fromDaysAgo, int toDaysAgo) {
        ZonedDateTime start = now.minusDays(fromDaysAgo);
        ZonedDateTime end = now.minusDays(toDaysAgo);

        return sessions.stream()
                .filter(session -> isWithin(session.getScheduledStart(), start, end))
                .mapToLong(this::safeDurationMinutes)
                .sum();
    }

    private double averageRatingWithinDays(List<Session> sessions, ZonedDateTime now, int fromDaysAgo, int toDaysAgo) {
        ZonedDateTime start = now.minusDays(fromDaysAgo);
        ZonedDateTime end = now.minusDays(toDaysAgo);

        return sessions.stream()
                .filter(session -> isWithin(session.getScheduledStart(), start, end))
                .map(Session::getRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private int calculatePercentGrowth(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100 : 0;
        }
        return (int) Math.round(((current - previous) * 100.0) / previous);
    }

    private long safeDurationMinutes(Session session) {
        if (session.getScheduledStart() == null || session.getScheduledEnd() == null) {
            return 0;
        }
        return Math.max(0, session.getDurationMinutes());
    }

    private List<String> extractMentorSkills(Profile mentor, MentorProfile mentorDetails) {
        List<String> skills = new ArrayList<>();

        if (mentor != null && mentor.getExpertise() != null) {
            skills.addAll(mentor.getExpertise().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList());
        }

        if (skills.isEmpty() && mentorDetails != null && mentorDetails.getSpecializations() != null) {
            skills.addAll(mentorDetails.getSpecializations().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList());
        }

        if (skills.isEmpty()) {
            skills.add("Mentorship");
        }

        return skills.stream().distinct().limit(3).toList();
    }

    private String buildDisplayName(Profile profile) {
        String firstName = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName().trim() : "";

        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }

        return profile.getEmail() != null ? profile.getEmail() : "User";
    }

    private String classifyCategory(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);

        if (containsAny(value, "react", "java", "spring", "backend", "frontend", "software", "engineering", "code", "typescript", "python", "system")) {
            return "Technical";
        }
        if (containsAny(value, "leader", "management", "strategy", "team", "product", "ownership")) {
            return "Leadership";
        }
        if (containsAny(value, "communication", "speaking", "presentation", "feedback", "stakeholder", "writing")) {
            return "Communication";
        }
        return "Other";
    }

    private String classifyGoalCategory(String goal) {
        String value = goal == null ? "" : goal.toLowerCase(Locale.ROOT);

        if (containsAny(value, "react", "java", "technical", "coding", "engineering", "architecture", "system", "software")) {
            return "Technical Skills";
        }
        if (containsAny(value, "leader", "management", "team", "strategy", "ownership")) {
            return "Leadership";
        }
        if (containsAny(value, "communication", "speaking", "presentation", "influence", "feedback")) {
            return "Communication";
        }
        return "Professional Development";
    }

    private boolean matchesGoal(String goal, Session session) {
        String normalizedGoal = goal.toLowerCase(Locale.ROOT);

        String title = defaultString(session.getTitle(), "").toLowerCase(Locale.ROOT);
        String description = defaultString(session.getDescription(), "").toLowerCase(Locale.ROOT);
        String skill = resolveSkillName(session).toLowerCase(Locale.ROOT);

        return title.contains(normalizedGoal)
                || description.contains(normalizedGoal)
                || skill.contains(normalizedGoal)
                || normalizedGoal.split(" ").length > 1 && normalizedGoal.split(" ")[0].length() > 3
                && (title.contains(normalizedGoal.split(" ")[0]) || skill.contains(normalizedGoal.split(" ")[0]));
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String resolveSessionType(Session session) {
        if (session.getMeetingPlatform() == null) {
            return "Mentorship Session";
        }

        return switch (session.getMeetingPlatform()) {
            case GOOGLE_MEET, ZOOM -> "Video Call";
        };
    }

    private int resolvePeriodDays(String period) {
        if (period == null) {
            return DEFAULT_COMPANY_DASHBOARD_DAYS;
        }

        return switch (period.toLowerCase(Locale.ROOT)) {
            case "last_7_days" -> 7;
            case "last_90_days" -> 90;
            case "last_year" -> 365;
            case "last_30_days" -> DEFAULT_COMPANY_DASHBOARD_DAYS;
            default -> DEFAULT_COMPANY_DASHBOARD_DAYS;
        };
    }

    private DashboardDateRange resolveCompanyDashboardDateRange(String period,
                                                                LocalDate startDate,
                                                                LocalDate endDate,
                                                                ZoneId zoneId,
                                                                ZonedDateTime now) {
        int fallbackDays = Math.max(1, resolvePeriodDays(period));
        LocalDate today = now.toLocalDate();
        LocalDate resolvedEndDate = endDate != null
                ? endDate
                : (startDate != null ? startDate.plusDays(fallbackDays - 1L) : today);
        LocalDate resolvedStartDate = startDate != null
                ? startDate
                : resolvedEndDate.minusDays(fallbackDays - 1L);

        if (resolvedEndDate.isBefore(resolvedStartDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        ZonedDateTime currentStart = resolvedStartDate.atStartOfDay(zoneId);
        ZonedDateTime currentEnd = resolvedEndDate.plusDays(1).atStartOfDay(zoneId);
        int periodDays = (int) Math.max(1L, ChronoUnit.DAYS.between(resolvedStartDate, resolvedEndDate.plusDays(1)));
        ZonedDateTime previousStart = currentStart.minusDays(periodDays);
        String periodLabel = (startDate != null || endDate != null)
                ? "custom"
                : defaultString(period, "last_30_days");

        return new DashboardDateRange(
                resolvedStartDate,
                resolvedEndDate,
                currentStart,
                currentEnd,
                previousStart,
                periodDays,
                periodLabel
        );
    }

    private String formatRelationshipDuration(ZonedDateTime firstSession, ZonedDateTime now) {
        if (firstSession == null) {
            return "New";
        }

        long months = ChronoUnit.MONTHS.between(
                firstSession.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate(),
                now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
        );

        if (months > 0) {
            return months + " month" + (months > 1 ? "s" : "");
        }

        long days = Math.max(1, ChronoUnit.DAYS.between(firstSession.toLocalDate(), now.toLocalDate()));
        return days + " day" + (days > 1 ? "s" : "");
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String defaultString(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private record DashboardDateRange(
            LocalDate startDate,
            LocalDate endDate,
            ZonedDateTime currentStart,
            ZonedDateTime currentEnd,
            ZonedDateTime previousStart,
            int periodDays,
            String periodLabel
    ) {
    }
}
