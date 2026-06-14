package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prosper.prospermentor.controller.dto.ApiResponse;
import com.prosper.prospermentor.service.ReviewSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/reviews/flow")
@Tag(name = "Review Flow", description = "Public connector endpoints for WhatsApp review flow submissions")
@RequiredArgsConstructor
@Slf4j
public class ReviewFlowController {

    private static final Set<String> SENSITIVE_FIELD_NAMES = Stream.of(
                    "review_token",
                    "reviewToken",
                    "submission_token",
                    "submissionToken"
            )
            .map(ReviewFlowController::normalizeFieldName)
            .collect(Collectors.toUnmodifiableSet());

    private final ReviewSubmissionService reviewSubmissionService;

    @Value("${review.connector.api-key:}")
    private String connectorApiKey;

    @PostMapping("/submit")
    @Operation(summary = "Submit a completed WhatsApp review flow",
            description = "Receives a final review payload from the WhatsApp flow data connector and stores it as a single submission.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitReview(
            @RequestHeader(value = "X-Review-Connector-Key", required = false) String providedConnectorKey,
            @RequestBody JsonNode payload) {
        if (log.isDebugEnabled()) {
            log.debug("Received review flow submission payload: {}", sanitizePayload(payload));
        }

        if (StringUtils.hasText(connectorApiKey) && !connectorApiKey.equals(providedConnectorKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid connector key"));
        }

        if (!StringUtils.hasText(connectorApiKey)) {
            log.warn("review.connector.api-key is not configured; accepting connector submission without shared-key validation");
        }

        try {
            ReviewSubmissionService.ReviewSubmissionResult result = reviewSubmissionService.submitFlowReview(payload);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "reviewRequestId", result.reviewRequestId().toString(),
                    "reviewCycleId", result.reviewCycleId().toString(),
                    "requestStatus", result.requestStatus(),
                    "cycleStatus", result.cycleStatus(),
                    "revealed", result.revealed()
            )));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to process review flow submission: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process review submission"));
        }
    }

    private JsonNode sanitizePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return payload;
        }
        JsonNode copy = payload.deepCopy();
        if (copy instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                if (SENSITIVE_FIELD_NAMES.contains(normalizeFieldName(fieldName))) {
                    maskSensitiveField(objectNode, fieldName);
                }
            });
        }
        return copy;
    }

    private void maskSensitiveField(ObjectNode objectNode, String fieldName) {
        if (objectNode.has(fieldName) && !objectNode.get(fieldName).isNull()) {
            objectNode.put(fieldName, "***");
        }
    }

    private static String normalizeFieldName(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return "";
        }
        return fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
