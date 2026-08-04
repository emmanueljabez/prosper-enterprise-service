package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.MyReviewStatusDto;
import com.prosper.prospermentor.dto.MyReviewSummaryDto;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ReviewAnswer;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.repository.ReviewAnswerRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyReviewService {

    private final ReviewRequestRepository reviewRequestRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;

    public MyReviewWorkspace getMyReviews(UUID profileId) {
        List<ReviewRequest> requests = reviewRequestRepository.findByReviewerProfile_IdOrderByCreatedAtDesc(profileId);
        if (requests.isEmpty()) {
            return new MyReviewWorkspace(
                    MyReviewSummaryDto.builder()
                            .totalReviews(0)
                            .actionRequired(0)
                            .awaitingReveal(0)
                            .revealed(0)
                            .expired(0)
                            .deliveryIssues(0)
                            .build(),
                    List.of()
            );
        }

        List<UUID> requestIds = requests.stream()
                .map(ReviewRequest::getId)
                .toList();

        Map<UUID, List<ReviewAnswer>> answersByRequest = reviewAnswerRepository.findByReviewRequestIdsOrdered(requestIds)
                .stream()
                .collect(Collectors.groupingBy(answer -> answer.getReviewRequest().getId()));

        List<MyReviewStatusDto> reviews = requests.stream()
                .map(request -> toDto(request, answersByRequest.getOrDefault(request.getId(), Collections.emptyList())))
                .toList();

        MyReviewSummaryDto summary = MyReviewSummaryDto.builder()
                .totalReviews(reviews.size())
                .actionRequired(reviews.stream().filter(MyReviewStatusDto::isActionRequired).count())
                .awaitingReveal(reviews.stream().filter(MyReviewStatusDto::isAwaitingReveal).count())
                .revealed(reviews.stream().filter(MyReviewStatusDto::isRevealed).count())
                .expired(reviews.stream().filter(MyReviewStatusDto::isExpired).count())
                .deliveryIssues(reviews.stream().filter(MyReviewStatusDto::isDeliveryIssue).count())
                .build();

        return new MyReviewWorkspace(summary, reviews);
    }

    private MyReviewStatusDto toDto(ReviewRequest request, List<ReviewAnswer> answers) {
        ReviewCycle cycle = request.getReviewCycle();

        long scoreCount = answers.stream()
                .filter(answer -> answer.getAnswerType() == ReviewAnswer.AnswerType.SCORE && answer.getNumericScore() != null)
                .count();

        double totalScore = answers.stream()
                .filter(answer -> answer.getAnswerType() == ReviewAnswer.AnswerType.SCORE && answer.getNumericScore() != null)
                .mapToInt(ReviewAnswer::getNumericScore)
                .sum();

        Double overallScore = scoreCount > 0 ? totalScore / scoreCount : null;
        Boolean recommendContinue = answers.stream()
                .filter(answer -> "recommend_continue".equals(answer.getQuestionCode()))
                .map(ReviewAnswer::getBooleanAnswer)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        String optionalComment = answers.stream()
                .filter(answer -> "optional_comment".equals(answer.getQuestionCode()))
                .map(ReviewAnswer::getTextAnswer)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);

        boolean actionRequired = request.getStatus() == ReviewRequest.ReviewRequestStatus.PENDING
                || request.getStatus() == ReviewRequest.ReviewRequestStatus.SENT
                || request.getStatus() == ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED;
        boolean awaitingReveal = request.getStatus() == ReviewRequest.ReviewRequestStatus.SUBMITTED
                && cycle != null
                && cycle.getStatus() == ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED;
        boolean revealed = cycle != null && cycle.getStatus() == ReviewCycle.ReviewCycleStatus.REVEALED;
        boolean expired = request.getStatus() == ReviewRequest.ReviewRequestStatus.EXPIRED
                || (cycle != null && (cycle.getStatus() == ReviewCycle.ReviewCycleStatus.EXPIRED_PARTIAL
                || cycle.getStatus() == ReviewCycle.ReviewCycleStatus.EXPIRED_EMPTY));
        boolean deliveryIssue = request.getStatus() == ReviewRequest.ReviewRequestStatus.DELIVERY_FAILED;

        return MyReviewStatusDto.builder()
                .reviewRequestId(request.getId())
                .reviewCycleId(cycle != null ? cycle.getId() : null)
                .companyProgramId(cycle != null && cycle.getCompanyProgram() != null ? cycle.getCompanyProgram().getId() : null)
                .companyProgramName(cycle != null && cycle.getCompanyProgram() != null ? cycle.getCompanyProgram().getName() : null)
                .participantId(cycle != null && cycle.getParticipant() != null ? cycle.getParticipant().getId() : null)
                .sessionId(cycle != null && cycle.getSession() != null ? cycle.getSession().getId() : null)
                .sessionTitle(cycle != null && cycle.getSession() != null ? cycle.getSession().getTitle() : null)
                .sessionScheduledStart(cycle != null && cycle.getSession() != null ? cycle.getSession().getScheduledStart() : null)
                .reviewType(cycle != null ? cycle.getType() : null)
                .reviewerRole(request.getReviewerRole())
                .targetName(formatProfileName(request.getTargetProfile()))
                .requestStatus(request.getStatus())
                .cycleStatus(cycle != null ? cycle.getStatus() : null)
                .sentAt(request.getSentAt())
                .submittedAt(request.getSubmittedAt())
                .expiresAt(cycle != null ? cycle.getExpiresAt() : null)
                .revealedAt(cycle != null ? cycle.getRevealedAt() : null)
                .answeredQuestions((int) answers.stream()
                        .filter(answer -> answer.getAnswerType() != ReviewAnswer.AnswerType.TEXT || StringUtils.hasText(answer.getTextAnswer()))
                        .count())
                .overallScore(overallScore)
                .recommendContinue(recommendContinue)
                .optionalComment(optionalComment)
                .actionRequired(actionRequired)
                .awaitingReveal(awaitingReveal)
                .revealed(revealed)
                .expired(expired)
                .deliveryIssue(deliveryIssue)
                .build();
    }

    private String formatProfileName(Profile profile) {
        if (profile == null) {
            return "Unknown";
        }

        String fullName = ((profile.getFirstName() != null ? profile.getFirstName() : "") + " "
                + (profile.getLastName() != null ? profile.getLastName() : "")).trim();

        if (StringUtils.hasText(fullName)) {
            return fullName;
        }
        if (StringUtils.hasText(profile.getEmail())) {
            return profile.getEmail();
        }
        return profile.getId() != null ? profile.getId().toString() : "Unknown";
    }

    public record MyReviewWorkspace(MyReviewSummaryDto summary, List<MyReviewStatusDto> reviews) {
    }
}
