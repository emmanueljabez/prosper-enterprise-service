package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyEmployeeWhitelist;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyEmployeeWhitelistRepository companyEmployeeWhitelistRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private MenteeProfileRepository menteeProfileRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ParticipantPulseRepository participantPulseRepository;

    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;

    @Mock
    private CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;

    @Mock
    private CompanySessionWalletRepository companySessionWalletRepository;

    @Mock
    private CompanySessionWalletTransactionRepository companySessionWalletTransactionRepository;

    @Mock
    private EmployeeSessionAllocationRepository employeeSessionAllocationRepository;

    @Mock
    private ReviewAlertRepository reviewAlertRepository;

    @Mock
    private AccessAuditLogRepository accessAuditLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void shouldBuildCompanyDashboardFromProfilesWhitelistAndSessions() {
        UUID companyId = UUID.randomUUID();
        UUID employeeOneId = UUID.randomUUID();
        UUID employeeTwoId = UUID.randomUUID();
        UUID companyAdminId = UUID.randomUUID();
        UUID mentorOneId = UUID.randomUUID();
        UUID mentorTwoId = UUID.randomUUID();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        Company company = new Company();
        company.setId(companyId);
        company.setName("Acme Corp");

        Profile employeeOne = profile(employeeOneId, company, "mentee", "Alice", "Ngugi", "alice@acme.test", true, now.minusDays(5));
        Profile employeeTwo = profile(employeeTwoId, company, "mentee", "Brian", "Otieno", "brian@acme.test", false, now.minusDays(45));
        Profile companyAdmin = profile(companyAdminId, company, "corporate_admin", "Casey", "Admin", "casey@acme.test", true, now.minusDays(2));

        Profile mentorOne = profile(mentorOneId, null, "mentor", "Mara", "Kim", "mara@test.com", true, now.minusDays(200));
        Profile mentorTwo = profile(mentorTwoId, null, "mentor", "Noah", "Shah", "noah@test.com", true, now.minusDays(150));

        CompanyEmployeeWhitelist unsentInvite = whitelist(company, "newhire@acme.test", false, false, now.minusDays(10).toLocalDateTime(), null);
        CompanyEmployeeWhitelist sentInvite = whitelist(company, "invited@acme.test", true, false, now.minusDays(20).toLocalDateTime(), now.minusDays(3).toLocalDateTime());
        CompanyEmployeeWhitelist acceptedInvite = whitelist(company, "accepted@acme.test", true, true, now.minusDays(25).toLocalDateTime(), now.minusDays(1).toLocalDateTime());
        acceptedInvite.setProfile(employeeOne);

        Session completedRecent = session(
                employeeOneId,
                mentorOneId,
                now.minusDays(3),
                60,
                Session.SessionStatus.COMPLETED,
                5,
                "Great session",
                BigDecimal.ZERO,
                true
        );

        Session upcomingConfirmed = session(
                employeeOneId,
                mentorOneId,
                now.plusDays(2),
                60,
                Session.SessionStatus.CONFIRMED,
                null,
                null,
                BigDecimal.ZERO,
                false
        );

        Session pendingRecent = session(
                employeeTwoId,
                mentorTwoId,
                now.minusDays(20),
                90,
                Session.SessionStatus.PENDING,
                null,
                null,
                BigDecimal.valueOf(100),
                false
        );

        Session completedPrevious = session(
                employeeTwoId,
                mentorTwoId,
                now.minusDays(40),
                60,
                Session.SessionStatus.COMPLETED,
                4,
                "Helpful",
                BigDecimal.ZERO,
                false
        );

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(profileRepository.findByCompanyId(companyId)).thenReturn(List.of(employeeOne, employeeTwo, companyAdmin));
        when(companyEmployeeWhitelistRepository.findByCompanyId(companyId))
                .thenReturn(List.of(unsentInvite, sentInvite, acceptedInvite));
        when(sessionRepository.findByMenteeIdIn(any())).thenReturn(List.of(
                completedRecent,
                upcomingConfirmed,
                pendingRecent,
                completedPrevious
        ));
        when(profileRepository.findAllById(any())).thenReturn(List.of(mentorOne, mentorTwo));
        when(participantPulseRepository.findByParticipant_CompanyProgram_Company_IdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of());
        when(companyProgramParticipantRepository.findByCompanyProgram_Company_Id(companyId)).thenReturn(List.of());
        when(companyProgramMentorAssignmentRepository.findByParticipant_CompanyProgram_Company_Id(companyId)).thenReturn(List.of());
        when(companySessionWalletRepository.findByCompany_Id(companyId)).thenReturn(Optional.empty());
        when(employeeSessionAllocationRepository.findByCompany_Id(companyId)).thenReturn(List.of());
        when(reviewAlertRepository.findCompanyAlertsForSummary(any(), any(), any(), any())).thenReturn(List.of());
        when(accessAuditLogRepository.countByCompanyActionInRange(any(), any(), any(), any())).thenReturn(0L);

        Map<String, Object> dashboard = dashboardService.buildCompanyDashboard(companyId, "last_30_days");

        Map<String, Object> companyData = castMap(dashboard.get("company"));
        Map<String, Object> loadSummary = castMap(dashboard.get("loadSummary"));
        Map<String, Object> stats = castMap(dashboard.get("stats"));
        List<Map<String, Object>> employeeAggregates = castList(dashboard.get("employeeAggregates"));
        List<Map<String, Object>> recentRegistrations = castList(dashboard.get("recentRegistrations"));
        List<Map<String, Object>> topTopics = castList(dashboard.get("topTopics"));
        List<Map<String, Object>> mentorLeaderboard = castList(dashboard.get("mentorLeaderboard"));
        List<Map<String, Object>> statusBreakdown = castList(dashboard.get("statusBreakdown"));
        List<Map<String, Object>> recentActivity = castList(dashboard.get("recentActivity"));

        assertThat(companyData.get("name")).isEqualTo("Acme Corp");
        assertThat(loadSummary.get("requestedEmployees")).isEqualTo(2);
        assertThat(loadSummary.get("loadedEmployees")).isEqualTo(2);
        assertThat(loadSummary.get("failedEmployees")).isEqualTo(0);

        assertThat(stats.get("registeredEmployees")).isEqualTo(2);
        assertThat(stats.get("newEmployeesLastPeriod")).isEqualTo(1L);
        assertThat(stats.get("participatingEmployees")).isEqualTo(2L);
        assertThat(stats.get("participationRate")).isEqualTo(100);
        assertThat(stats.get("sessionsCurrentPeriod")).isEqualTo(2);
        assertThat(stats.get("sessionsPreviousPeriod")).isEqualTo(1);
        assertThat(stats.get("hoursCurrentPeriod")).isEqualTo(2.5);
        assertThat(stats.get("hoursPreviousPeriod")).isEqualTo(1.0);
        assertThat(stats.get("totalHours")).isEqualTo(2.5);
        assertThat(stats.get("averageRating")).isEqualTo(5.0);
        assertThat(stats.get("completionRate")).isEqualTo(50);
        assertThat(stats.get("feedbackCoverage")).isEqualTo(100);
        assertThat(stats.get("completedSessions")).isEqualTo(1);
        assertThat(stats.get("upcomingSessions")).isEqualTo(0);
        assertThat(stats.get("pendingSessions")).isEqualTo(1);
        assertThat(stats.get("cancelledSessions")).isEqualTo(0);
        assertThat(stats.get("paidSessionsCount")).isEqualTo(1);
        assertThat(stats.get("unpaidSessionsCount")).isEqualTo(1);
        assertThat(stats.get("verifiedEmployees")).isEqualTo(1);
        assertThat(stats.get("verificationRate")).isEqualTo(50);
        assertThat(stats.get("whitelistTotal")).isEqualTo(3);
        assertThat(stats.get("invitesSentCount")).isEqualTo(2);
        assertThat(stats.get("acceptedInvitesCount")).isEqualTo(1);
        assertThat(stats.get("pendingInvitationSendCount")).isEqualTo(1);
        assertThat(stats.get("awaitingAcceptanceCount")).isEqualTo(1);
        assertThat(stats.get("invitationAcceptanceRate")).isEqualTo(50);

        assertThat(employeeAggregates).hasSize(2);
        assertThat(employeeAggregates.get(0).get("name")).isEqualTo("Alice Ngugi");
        assertThat(employeeAggregates.get(0).get("sessions")).isEqualTo(1);
        assertThat(employeeAggregates.get(0).get("completed")).isEqualTo(1);
        assertThat(employeeAggregates.get(0).get("upcoming")).isEqualTo(0);
        assertThat(employeeAggregates.get(0).get("hours")).isEqualTo(1.0);
        assertThat(employeeAggregates.get(0).get("averageRating")).isEqualTo(5.0);

        assertThat(recentRegistrations).hasSize(1);
        assertThat(recentRegistrations.get(0).get("id")).isEqualTo(employeeOneId.toString());

        assertThat(topTopics).isNotEmpty();
        assertThat(mentorLeaderboard).hasSize(2);
        assertThat(statusBreakdown).hasSize(4);
        assertThat(statusBreakdown.get(0).get("count")).isEqualTo(1);
        assertThat(recentActivity).isNotEmpty();
        assertThat(recentActivity)
                .allSatisfy(activity -> {
                    Object timestampValue = activity.get("timestamp");
                    Object createdAtValue = activity.get("createdAt");

                    assertThat(timestampValue).isInstanceOf(String.class);
                    assertThat(createdAtValue).isInstanceOf(String.class);
                    assertThat(timestampValue).isEqualTo(createdAtValue);
                    assertThatCode(() -> OffsetDateTime.parse((String) timestampValue))
                            .doesNotThrowAnyException();
                });
    }

    @Test
    void shouldBuildCompanyDashboardWhenCompanyHasNoEmployeeProfilesYet() {
        UUID companyId = UUID.randomUUID();
        UUID companyAdminId = UUID.randomUUID();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        Company company = new Company();
        company.setId(companyId);
        company.setName("Acme Corp");

        Profile companyAdmin = profile(companyAdminId, company, "corporate_admin", "Casey", "Admin", "casey@acme.test", true, now.minusDays(2));

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(profileRepository.findByCompanyId(companyId)).thenReturn(List.of(companyAdmin));
        when(companyEmployeeWhitelistRepository.findByCompanyId(companyId)).thenReturn(List.of());
        when(participantPulseRepository.findByParticipant_CompanyProgram_Company_IdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of());
        when(companyProgramParticipantRepository.findByCompanyProgram_Company_Id(companyId)).thenReturn(List.of());
        when(companyProgramMentorAssignmentRepository.findByParticipant_CompanyProgram_Company_Id(companyId)).thenReturn(List.of());
        when(companySessionWalletRepository.findByCompany_Id(companyId)).thenReturn(Optional.empty());
        when(employeeSessionAllocationRepository.findByCompany_Id(companyId)).thenReturn(List.of());
        when(reviewAlertRepository.findCompanyAlertsForSummary(any(), any(), any(), any())).thenReturn(List.of());
        when(accessAuditLogRepository.countByCompanyActionInRange(any(), any(), any(), any())).thenReturn(0L);

        Map<String, Object> dashboard = dashboardService.buildCompanyDashboard(companyId, "last_30_days");

        Map<String, Object> stats = castMap(dashboard.get("stats"));
        List<Map<String, Object>> employeeAggregates = castList(dashboard.get("employeeAggregates"));

        assertThat(stats.get("registeredEmployees")).isEqualTo(0);
        assertThat(stats.get("totalSessions")).isEqualTo(0);
        assertThat(employeeAggregates).isEmpty();
    }

    private Profile profile(
            UUID id,
            Company company,
            String role,
            String firstName,
            String lastName,
            String email,
            boolean verified,
            ZonedDateTime createdAt
    ) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setCompany(company);
        profile.setRole(role);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setEmail(email);
        profile.setUsername(email);
        profile.setIsVerified(verified);
        profile.setCreatedAt(createdAt);
        profile.setIndustry("Engineering");
        return profile;
    }

    private CompanyEmployeeWhitelist whitelist(
            Company company,
            String email,
            boolean invitationSent,
            boolean invitationAccepted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        CompanyEmployeeWhitelist whitelist = new CompanyEmployeeWhitelist();
        whitelist.setId(UUID.randomUUID());
        whitelist.setCompany(company);
        whitelist.setEmail(email);
        whitelist.setInvitationSent(invitationSent);
        whitelist.setInvitationAccepted(invitationAccepted);
        whitelist.setCreatedAt(createdAt);
        whitelist.setUpdatedAt(updatedAt);
        return whitelist;
    }

    private Session session(
            UUID menteeId,
            UUID mentorId,
            ZonedDateTime start,
            int durationMinutes,
            Session.SessionStatus status,
            Integer rating,
            String feedback,
            BigDecimal price,
            boolean paid
    ) {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setMenteeId(menteeId);
        session.setMentorId(mentorId);
        session.setSkillId(UUID.randomUUID());
        session.setTitle("Leadership Coaching");
        session.setScheduledStart(start);
        session.setScheduledEnd(start.plusMinutes(durationMinutes));
        session.setStatus(status);
        session.setRating(rating);
        session.setFeedback(feedback);
        session.setPrice(price);
        session.setPaid(paid);
        session.setCreatedAt(start.minusDays(2).toLocalDateTime());
        session.setUpdatedAt(start.minusDays(1).toLocalDateTime());
        return session;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
