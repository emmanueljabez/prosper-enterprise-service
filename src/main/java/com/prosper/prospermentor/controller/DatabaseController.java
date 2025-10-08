package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.service.DatabaseInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for database information and management endpoints
 */
@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
@Slf4j
public class DatabaseController {

    private final DatabaseInfoService databaseInfoService;

    /**
     * Test database connection
     */
    @GetMapping("/test")
    public ResponseEntity<Object> testConnection() {
        log.info("Testing database connection");
        JsonNode result = databaseInfoService.testConnection();
        return ResponseEntity.ok(result);
    }

    /**
     * Get database information
     */
    @GetMapping("/info")
    public ResponseEntity<Object> getDatabaseInfo() {
        log.info("Fetching database information");
        JsonNode result = databaseInfoService.getDatabaseInfo();
        return ResponseEntity.ok(result);
    }

    /**
     * Get all tables in the database
     */
    @GetMapping("/tables")
    public ResponseEntity<Object> getAllTables() {
        log.info("Fetching all database tables");
        JsonNode result = databaseInfoService.getAllTables();
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed information about a specific table
     */
    @GetMapping("/tables/{tableName}")
    public ResponseEntity<Object> getTableDetails(@PathVariable String tableName) {
        log.info("Fetching details for table: {}", tableName);
        JsonNode result = databaseInfoService.getTableDetails(tableName);
        
        // Check if there was an error
        if (result.has("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.ok(result);
    }
}
