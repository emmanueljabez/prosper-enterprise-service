package com.prosper.prospermentor.badge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeAutoAwardHookTest {

    @Test
    void companyServiceAwardsBadgesAfterCompanyLinks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/prosper/prospermentor/service/CompanyService.java"));

        assertThat(source).contains("private final BadgeService badgeService;");
        assertThat(source).contains("badgeService.awardAffiliationBadgesForCompanyLink(");
        assertThat(source).contains("Automatic - company link confirmed");
        assertThat(source).contains("Automatic - company invitation confirmed");
    }

    @Test
    void mentorAssignmentServiceAwardsMentorAffiliationBadge() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/prosper/prospermentor/service/CompanyProgramMentorAssignmentService.java"));

        assertThat(source).contains("private final BadgeService badgeService;");
        assertThat(source).contains("badgeService.awardAffiliationBadgesForCompanyLink(");
        assertThat(source).contains("Automatic - company program mentor assignment confirmed");
    }
}
