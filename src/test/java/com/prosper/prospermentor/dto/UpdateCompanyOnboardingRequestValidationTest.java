package com.prosper.prospermentor.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCompanyOnboardingRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void validationShouldAllowCompanyProfileOnlyOnboardingPayload() {
        UpdateCompanyOnboardingRequest request = new UpdateCompanyOnboardingRequest();
        request.setIndustry("Aviation");
        request.setCompanySizeBand("1001-5000");
        request.setCountry("Kenya");
        request.setTimezone("Africa/Nairobi");

        Set<ConstraintViolation<UpdateCompanyOnboardingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .doesNotContain(
                        "mentorshipObjective",
                        "targetAudienceDescription",
                        "programDesignPreference",
                        "recommendedProgramIds"
                );
        assertThat(violations).isEmpty();
    }
}
