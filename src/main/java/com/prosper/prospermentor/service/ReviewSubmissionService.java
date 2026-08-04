package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.entity.ReviewAnswer;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.repository.ReviewAnswerRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewSubmissionService {

    private final ReviewRequestRepository reviewRequestRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final ReviewLifecycleService reviewLifecycleService;

    public ReviewSubmissionResult submitFlowReview(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new IllegalArgumentException("Flow submission payload must be a JSON object");
        }

        UUID reviewRequestId = parseRequiredUuid(payload, "review_request_id", "reviewRequestId");
        String submittedToken = parseRequiredText(payload, "review_token", "reviewToken");

        ReviewRequest request = reviewRequestRepository.findById(reviewRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Review request not found"));

        validateRequestState(request, submittedToken);

        List<QuestionDefinition> questions = resolveQuestionDefinitions(request);
        List<ReviewAnswer> answers = new ArrayList<>();
        int sortOrder = 0;
        for (QuestionDefinition question : questions) {
            ReviewAnswer answer = buildAnswer(request, payload, question, sortOrder++);
            if (answer != null) {
                answers.add(answer);
            }
        }

        reviewAnswerRepository.deleteByReviewRequest_Id(request.getId());
        reviewAnswerRepository.saveAll(answers);

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(ReviewRequest.ReviewRequestStatus.SUBMITTED);
        request.setSubmittedAt(now);
        request.setLastError(null);
        reviewRequestRepository.save(request);

        ReviewCycle cycle = request.getReviewCycle();
        List<ReviewRequest> cycleRequests = reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId());
        boolean allSubmitted = cycleRequests.stream()
                .allMatch(candidate -> candidate.getStatus() == ReviewRequest.ReviewRequestStatus.SUBMITTED
                        || candidate.getId().equals(request.getId()));

        if (allSubmitted) {
            cycle.setStatus(ReviewCycle.ReviewCycleStatus.REVEALED);
            cycle.setRevealedAt(now);
        } else {
            cycle.setStatus(ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED);
        }
        reviewCycleRepository.save(cycle);

        if (allSubmitted) {
            reviewLifecycleService.processRevealedCycle(cycle);
        }

        log.info("Stored {} review answers for request {}. cycleStatus={}",
                answers.size(), request.getId(), cycle.getStatus());

        return new ReviewSubmissionResult(
                request.getId(),
                cycle.getId(),
                request.getStatus().name(),
                cycle.getStatus().name(),
                allSubmitted
        );
    }

    private void validateRequestState(ReviewRequest request, String submittedToken) {
        if (!StringUtils.hasText(request.getSubmissionToken()) || !request.getSubmissionToken().equals(submittedToken)) {
            throw new IllegalArgumentException("Invalid review token");
        }

        if (request.getStatus() == ReviewRequest.ReviewRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Review request has already been submitted");
        }
        if (request.getStatus() == ReviewRequest.ReviewRequestStatus.EXPIRED
                || request.getStatus() == ReviewRequest.ReviewRequestStatus.CANCELLED) {
            throw new IllegalStateException("Review request is no longer active");
        }

        ReviewCycle cycle = request.getReviewCycle();
        if (cycle == null) {
            throw new IllegalStateException("Review cycle is missing for this request");
        }
        if (cycle.getExpiresAt() != null && LocalDateTime.now().isAfter(cycle.getExpiresAt())) {
            request.setStatus(ReviewRequest.ReviewRequestStatus.EXPIRED);
            reviewRequestRepository.save(request);
            throw new IllegalStateException("Review request has expired");
        }
    }

    private List<QuestionDefinition> resolveQuestionDefinitions(ReviewRequest request) {
        ReviewCycle.ReviewType reviewType = request.getReviewCycle().getType();
        ReviewRequest.ReviewRole reviewerRole = request.getReviewerRole();

        if (reviewType == ReviewCycle.ReviewType.FIT) {
            return List.of(
                    new QuestionDefinition("fit_score", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("want_to_continue_with_same_match", ReviewAnswer.AnswerType.BOOLEAN, true),
                    new QuestionDefinition("optional_comment", ReviewAnswer.AnswerType.TEXT, false)
            );
        }

        if (reviewerRole == ReviewRequest.ReviewRole.MENTEE) {
            return List.of(
                    new QuestionDefinition("quality_of_guidance", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("listened_and_adapted", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("presence_and_punctuality", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("knowledge_generosity", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("space_for_my_insights", ReviewAnswer.AnswerType.SCORE, true),
                    new QuestionDefinition("recommend_continue", ReviewAnswer.AnswerType.BOOLEAN, true),
                    new QuestionDefinition("optional_comment", ReviewAnswer.AnswerType.TEXT, false)
            );
        }

        return List.of(
                new QuestionDefinition("preparedness", ReviewAnswer.AnswerType.SCORE, true),
                new QuestionDefinition("active_engagement", ReviewAnswer.AnswerType.SCORE, true),
                new QuestionDefinition("respect_for_time", ReviewAnswer.AnswerType.SCORE, true),
                new QuestionDefinition("ownership_mindset", ReviewAnswer.AnswerType.SCORE, true),
                new QuestionDefinition("reciprocal_value", ReviewAnswer.AnswerType.SCORE, true),
                new QuestionDefinition("recommend_continue", ReviewAnswer.AnswerType.BOOLEAN, true),
                new QuestionDefinition("optional_comment", ReviewAnswer.AnswerType.TEXT, false)
        );
    }

    private ReviewAnswer buildAnswer(ReviewRequest request,
                                     JsonNode payload,
                                     QuestionDefinition question,
                                     int sortOrder) {
        JsonNode valueNode = resolveNode(payload, question.code(), toCamelCase(question.code()));

        if ((valueNode == null || valueNode.isNull() || valueNode.asText("").isBlank()) && !question.required()) {
            return null;
        }

        ReviewAnswer answer = new ReviewAnswer();
        answer.setReviewRequest(request);
        answer.setQuestionCode(question.code());
        answer.setAnswerType(question.answerType());
        answer.setSortOrder(sortOrder);

        switch (question.answerType()) {
            case SCORE -> answer.setNumericScore(parseScore(valueNode, question.code()));
            case BOOLEAN -> answer.setBooleanAnswer(parseBooleanAnswer(valueNode, question.code()));
            case TEXT -> {
                String text = valueNode != null && !valueNode.isNull() ? valueNode.asText(null) : null;
                if (question.required() && !StringUtils.hasText(text)) {
                    throw new IllegalArgumentException("Missing answer for " + question.code());
                }
                answer.setTextAnswer(StringUtils.hasText(text) ? text.trim() : null);
            }
        }

        return answer;
    }

    private Integer parseScore(JsonNode valueNode, String questionCode) {
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Missing score for " + questionCode);
        }
        int score;
        if (valueNode.isNumber()) {
            score = valueNode.asInt();
        } else {
            String raw = valueNode.asText("").trim();
            if (!StringUtils.hasText(raw)) {
                throw new IllegalArgumentException("Missing score for " + questionCode);
            }
            try {
                score = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid score for " + questionCode);
            }
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score for " + questionCode + " must be between 1 and 5");
        }
        return score;
    }

    private Boolean parseBooleanAnswer(JsonNode valueNode, String questionCode) {
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Missing answer for " + questionCode);
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        if (valueNode.isNumber()) {
            int numeric = valueNode.asInt();
            if (numeric == 1) {
                return true;
            }
            if (numeric == 2) {
                return false;
            }
        }
        String raw = valueNode.asText("").trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "yes", "true", "1" -> true;
            case "no", "false", "2" -> false;
            default -> throw new IllegalArgumentException("Invalid answer for " + questionCode);
        };
    }

    private UUID parseRequiredUuid(JsonNode payload, String primaryField, String secondaryField) {
        String raw = parseRequiredText(payload, primaryField, secondaryField);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID in " + primaryField);
        }
    }

    private String parseRequiredText(JsonNode payload, String primaryField, String secondaryField) {
        JsonNode node = resolveNode(payload, primaryField, secondaryField);
        String raw = node != null && !node.isNull() ? node.asText(null) : null;
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Missing field " + primaryField);
        }
        return raw.trim();
    }

    private JsonNode resolveNode(JsonNode payload, String primaryField, String secondaryField) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        if (payload.has(primaryField)) {
            return payload.get(primaryField);
        }
        if (payload.has(secondaryField)) {
            return payload.get(secondaryField);
        }
        String normalizedPrimary = normalizeFieldKey(primaryField);
        String normalizedSecondary = normalizeFieldKey(secondaryField);
        var fieldNames = payload.fieldNames();
        while (fieldNames.hasNext()) {
            String candidate = fieldNames.next();
            String normalizedCandidate = normalizeFieldKey(candidate);
            if (normalizedPrimary.equals(normalizedCandidate) || normalizedSecondary.equals(normalizedCandidate)) {
                return payload.get(candidate);
            }
        }
        return null;
    }

    private String normalizeFieldKey(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return "";
        }
        return fieldName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        if (parts.length == 0) {
            return snakeCase;
        }
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private record QuestionDefinition(String code, ReviewAnswer.AnswerType answerType, boolean required) {}

    public record ReviewSubmissionResult(UUID reviewRequestId,
                                         UUID reviewCycleId,
                                         String requestStatus,
                                         String cycleStatus,
                                         boolean revealed) {}
}
