package com.prosper.prospermentor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for debugging Supabase database issues that might cause user creation failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseDatabaseDebugService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Check for database triggers that might be causing user creation issues
     */
    public void checkDatabaseTriggers() {
        try {
            log.info("=== Checking Database Triggers ===");
            
            // Check for triggers on auth.users table
            String triggerQuery = """
                SELECT trigger_name, event_manipulation, action_statement, action_timing
                FROM information_schema.triggers 
                WHERE event_object_schema = 'auth' 
                AND event_object_table = 'users'
                ORDER BY trigger_name;
                """;
            
            List<Map<String, Object>> triggers = jdbcTemplate.queryForList(triggerQuery);
            
            if (triggers.isEmpty()) {
                log.info("No custom triggers found on auth.users table");
            } else {
                log.info("Found {} triggers on auth.users table:", triggers.size());
                for (Map<String, Object> trigger : triggers) {
                    log.info("- Trigger: {} | Event: {} | Timing: {}", 
                            trigger.get("trigger_name"),
                            trigger.get("event_manipulation"),
                            trigger.get("action_timing"));
                    log.debug("  Action: {}", trigger.get("action_statement"));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check database triggers: {}", e.getMessage());
        }
    }

    /**
     * Check for foreign key constraints that might be failing
     */
    public void checkForeignKeyConstraints() {
        try {
            log.info("=== Checking Foreign Key Constraints ===");
            
            String constraintQuery = """
                SELECT 
                    tc.constraint_name,
                    tc.table_name,
                    kcu.column_name,
                    ccu.table_name AS foreign_table_name,
                    ccu.column_name AS foreign_column_name
                FROM information_schema.table_constraints AS tc
                JOIN information_schema.key_column_usage AS kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage AS ccu
                    ON ccu.constraint_name = tc.constraint_name
                    AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                    AND (tc.table_name = 'users' OR ccu.table_name = 'users')
                    AND tc.table_schema IN ('auth', 'public')
                ORDER BY tc.table_name, tc.constraint_name;
                """;
            
            List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintQuery);
            
            if (constraints.isEmpty()) {
                log.info("No foreign key constraints found involving users table");
            } else {
                log.info("Found {} foreign key constraints involving users table:", constraints.size());
                for (Map<String, Object> constraint : constraints) {
                    log.info("- Constraint: {} | Table: {}.{} -> {}.{}", 
                            constraint.get("constraint_name"),
                            constraint.get("table_name"),
                            constraint.get("column_name"),
                            constraint.get("foreign_table_name"),
                            constraint.get("foreign_column_name"));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check foreign key constraints: {}", e.getMessage());
        }
    }

    /**
     * Check database functions that might be called during user creation
     */
    public void checkDatabaseFunctions() {
        try {
            log.info("=== Checking Database Functions ===");
            
            String functionQuery = """
                SELECT 
                    routine_name,
                    routine_type,
                    security_type,
                    routine_definition
                FROM information_schema.routines 
                WHERE routine_schema IN ('auth', 'public')
                    AND routine_name LIKE '%user%'
                ORDER BY routine_name;
                """;
            
            List<Map<String, Object>> functions = jdbcTemplate.queryForList(functionQuery);
            
            if (functions.isEmpty()) {
                log.info("No custom user-related functions found");
            } else {
                log.info("Found {} user-related functions:", functions.size());
                for (Map<String, Object> function : functions) {
                    log.info("- Function: {} | Type: {} | Security: {}", 
                            function.get("routine_name"),
                            function.get("routine_type"),
                            function.get("security_type"));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check database functions: {}", e.getMessage());
        }
    }

    /**
     * Check if there are any problematic data types or constraints in the schema
     */
    public void checkAuthUsersSchema() {
        try {
            log.info("=== Checking auth.users Schema ===");
            
            String schemaQuery = """
                SELECT 
                    column_name,
                    data_type,
                    is_nullable,
                    column_default,
                    character_maximum_length
                FROM information_schema.columns 
                WHERE table_schema = 'auth' 
                    AND table_name = 'users'
                ORDER BY ordinal_position;
                """;
            
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(schemaQuery);
            
            log.info("auth.users table structure:");
            for (Map<String, Object> column : columns) {
                log.info("- Column: {} | Type: {} | Nullable: {} | Default: {}", 
                        column.get("column_name"),
                        column.get("data_type"),
                        column.get("is_nullable"),
                        column.get("column_default"));
            }
            
        } catch (Exception e) {
            log.error("Failed to check auth.users schema: {}", e.getMessage());
        }
    }

    /**
     * Run comprehensive database debugging
     */
    public void runComprehensiveDebug() {
        log.info("Starting comprehensive Supabase database debugging...");
        
        checkAuthUsersSchema();
        checkDatabaseTriggers();
        checkForeignKeyConstraints();
        checkDatabaseFunctions();
        
        log.info("Database debugging completed.");
    }

    /**
     * Check database logs (if accessible) for recent errors
     */
    public void checkRecentErrors() {
        try {
            log.info("=== Checking Recent Database Errors ===");
            
            // Note: This might not work depending on permissions and Supabase configuration
            String logQuery = """
                SELECT 
                    message,
                    error_severity,
                    sql_state_code
                FROM pg_stat_statements 
                WHERE query LIKE '%auth.users%'
                    AND calls > 0
                ORDER BY last_exec_time DESC 
                LIMIT 10;
                """;
            
            try {
                List<Map<String, Object>> errors = jdbcTemplate.queryForList(logQuery);
                
                if (errors.isEmpty()) {
                    log.info("No recent errors found in pg_stat_statements");
                } else {
                    log.info("Recent database activity on auth.users:");
                    for (Map<String, Object> error : errors) {
                        log.info("- Message: {} | Severity: {} | State: {}", 
                                error.get("message"),
                                error.get("error_severity"),
                                error.get("sql_state_code"));
                    }
                }
            } catch (Exception e) {
                log.debug("pg_stat_statements not accessible (this is normal): {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.debug("Database error checking not available: {}", e.getMessage());
        }
    }
}



