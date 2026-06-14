package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.ReviewAnswer;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.ReviewAnswerRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewLifecycleService {

    private static final BigDecimal LOW_MENTOR_SCORE_THRESHOLD = new BigDecimal("4.00");
    private static final BigDecimal LOW_MENTEE_SCORE_THRESHOLD = new BigDecimal("3.50");
    private static final BigDecimal LOW_FIT_SCORE_THRESHOLD = new BigDecimal("3.00");

    private final ReviewCycleRepository reviewCycleRepository;
    private final ReviewRequestRepository reviewRequestRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final ReviewAlertRepository reviewAlertRepository;

    @Transactional
    public void processRevealedCycle(ReviewCycle cycle) {
        List<ReviewRequest> requests = reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId());
        requests.stream()
                .filter(request -> request.getStatus() == ReviewRequest.ReviewRequestStatus.SUBMITTED)
                .forEach(this::evaluateSubmittedRequest);
    }

    @Transactional
    public int expireOverdueCycles() {
        Collection<ReviewCycle.ReviewCycleStatus> expirableStatuses = List.of(
                ReviewCycle.ReviewCycleStatus.OPEN,
                ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED
        );

        List<ReviewCycle> cycles = reviewCycleRepository.findByStatusInAndExpiresAtLessThanEqual(expirableStatuses, LocalDateTime.now());
        for (ReviewCycle cycle : cycles) {
            expireCycle(cycle);
        }
        return cycles.size();
    }

    private void expireCycle(ReviewCycle cycle) {
        List<ReviewRequest> requests = reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId());
        boolean hasSubmittedRequest = false;
        LocalDateTime now = LocalDateTime.now();

        for (ReviewRequest request : requests) {
            if (request.getStatus() == ReviewRequest.ReviewRequestStatus.SUBMITTED) {
                hasSubmittedRequest = true;
                continue;
            }

            if (request.getStatus() == ReviewRequest.ReviewRequestStatus.CANCELLED
                    || request.getStatus() == ReviewRequest.ReviewRequestStatus.EXPIRED) {
                continue;
            }

            request.setStatus(ReviewRequest.ReviewRequestStatus.EXPIRED);
            request.setLastError("Review window closed before submission");
            reviewRequestRepository.save(request);
        }

        if (hasSubmittedRequest) {
            cycle.setStatus(ReviewCycle.ReviewCycleStatus.EXPIRED_PARTIAL);
            if (cycle.getRevealedAt() == null) {
                cycle.setRevealedAt(now);
            }
            reviewCycleRepository.save(cycle);
            processRevealedCycle(cycle);
            log.info("Expired review cycle {} with partial reveal", cycle.getId());
            return;
        }

        cycle.setStatus(ReviewCycle.ReviewCycleStatus.EXPIRED_EMPTY);
        reviewCycleRepository.save(cycle);
        log.info("Expired review cycle {} without any submissions", cycle.getId());
    }

    private void evaluateSubmittedRequest(ReviewRequest request) {
        List<ReviewAnswer> answers = reviewAnswerRepository.findByReviewRequest_IdOrderBySortOrderAsc(request.getId());
        BigDecimal overallScore = averageScore(answers);

        if (request.getReviewCycle().getType() == ReviewCycle.ReviewType.FIT) {
            evaluateFitReview(request, answers, overallScore);
            return;
        }

        if (request.getReviewerRole() == ReviewRequest.ReviewRole.MENTEE
                && overallScore != null
                && overallScore.compareTo(LOW_MENTOR_SCORE_THRESHOLD) < 0) {
            createAlert(
                    "low-mentor-score:" + request.getId(),
                    request,
                    ReviewAlert.ReviewAlertType.LOW_MENTOR_SCORE,
                    ReviewAlert.Severity.HIGH,
                    null,
                    overallScore,
                    null,
                    "Mentee rated mentor below the acceptable threshold"
            );
        }

        if (request.getReviewerRole() == ReviewRequest.ReviewRole.MENTOR
                && overallScore != null
                && overallScore.compareTo(LOW_MENTEE_SCORE_THRESHOLD) < 0) {
            createAlert(
                    "low-mentee-score:" + request.getId(),
                    request,
                    ReviewAlert.ReviewAlertType.LOW_MENTEE_SCORE,
                    ReviewAlert.Severity.MEDIUM,
                    null,
                    overallScore,
                    null,
                    "Mentor rated mentee below the acceptable threshold"
            );
        }

        ReviewAnswer recommendContinue = findAnswer(answers, "recommend_continue");
        if (recommendContinue != null && Boolean.FALSE.equals(recommendContinue.getBooleanAnswer())) {
            createAlert(
                    "do-not-continue:" + request.getId(),
                    request,
                    ReviewAlert.ReviewAlertType.DO_NOT_CONTINUE,
                    ReviewAlert.Severity.HIGH,
                    recommendContinue.getQuestionCode(),
                    null,
                    Boolean.FALSE,
                    messageForContinueAlert(request)
            );
        }
    }

    private void evaluateFitReview(ReviewRequest request, List<ReviewAnswer> answers, BigDecimal overallScore) {
        ReviewAnswer fitScore = findAnswer(answers, "fit_score");
        if (fitScore != null && fitScore.getNumericScore() != null) {
            BigDecimal fitScoreValue = BigDecimal.valueOf(fitScore.getNumericScore()).setScale(2, RoundingMode.HALF_UP);
            if (fitScoreValue.compareTo(LOW_FIT_SCORE_THRESHOLD) <= 0) {
                createAlert(
                        "low-fit-score:" + request.getId(),
                        request,
                        ReviewAlert.ReviewAlertType.LOW_FIT_SCORE,
                        ReviewAlert.Severity.HIGH,
                        fitScore.getQuestionCode(),
                        fitScoreValue,
                        null,
                        "Participant reported a weak mentor relationship fit"
                );
            }
        } else if (overallScore != null && overallScore.compareTo(LOW_FIT_SCORE_THRESHOLD) <= 0) {
            createAlert(
                    "low-fit-score:" + request.getId(),
                    request,
                    ReviewAlert.ReviewAlertType.LOW_FIT_SCORE,
                    ReviewAlert.Severity.HIGH,
                    "fit_score",
                    overallScore,
                    null,
                    "Participant reported a weak mentor relationship fit"
            );
        }

        ReviewAnswer continueAnswer = findAnswer(answers, "want_to_continue_with_same_match");
        if (continueAnswer != null && Boolean.FALSE.equals(continueAnswer.getBooleanAnswer())) {
            createAlert(
                    "do-not-continue-fit:" + request.getId(),
                    request,
                    ReviewAlert.ReviewAlertType.DO_NOT_CONTINUE,
                    ReviewAlert.Severity.HIGH,
                    continueAnswer.getQuestionCode(),
                    null,
                    Boolean.FALSE,
                    "Participant requested a rematch after the fit review"
            );
        }
    }

    private void createAlert(String alertKey,
                             ReviewRequest request,
                             ReviewAlert.ReviewAlertType alertType,
                             ReviewAlert.Severity severity,
                             String questionCode,
                             BigDecimal scoreValue,
                             Boolean booleanValue,
                             String details) {
        if (reviewAlertRepository.findByAlertKey(alertKey).isPresent()) {
            return;
        }

        ReviewCycle cycle = request.getReviewCycle();
        ReviewAlert alert = new ReviewAlert();
        alert.setAlertKey(alertKey);
        alert.setReviewCycle(cycle);
        alert.setReviewRequest(request);
        alert.setCompanyProgram(cycle.getCompanyProgram());
        alert.setParticipant(cycle.getParticipant());
        alert.setMentorAssignment(cycle.getMentorAssignment());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setStatus(ReviewAlert.ReviewAlertStatus.OPEN);
        alert.setQuestionCode(questionCode);
        alert.setScoreValue(scoreValue);
        alert.setBooleanValue(booleanValue);
        alert.setDetails(details);
        reviewAlertRepository.save(alert);
    }

    private ReviewAnswer findAnswer(List<ReviewAnswer> answers, String questionCode) {
        return answers.stream()
                .filter(answer -> questionCode.equals(answer.getQuestionCode()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal averageScore(List<ReviewAnswer> answers) {
        List<Integer> scores = answers.stream()
                .filter(answer -> answer.getAnswerType() == ReviewAnswer.AnswerType.SCORE)
                .map(ReviewAnswer::getNumericScore)
                .filter(score -> score != null)
                .toList();

        if (scores.isEmpty()) {
            return null;
        }

        BigDecimal total = scores.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private String messageForContinueAlert(ReviewRequest request) {
        String reviewer = request.getReviewerRole().name().toLowerCase(Locale.ROOT);
        String target = request.getTargetRole().name().toLowerCase(Locale.ROOT);
        return "Submitted " + reviewer + " review indicates they do not want to continue with the current " + target + " pairing";
    }
}
