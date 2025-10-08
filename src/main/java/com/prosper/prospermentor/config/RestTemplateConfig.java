package com.prosper.prospermentor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate beans
 * Provides HTTP client beans for external API integrations
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Configure RestTemplate for general HTTP requests
     * Used by meeting providers (Zoom, Google Meet) and other external API calls
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


