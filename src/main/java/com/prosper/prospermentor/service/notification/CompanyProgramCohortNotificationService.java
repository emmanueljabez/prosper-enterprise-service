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
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyProgramCohortNotificationService {

    private final EmailInterface emailInterface;
    private final SpringTemplateEngine templateEngine;
    private final NautixWhatsAppService nautixWhatsAppService;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${nautix.whatsapp.cohort-participant-added-template:prosper_cohort_participant_added_v2}")
    private String cohortAddedWhatsappTemplateName;

    @Value("${nautix.whatsapp.cohort-participant-confirmed-template:prosper_cohort_participant_confirmed_v2}")
    private String cohortConfirmedWhatsappTemplateName;

    @Value("${nautix.whatsapp.circle-assigned-template:prosper_circle_assigned_v2}")
    private String circleAssignedWhatsappTemplateName;

    public DeliveryAttemptResult sendCohortParticipantAdded(CompanyProgramCohortParticipant participant) {
        String actionUrl = employeeProgramsUrl();
        CompanyProgramCohort cohort = participant != null ? participant.getCohort() : null;
        String cohortName = cohortName(cohort);
        Context context = participantContext(participant, actionUrl);

        boolean emailSent = sendEmail(
                email(participant),
                "You're added to " + cohortName + " on " + appName,
                "email/cohort-participant-added",
                context
        );
        boolean whatsappSent = sendParticipantWhatsapp(
                cohortAddedWhatsappTemplateName,
                participant,
                List.of(
                        participantFirstName(participant),
                        companyName(cohort),
                        programName(cohort),
                        cohortName,
                        programBrief(cohort),
                        actionUrl
                )
        );
        return DeliveryAttemptResult.builder()
                .emailSent(emailSent)
                .whatsappSent(whatsappSent)
                .build();
    }

    public DeliveryAttemptResult sendCohortParticipantConfirmed(CompanyProgramCohortParticipant participant) {
        String actionUrl = employeeProgramsUrl();
        CompanyProgramCohort cohort = participant != null ? participant.getCohort() : null;
        String cohortName = cohortName(cohort);
        Context context = participantContext(participant, actionUrl);

        boolean emailSent = sendEmail(
                email(participant),
                "You're confirmed for " + cohortName + " on " + appName,
                "email/cohort-participant-confirmed",
                context
        );
        boolean whatsappSent = sendParticipantWhatsapp(
                cohortConfirmedWhatsappTemplateName,
                participant,
                List.of(
                        participantFirstName(participant),
                        companyName(cohort),
                        programName(cohort),
                        cohortName,
                        programBrief(cohort),
                        actionUrl
                )
        );
        return DeliveryAttemptResult.builder()
                .emailSent(emailSent)
                .whatsappSent(whatsappSent)
                .build();
    }

    public DeliveryAttemptResult sendCircleAssigned(CommonInterestCircleMembership membership) {
        CommonInterestCircle circle = membership != null ? membership.getCircle() : null;
        CompanyProgramCohortParticipant participant = membership != null ? membership.getCohortParticipant() : null;
        CompanyProgramCohort cohort = circle != null ? circle.getCohort() : participant != null ? participant.getCohort() : null;
        String actionUrl = employeeProgramsUrl();
        Context context = participantContext(participant, actionUrl);
        context.setVariable("circle", circle);

        boolean emailSent = sendEmail(
                email(participant),
                "You're in " + circleName(circle) + " for " + cohortName(cohort),
                "email/circle-assigned",
                context
        );
        boolean whatsappSent = sendParticipantWhatsapp(
                circleAssignedWhatsappTemplateName,
                participant,
                List.of(
                        participantFirstName(participant),
                        circleName(circle),
                        cohortName(cohort),
                        programName(cohort),
                        programBrief(cohort),
                        actionUrl
                )
        );
        return DeliveryAttemptResult.builder()
                .emailSent(emailSent)
                .whatsappSent(whatsappSent)
                .build();
    }

    private boolean sendEmail(String recipientEmail, String subject, String templateName, Context context) {
        if (!StringUtils.hasText(recipientEmail)) {
            log.debug("Skipping {} email because participant email is missing", templateName);
            return false;
        }
        try {
            String html = templateEngine.process(templateName, context);
            emailInterface.sendEmail(recipientEmail, subject, html, List.of());
            return true;
        } catch (Exception e) {
            log.error("Failed to send {} email to {}: {}", templateName, recipientEmail, e.getMessage(), e);
            return false;
        }
    }

    private boolean sendParticipantWhatsapp(String templateName,
                                            CompanyProgramCohortParticipant participant,
                                            List<String> bodyParameters) {
        String phone = phone(participant);
        if (!StringUtils.hasText(phone)) {
            log.debug("Skipping {} WhatsApp because participant phone is missing", templateName);
            return false;
        }
        try {
            nautixWhatsAppService.sendTemplateMessage(templateName, phone, bodyParameters);
            return true;
        } catch (Exception e) {
            log.error("Failed to send {} WhatsApp to {}: {}", templateName, phone, e.getMessage(), e);
            return false;
        }
    }

    private Context participantContext(CompanyProgramCohortParticipant participant, String actionUrl) {
        CompanyProgramCohort cohort = participant != null ? participant.getCohort() : null;
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Company company = companyProgram != null ? companyProgram.getCompany() : null;

        Context context = new Context();
        context.setVariable("appName", appName);
        context.setVariable("baseUrl", frontendUrl);
        context.setVariable("participant", participant);
        context.setVariable("participantName", participantFirstName(participant));
        context.setVariable("company", company);
        context.setVariable("companyProgram", companyProgram);
        context.setVariable("cohort", cohort);
        context.setVariable("actionUrl", actionUrl);
        return context;
    }

    private String employeeProgramsUrl() {
        return trimTrailingSlash(frontendUrl) + "/app/employee/programs";
    }

    private String email(CompanyProgramCohortParticipant participant) {
        Profile profile = participant != null ? participant.getProfile() : null;
        return Stream.of(profile != null ? profile.getEmail() : null, participant != null ? participant.getEmailSnapshot() : null)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String phone(CompanyProgramCohortParticipant participant) {
        Profile profile = participant != null ? participant.getProfile() : null;
        return Stream.of(profile != null ? profile.getPhone() : null, participant != null ? participant.getPhoneSnapshot() : null)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String participantFirstName(CompanyProgramCohortParticipant participant) {
        Profile profile = participant != null ? participant.getProfile() : null;
        String firstName = profile != null ? profile.getFirstName() : participant != null ? participant.getFirstNameSnapshot() : null;
        if (StringUtils.hasText(firstName)) {
            return firstName.trim();
        }
        String fullName = Stream.of(
                        profile != null ? profile.getFirstName() : participant != null ? participant.getFirstNameSnapshot() : null,
                        profile != null ? profile.getLastName() : participant != null ? participant.getLastNameSnapshot() : null
                )
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((first, second) -> first + " " + second)
                .orElse(null);
        if (StringUtils.hasText(fullName)) {
            return fullName;
        }
        String recipientEmail = email(participant);
        return StringUtils.hasText(recipientEmail) ? recipientEmail : "Participant";
    }

    private String companyName(CompanyProgramCohort cohort) {
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        Company company = companyProgram != null ? companyProgram.getCompany() : null;
        return company != null && StringUtils.hasText(company.getName()) ? company.getName() : "your company";
    }

    private String programName(CompanyProgramCohort cohort) {
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        return companyProgram != null && StringUtils.hasText(companyProgram.getName())
                ? companyProgram.getName()
                : "your mentorship program";
    }

    private String programBrief(CompanyProgramCohort cohort) {
        CompanyProgram companyProgram = cohort != null ? cohort.getCompanyProgram() : null;
        String brief = Stream.of(
                        companyProgram != null ? companyProgram.getObjective() : null,
                        companyProgram != null ? companyProgram.getTargetAudienceDescription() : null
                )
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse("A guided mentorship journey for your cohort.");
        return brief.length() <= 320 ? brief : brief.substring(0, 317) + "...";
    }

    private String cohortName(CompanyProgramCohort cohort) {
        return cohort != null && StringUtils.hasText(cohort.getName()) ? cohort.getName() : "your cohort";
    }

    private String circleName(CommonInterestCircle circle) {
        return circle != null && StringUtils.hasText(circle.getName()) ? circle.getName() : "your circle";
    }

    private String trimTrailingSlash(String value) {
        String resolved = StringUtils.hasText(value) ? value.trim() : "http://localhost:3000";
        return Objects.toString(resolved, "").endsWith("/")
                ? resolved.substring(0, resolved.length() - 1)
                : resolved;
    }

    @Builder
    public record DeliveryAttemptResult(boolean emailSent, boolean whatsappSent) {
    }
}
