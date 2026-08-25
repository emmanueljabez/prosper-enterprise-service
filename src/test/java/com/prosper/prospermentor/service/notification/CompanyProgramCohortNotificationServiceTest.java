package com.prosper.prospermentor.service.notification;

import com.prosper.prospermentor.EmailInterface;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyProgramCohortNotificationServiceTest {

    @Test
    void cohortParticipantAddedTemplate_shouldRenderProgramBriefAndEmployeeCta() {
        Context context = baseContext();
        context.setVariable("actionUrl", "https://enterprise.prospermentor.com/app/employee/programs");

        String html = templateEngine().process("email/cohort-participant-added", context);

        assertThat(html)
                .contains("prosper-logo.png")
                .contains("COHORT INVITE")
                .contains("Amina, you have been added to<br>G4G Nairobi - Q3 2026.")
                .contains("Kenya Airways added you to a ProsperMentor cohort.")
                .contains("Build a circle-based mentorship journey for emerging leaders.")
                .contains("View my program")
                .contains("https://enterprise.prospermentor.com/app/employee/programs")
                .contains("Support")
                .contains("Privacy Policy");
    }

    @Test
    void cohortParticipantConfirmedTemplate_shouldRenderNextStepCopy() {
        Context context = baseContext();
        context.setVariable("actionUrl", "https://enterprise.prospermentor.com/app/employee/programs");

        String html = templateEngine().process("email/cohort-participant-confirmed", context);

        assertThat(html)
                .contains("COHORT CONFIRMED")
                .contains("Amina, your place is confirmed for<br>G4G Nairobi - Q3 2026.")
                .contains("Next, attend the cohort plenary and get ready for circle placement.")
                .contains("Open my program");
    }

    @Test
    void circleAssignedTemplate_shouldRenderCircleAndProgramContext() {
        Context context = baseContext();
        context.setVariable("circle", membership().getCircle());
        context.setVariable("actionUrl", "https://enterprise.prospermentor.com/app/employee/programs");

        String html = templateEngine().process("email/circle-assigned", context);

        assertThat(html)
                .contains("CIRCLE ASSIGNED")
                .contains("Amina, you are now in<br>STEM Risers.")
                .contains("Your circle is part of G4G Nairobi - Q3 2026 under G4G Mentorship.")
                .contains("Open my circle");
    }

    @Test
    void sendCohortParticipantAdded_shouldSendEmailAndWhatsApp() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
        NautixWhatsAppService nautixWhatsAppService = mock(NautixWhatsAppService.class);
        CompanyProgramCohortNotificationService service = new CompanyProgramCohortNotificationService(
                emailInterface,
                templateEngine,
                nautixWhatsAppService
        );
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(service, "cohortAddedWhatsappTemplateName", "prosper_cohort_participant_added_v2");
        when(templateEngine.process(eq("email/cohort-participant-added"), any(Context.class)))
                .thenReturn("<html>Cohort added</html>");

        CompanyProgramCohortNotificationService.DeliveryAttemptResult result =
                service.sendCohortParticipantAdded(participant());

        assertThat(result.emailSent()).isTrue();
        assertThat(result.whatsappSent()).isTrue();
        verify(emailInterface).sendEmail(
                eq("amina@example.com"),
                eq("You're added to G4G Nairobi - Q3 2026 on ProsperMentor"),
                eq("<html>Cohort added</html>"),
                eq(List.of())
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_cohort_participant_added_v2"),
                eq("+254720482575"),
                eq(List.of(
                        "Amina",
                        "Kenya Airways",
                        "G4G Mentorship",
                        "G4G Nairobi - Q3 2026",
                        "Build a circle-based mentorship journey for emerging leaders.",
                        "https://enterprise.prospermentor.com/app/employee/programs"
                ))
        );
    }

    @Test
    void sendCohortParticipantConfirmed_shouldUseConfirmedTemplateAndProgramLink() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
        NautixWhatsAppService nautixWhatsAppService = mock(NautixWhatsAppService.class);
        CompanyProgramCohortNotificationService service = new CompanyProgramCohortNotificationService(
                emailInterface,
                templateEngine,
                nautixWhatsAppService
        );
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(service, "cohortConfirmedWhatsappTemplateName", "prosper_cohort_participant_confirmed_v2");
        when(templateEngine.process(eq("email/cohort-participant-confirmed"), any(Context.class)))
                .thenReturn("<html>Cohort confirmed</html>");

        service.sendCohortParticipantConfirmed(participant());

        verify(emailInterface).sendEmail(
                eq("amina@example.com"),
                eq("You're confirmed for G4G Nairobi - Q3 2026 on ProsperMentor"),
                eq("<html>Cohort confirmed</html>"),
                eq(List.of())
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_cohort_participant_confirmed_v2"),
                eq("+254720482575"),
                eq(List.of(
                        "Amina",
                        "Kenya Airways",
                        "G4G Mentorship",
                        "G4G Nairobi - Q3 2026",
                        "Build a circle-based mentorship journey for emerging leaders.",
                        "https://enterprise.prospermentor.com/app/employee/programs"
                ))
        );
    }

    @Test
    void sendCircleAssigned_shouldSendCircleTemplateAndJourneyLink() {
        EmailInterface emailInterface = mock(EmailInterface.class);
        SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
        NautixWhatsAppService nautixWhatsAppService = mock(NautixWhatsAppService.class);
        CompanyProgramCohortNotificationService service = new CompanyProgramCohortNotificationService(
                emailInterface,
                templateEngine,
                nautixWhatsAppService
        );
        ReflectionTestUtils.setField(service, "appName", "ProsperMentor");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://enterprise.prospermentor.com");
        ReflectionTestUtils.setField(service, "circleAssignedWhatsappTemplateName", "prosper_circle_assigned_v2");
        when(templateEngine.process(eq("email/circle-assigned"), any(Context.class)))
                .thenReturn("<html>Circle assigned</html>");

        service.sendCircleAssigned(membership());

        verify(emailInterface).sendEmail(
                eq("amina@example.com"),
                eq("You're in STEM Risers for G4G Nairobi - Q3 2026"),
                eq("<html>Circle assigned</html>"),
                eq(List.of())
        );
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_circle_assigned_v2"),
                eq("+254720482575"),
                eq(List.of(
                        "Amina",
                        "STEM Risers",
                        "G4G Nairobi - Q3 2026",
                        "G4G Mentorship",
                        "Build a circle-based mentorship journey for emerging leaders.",
                        "https://enterprise.prospermentor.com/app/employee/programs"
                ))
        );
    }

    private Context baseContext() {
        CompanyProgramCohortParticipant participant = participant();
        Context context = new Context();
        context.setVariable("appName", "ProsperMentor");
        context.setVariable("participantName", "Amina");
        context.setVariable("company", participant.getCohort().getCompanyProgram().getCompany());
        context.setVariable("companyProgram", participant.getCohort().getCompanyProgram());
        context.setVariable("cohort", participant.getCohort());
        return context;
    }

    private CommonInterestCircleMembership membership() {
        CommonInterestCircle circle = new CommonInterestCircle();
        circle.setName("STEM Risers");
        circle.setTheme("STEM");
        circle.setNextSessionAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        circle.setCohort(participant().getCohort());

        CommonInterestCircleMembership membership = new CommonInterestCircleMembership();
        membership.setCircle(circle);
        membership.setCohortParticipant(participant());
        return membership;
    }

    private CompanyProgramCohortParticipant participant() {
        Company company = new Company();
        company.setName("Kenya Airways");

        CompanyProgram program = new CompanyProgram();
        program.setName("G4G Mentorship");
        program.setObjective("Build a circle-based mentorship journey for emerging leaders.");
        program.setCompany(company);

        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setName("G4G Nairobi - Q3 2026");
        cohort.setCode("G4G-NBO-Q3-2026");
        cohort.setChapter("Nairobi");
        cohort.setRegion("Kenya");
        cohort.setCompanyProgram(program);

        Profile profile = new Profile();
        profile.setFirstName("Amina");
        profile.setLastName("Otieno");
        profile.setEmail("amina@example.com");
        profile.setPhone("+254720482575");

        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setCohort(cohort);
        participant.setProfile(profile);
        participant.setInterestTags(List.of("STEM", "Career readiness"));
        return participant;
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
