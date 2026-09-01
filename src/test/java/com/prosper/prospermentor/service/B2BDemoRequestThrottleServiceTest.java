package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class B2BDemoRequestThrottleServiceTest {

    @Test
    void assertAllowed_shouldThrottleRepeatedSubmissionsByWorkEmail() {
        B2BDemoRequestThrottleService service = new B2BDemoRequestThrottleService();

        service.assertAllowed("203.0.113.10", "buyer@example.com");
        service.assertAllowed("203.0.113.11", "buyer@example.com");
        service.assertAllowed("203.0.113.12", "buyer@example.com");

        assertThatThrownBy(() -> service.assertAllowed("203.0.113.13", "buyer@example.com"))
                .isInstanceOf(B2BDemoRequestThrottleService.TooManyDemoRequestsException.class)
                .hasMessage("Too many demo requests for this work email. Please try again later.");
    }

    @Test
    void assertAllowed_shouldThrottleRepeatedSubmissionsByNetwork() {
        B2BDemoRequestThrottleService service = new B2BDemoRequestThrottleService();

        for (int index = 0; index < 20; index++) {
            service.assertAllowed("203.0.113.10", "buyer-" + index + "@example.com");
        }

        assertThatThrownBy(() -> service.assertAllowed("203.0.113.10", "another@example.com"))
                .isInstanceOf(B2BDemoRequestThrottleService.TooManyDemoRequestsException.class)
                .hasMessage("Too many demo requests from this network. Please try again later.");
    }
}
