package com.prosper.prospermentor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Supabase configuration properties and beans
 */
@Configuration
@ConfigurationProperties(prefix = "supabase")
@Getter
@Setter
public class SupabaseConfig {

    private String url;
    private String anonKey;
    private String serviceRoleKey;
    private String jwtSecret;

    /**
     * WebClient configured for Supabase API calls with improved DNS handling
     */
    @Bean
    public WebClient supabaseWebClient() {
        // Create connection provider with custom settings
        ConnectionProvider connectionProvider = ConnectionProvider.builder("supabase")
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // Create HttpClient with custom DNS and timeout settings
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofSeconds(30))
                .resolver(spec -> spec.queryTimeout(Duration.ofSeconds(5)));

        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("apikey", anonKey)
                .defaultHeader("Authorization", "Bearer " + anonKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Admin WebClient with service role key for administrative operations
     */
    @Bean
    public WebClient supabaseAdminWebClient() {
        System.out.println("Creating admin WebClient with URL: " + url);
        System.out.println("Service role key length: " + (serviceRoleKey != null ? serviceRoleKey.length() : "null"));
        System.out.println("Service role key starts with: " + (serviceRoleKey != null && serviceRoleKey.length() > 20 ? serviceRoleKey.substring(0, 20) + "..." : serviceRoleKey));
        
        // Create connection provider with custom settings
        ConnectionProvider connectionProvider = ConnectionProvider.builder("supabase-admin")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // Create HttpClient with custom DNS and timeout settings
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofSeconds(30))
                .resolver(spec -> spec.queryTimeout(Duration.ofSeconds(5)));
        
        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

