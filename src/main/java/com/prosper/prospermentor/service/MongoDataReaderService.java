package com.prosper.prospermentor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for reading and parsing MongoDB JSON collection files
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MongoDataReaderService {

    private final ObjectMapper objectMapper;

    private static final String COLLECTIONS_DIR = "collections";

    /**
     * Read and parse users.json file
     */
    public List<JsonNode> readUsers() {
        return readJsonFile("users.json", "users");
    }

    /**
     * Read and parse topics.json file
     */
    public List<JsonNode> readTopics() {
        return readJsonFile("topics.json", "topics");
    }

    /**
     * Read and parse advisor_sessions.json file
     */
    public List<JsonNode> readSessions() {
        return readJsonFile("advisor_sessions.json", "advisor_sessions");
    }

    /**
     * Filter users by userType (ADVISEE or ADVISOR)
     */
    public List<JsonNode> filterUsersByType(List<JsonNode> users, String userType) {
        if (users == null || userType == null) {
            log.warn("Cannot filter users: users list or userType is null");
            return new ArrayList<>();
        }

        List<JsonNode> filtered = users.stream()
                .filter(user -> {
                    JsonNode userTypeNode = user.get("userType");
                    return userTypeNode != null && userType.equals(userTypeNode.asText());
                })
                .toList();

        log.info("Filtered {} users of type {} from {} total users", 
                filtered.size(), userType, users.size());
        
        return filtered;
    }

    /**
     * Get advisees (userType: ADVISEE)
     */
    public List<JsonNode> getAdvisees() {
        List<JsonNode> users = readUsers();
        return filterUsersByType(users, "ADVISEE");
    }

    /**
     * Get advisors (userType: ADVISOR)
     */
    public List<JsonNode> getAdvisors() {
        List<JsonNode> users = readUsers();
        return filterUsersByType(users, "ADVISOR");
    }

    /**
     * Extract advisor topics from advisorDetails
     */
    public List<String> extractAdvisorTopics(JsonNode advisor) {
        if (advisor == null) {
            return new ArrayList<>();
        }

        JsonNode advisorDetails = advisor.get("advisorDetails");
        if (advisorDetails == null) {
            log.debug("No advisorDetails found for advisor: {}", advisor.get("_id"));
            return new ArrayList<>();
        }

        JsonNode topicsNode = advisorDetails.get("topics");
        if (topicsNode == null || !topicsNode.isArray()) {
            log.debug("No topics array found in advisorDetails for advisor: {}", advisor.get("_id"));
            return new ArrayList<>();
        }

        List<String> topics = new ArrayList<>();
        topicsNode.forEach(topic -> {
            if (topic.isTextual()) {
                topics.add(topic.asText());
            }
        });

        log.debug("Extracted {} topics for advisor: {}", topics.size(), advisor.get("_id"));
        return topics;
    }

    /**
     * Get MongoDB ObjectID as string
     */
    public String extractMongoId(JsonNode document) {
        if (document == null) {
            return null;
        }

        JsonNode idNode = document.get("_id");
        if (idNode == null) {
            return null;
        }

        // Handle different MongoDB ID formats
        if (idNode.isTextual()) {
            return idNode.asText();
        } else if (idNode.isObject()) {
            JsonNode oidNode = idNode.get("$oid");
            if (oidNode != null) {
                return oidNode.asText();
            }
        } else if (idNode.isNumber()) {
            return idNode.asText();
        }

        return idNode.toString();
    }

    /**
     * Validate that a MongoDB document has required fields
     */
    public boolean validateDocument(JsonNode document, String... requiredFields) {
        if (document == null) {
            return false;
        }

        for (String field : requiredFields) {
            if (!document.has(field) || document.get(field).isNull()) {
                log.warn("Document missing required field: {}", field);
                return false;
            }
        }

        return true;
    }

    /**
     * Get statistics for loaded data
     */
    public Map<String, Object> getDataStatistics() {
        List<JsonNode> users = readUsers();
        List<JsonNode> topics = readTopics();
        List<JsonNode> sessions = readSessions();

        List<JsonNode> advisees = filterUsersByType(users, "ADVISEE");
        List<JsonNode> advisors = filterUsersByType(users, "ADVISOR");

        return Map.of(
                "total_users", users.size(),
                "advisees", advisees.size(),
                "advisors", advisors.size(),
                "topics", topics.size(),
                "sessions", sessions.size(),
                "advisor_topics_total", advisors.stream()
                        .mapToInt(advisor -> extractAdvisorTopics(advisor).size())
                        .sum()
        );
    }

    /**
     * Generic method to read JSON file from collections directory
     */
    private List<JsonNode> readJsonFile(String filename, String collectionName) {
        try {
            Path filePath = Paths.get(COLLECTIONS_DIR, filename);
            
            if (!Files.exists(filePath)) {
                log.error("Collection file not found: {}", filePath);
                return new ArrayList<>();
            }

            log.info("Reading {} collection from: {}", collectionName, filePath);
            
            String content = Files.readString(filePath);
            if (content.trim().isEmpty()) {
                log.warn("Collection file is empty: {}", filename);
                return new ArrayList<>();
            }

            List<JsonNode> documents = objectMapper.readValue(content, new TypeReference<List<JsonNode>>() {});
            
            log.info("Successfully loaded {} documents from {}", documents.size(), filename);
            return documents;

        } catch (IOException e) {
            log.error("Error reading {} collection from {}: {}", collectionName, filename, e.getMessage(), e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Unexpected error processing {} collection: {}", collectionName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if all required collection files exist
     */
    public boolean validateCollectionFiles() {
        String[] requiredFiles = {"users.json", "topics.json", "advisor_sessions.json"};
        boolean allExist = true;

        for (String filename : requiredFiles) {
            Path filePath = Paths.get(COLLECTIONS_DIR, filename);
            if (!Files.exists(filePath)) {
                log.error("Required collection file missing: {}", filePath);
                allExist = false;
            } else {
                log.debug("Collection file found: {}", filePath);
            }
        }

        return allExist;
    }
}
