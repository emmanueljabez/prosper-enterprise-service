package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for retrieving database information from Supabase
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseInfoService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get all tables in the current database schema
     */
    public JsonNode getAllTables() {
        try {
            log.info("Fetching all tables from database");
            
            String sql = """
                SELECT 
                    table_name,
                    table_type,
                    table_schema
                FROM information_schema.tables 
                WHERE table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
                ORDER BY table_schema, table_name
                """;

            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("total_tables", tables.size());
            
            ArrayNode tableArray = objectMapper.createArrayNode();
            for (Map<String, Object> table : tables) {
                ObjectNode tableNode = objectMapper.createObjectNode();
                tableNode.put("name", (String) table.get("table_name"));
                tableNode.put("type", (String) table.get("table_type"));
                tableNode.put("schema", (String) table.get("table_schema"));
                tableArray.add(tableNode);
            }
            result.set("tables", tableArray);
            
            log.info("Successfully fetched {} tables", tables.size());
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching tables: {}", e.getMessage(), e);
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("error", "Failed to fetch tables: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * Get detailed information about a specific table
     * Supports both simple table names (searches in public schema) and schema-qualified names (schema.table)
     */
    public JsonNode getTableDetails(String tableIdentifier) {
        try {
            log.info("Fetching details for table: {}", tableIdentifier);
            
            // Parse schema and table name
            String schemaName = "public"; // default schema
            String tableName = tableIdentifier;
            
            if (tableIdentifier.contains(".")) {
                String[] parts = tableIdentifier.split("\\.", 2);
                schemaName = parts[0];
                tableName = parts[1];
            }
            
            log.info("Using schema: {}, table: {}", schemaName, tableName);
            
            // Get table columns
            String columnsSql = """
                SELECT 
                    column_name,
                    data_type,
                    is_nullable,
                    column_default,
                    character_maximum_length,
                    numeric_precision,
                    numeric_scale
                FROM information_schema.columns 
                WHERE table_name = ? AND table_schema = ?
                ORDER BY ordinal_position
                """;
            
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnsSql, tableName, schemaName);
            
            // Get table constraints
            String constraintsSql = """
                SELECT 
                    tc.constraint_name,
                    tc.constraint_type,
                    kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu 
                    ON tc.constraint_name = kcu.constraint_name
                WHERE tc.table_name = ? AND tc.table_schema = ?
                ORDER BY tc.constraint_type, kcu.column_name
                """;
            
            List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintsSql, tableName, schemaName);
            
            // Get row count with proper schema qualification
            String qualifiedTableName = schemaName.equals("public") ? tableName : "\"" + schemaName + "\".\"" + tableName + "\"";
            String countSql = "SELECT COUNT(*) FROM " + qualifiedTableName;
            Long rowCount = jdbcTemplate.queryForObject(countSql, Long.class);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("table_name", tableName);
            result.put("schema_name", schemaName);
            result.put("qualified_name", tableIdentifier);
            result.put("row_count", rowCount);
            
            // Add columns
            ArrayNode columnsArray = objectMapper.createArrayNode();
            for (Map<String, Object> column : columns) {
                ObjectNode columnNode = objectMapper.createObjectNode();
                columnNode.put("name", (String) column.get("column_name"));
                columnNode.put("type", (String) column.get("data_type"));
                columnNode.put("nullable", "YES".equals(column.get("is_nullable")));
                columnNode.put("default_value", (String) column.get("column_default"));
                
                if (column.get("character_maximum_length") != null) {
                    columnNode.put("max_length", (Integer) column.get("character_maximum_length"));
                }
                if (column.get("numeric_precision") != null) {
                    columnNode.put("precision", (Integer) column.get("numeric_precision"));
                }
                if (column.get("numeric_scale") != null) {
                    columnNode.put("scale", (Integer) column.get("numeric_scale"));
                }
                
                columnsArray.add(columnNode);
            }
            result.set("columns", columnsArray);
            
            // Add constraints
            ArrayNode constraintsArray = objectMapper.createArrayNode();
            for (Map<String, Object> constraint : constraints) {
                ObjectNode constraintNode = objectMapper.createObjectNode();
                constraintNode.put("name", (String) constraint.get("constraint_name"));
                constraintNode.put("type", (String) constraint.get("constraint_type"));
                constraintNode.put("column", (String) constraint.get("column_name"));
                constraintsArray.add(constraintNode);
            }
            result.set("constraints", constraintsArray);
            
            log.info("Successfully fetched details for table: {}", tableName);
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching table details for {}: {}", tableIdentifier, e.getMessage(), e);
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("error", "Failed to fetch table details: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * Get database connection information
     */
    public JsonNode getDatabaseInfo() {
        try {
            log.info("Fetching database connection information");
            
            // Get database version
            String versionSql = "SELECT version()";
            String version = jdbcTemplate.queryForObject(versionSql, String.class);
            
            // Get current database name
            String dbNameSql = "SELECT current_database()";
            String dbName = jdbcTemplate.queryForObject(dbNameSql, String.class);
            
            // Get current user
            String userSql = "SELECT current_user";
            String currentUser = jdbcTemplate.queryForObject(userSql, String.class);
            
            // Get schema information
            String schemasSql = """
                SELECT schema_name 
                FROM information_schema.schemata 
                WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
                ORDER BY schema_name
                """;
            List<String> schemas = jdbcTemplate.queryForList(schemasSql, String.class);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("database_name", dbName);
            result.put("version", version);
            result.put("current_user", currentUser);
            
            ArrayNode schemasArray = objectMapper.createArrayNode();
            schemas.forEach(schemasArray::add);
            result.set("schemas", schemasArray);
            
            log.info("Successfully fetched database information");
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching database info: {}", e.getMessage(), e);
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("error", "Failed to fetch database info: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test database connectivity
     */
    public JsonNode testConnection() {
        try {
            log.info("Testing database connection");
            
            String testSql = "SELECT 1 as test_value, NOW() as current_time";
            Map<String, Object> result = jdbcTemplate.queryForMap(testSql);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("connection_status", "SUCCESS");
            response.put("test_value", (Integer) result.get("test_value"));
            response.put("server_time", result.get("current_time").toString());
            response.put("message", "Database connection is working properly");
            
            log.info("Database connection test successful");
            return response;
            
        } catch (Exception e) {
            log.error("Database connection test failed: {}", e.getMessage(), e);
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("connection_status", "FAILED");
            errorResult.put("error", e.getMessage());
            errorResult.put("message", "Database connection test failed");
            return errorResult;
        }
    }
}
