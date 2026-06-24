package com.prosper.prospermentor.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendedPlanDtoTest {

    @Test
    void oneTimePlansUsePackageDisplayCopy() {
        RecommendedPlanDto plan = RecommendedPlanDto.builder()
                .name("3-Session Pack")
                .code("PACK_3")
                .cost(new BigDecimal("11000.00"))
                .currency("KES")
                .sessionsPerPeriod(3)
                .billingType("ONE_TIME")
                .build();

        assertEquals("KES 11,000", plan.getFormattedPrice());
        assertEquals("3 one-on-one sessions", plan.getSessionsDescription());
    }

    @Test
    void recurringPlansKeepMonthlyDisplayCopy() {
        RecommendedPlanDto plan = RecommendedPlanDto.builder()
                .name("All Access")
                .code("ALL_ACCESS")
                .cost(new BigDecimal("4000.00"))
                .currency("KES")
                .sessionsPerPeriod(1)
                .billingType("RECURRING")
                .build();

        assertEquals("KES 4,000/month", plan.getFormattedPrice());
        assertEquals("1 session per month", plan.getSessionsDescription());
    }
}
