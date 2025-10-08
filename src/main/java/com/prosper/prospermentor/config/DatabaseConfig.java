package com.prosper.prospermentor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database configuration for Supabase PostgreSQL connection
 * Consolidated JPA configuration to avoid circular dependencies with Flyway
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.prosper.prospermentor.repository")
@EnableTransactionManagement
@EnableJpaAuditing
public class DatabaseConfig {

    // DataSource will be auto-configured by Spring Boot using application.properties
    // No need for manual configuration
    // JPA Auditing is enabled here instead of main application class
    // to avoid circular dependency issues with Flyway
}

