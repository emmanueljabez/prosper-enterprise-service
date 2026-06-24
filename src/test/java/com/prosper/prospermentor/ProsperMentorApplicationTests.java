package com.prosper.prospermentor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProsperMentorApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("prospermentor_test")
            .withUsername("prospermentor")
            .withPassword("prospermentor");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.validate-on-migrate", () -> "false");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "587");
        registry.add("spring.mail.username", () -> "test");
        registry.add("spring.mail.password", () -> "test");
        registry.add("wati.token", () -> "test-token");
        registry.add("prosper.mail.email_address", () -> "customersuccess@prospermentor.com");
        registry.add("prosper.mail.username", () -> "Prosper Mentor");
        registry.add("prosper.mail.password", () -> "test-password");
        registry.add("prosper.mail.host", () -> "localhost");
        registry.add("prosper.mail.port", () -> "587");
        registry.add("cybersource.merchant.id", () -> "test-merchant");
        registry.add("cybersource.access.key", () -> "test-access-key");
        registry.add("cybersource.secret.key", () -> "test-secret-key");
        registry.add("cybersource.profile.id", () -> "test-profile");
        registry.add("cybersource.endpoint.url", () -> "https://example.test/pay");
        registry.add("cybersource.callback.url", () -> "http://localhost:8080/api/v1/payments/cybersource/callback");
        registry.add("cybersource.return.url", () -> "http://localhost:3000/payment/cybersource/response");
        registry.add("cybersource.cancel.url", () -> "http://localhost:3000/payment/cybersource/cancel");
    }

    @Test
    void contextLoads() {
    }

}
