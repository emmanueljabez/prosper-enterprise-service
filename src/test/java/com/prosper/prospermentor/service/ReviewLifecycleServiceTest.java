package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.ReviewAnswerRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewLifecycleServiceTest {

    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private ReviewRequestRepository reviewRequestRepository;
    @Mock
    private ReviewAnswerRepository reviewAnswerRepository;
    @Mock
    private ReviewAlertRepository reviewAlertRepository;

    @InjectMocks
    private ReviewLifecycleService reviewLifecycleService;

    @Test
    void processRevealedCycle_shouldCreateLowMentorAndDoNotContinueAlerts() {
        ReviewCycle cycle = reviewCycle(UUID.randomUUID(), ReviewCycle.ReviewType.SESSION);
        ReviewRequest request = reviewRequest(cycle, ReviewRequest.ReviewRole.MENTEE, ReviewRequest.ReviewRole.MENTOR);
        request.setStatus(ReviewRequest.ReviewRequestStatus.SUBMITTED);

        when(reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId()))
                .thenReturn(List.of(request));
        when(reviewAnswerRepository.findByReviewRequest_IdOrderBySortOrderAsc(request.getId()))
                .thenReturn(List.of(
                        scoreAnswer(request, "quality_of_guidance", 3, 0),
                        scoreAnswer(request, "listened_and_adapted", 4, 1),
                        scoreAnswer(request, "presence_and_punctuality", 3, 2),
                        scoreAnswer(request, "knowledge_generosity", 4, 3),
                        scoreAnswer(request, "space_for_my_insights", 3, 4),
                        booleanAnswer(request, "recommend_continue", false, 5)
                ));
        when(reviewAlertRepository.findByAlertKey(any())).thenReturn(Optional.empty());

        reviewLifecycleService.processRevealedCycle(cycle);

        ArgumentCaptor<ReviewAlert> captor = ArgumentCaptor.forClass(ReviewAlert.class);
        verify(reviewAlertRepository, times(2)).save(captor.capture());

        List<ReviewAlert> alerts = captor.getAllValues();
        assertThat(alerts)
                .extracting(ReviewAlert::getAlertType)
                .containsExactlyInAnyOrder(
                        ReviewAlert.ReviewAlertType.LOW_MENTOR_SCORE,
                        ReviewAlert.ReviewAlertType.DO_NOT_CONTINUE
                );
        assertThat(alerts)
                .filteredOn(alert -> alert.getAlertType() == ReviewAlert.ReviewAlertType.LOW_MENTOR_SCORE)
                .singleElement()
                .extracting(ReviewAlert::getScoreValue)
                .isEqualTo(new BigDecimal("3.40"));
    }

    @Test
    void expireOverdueCycles_shouldExpireMissingRequestsAndRevealPartialCycle() {
        ReviewCycle cycle = reviewCycle(UUID.randomUUID(), ReviewCycle.ReviewType.SESSION);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED);
        cycle.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        ReviewRequest submittedRequest = reviewRequest(cycle, ReviewRequest.ReviewRole.MENTEE, ReviewRequest.ReviewRole.MENTOR);
        submittedRequest.setStatus(ReviewRequest.ReviewRequestStatus.SUBMITTED);

        ReviewRequest pendingRequest = reviewRequest(cycle, ReviewRequest.ReviewRole.MENTOR, ReviewRequest.ReviewRole.MENTEE);
        pendingRequest.setStatus(ReviewRequest.ReviewRequestStatus.SENT);

        when(reviewCycleRepository.findByStatusInAndExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(cycle));
        when(reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId()))
                .thenReturn(List.of(submittedRequest, pendingRequest));
        when(reviewAnswerRepository.findByReviewRequest_IdOrderBySortOrderAsc(submittedRequest.getId()))
                .thenReturn(List.of(
                        scoreAnswer(submittedRequest, "quality_of_guidance", 5, 0),
                        scoreAnswer(submittedRequest, "listened_and_adapted", 5, 1),
                        booleanAnswer(submittedRequest, "recommend_continue", true, 2)
                ));

        int expiredCount = reviewLifecycleService.expireOverdueCycles();

        assertThat(expiredCount).isEqualTo(1);
        verify(reviewRequestRepository).save(argThat(request ->
                request.getId().equals(pendingRequest.getId())
                        && request.getStatus() == ReviewRequest.ReviewRequestStatus.EXPIRED
                        && "Review window closed before submission".equals(request.getLastError())
        ));
        verify(reviewCycleRepository, atLeastOnce()).save(argThat(savedCycle ->
                savedCycle.getId().equals(cycle.getId())
                        && savedCycle.getStatus() == ReviewCycle.ReviewCycleStatus.EXPIRED_PARTIAL
                        && savedCycle.getRevealedAt() != null
        ));
    }

    private ReviewCycle reviewCycle(UUID id, ReviewCycle.ReviewType type) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(id);
        cycle.setType(type);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.REVEALED);
        cycle.setOpenedAt(LocalDateTime.now().minusHours(48));
        cycle.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        cycle.setMentorProfile(profile());
        cycle.setMenteeProfile(profile());
        return cycle;
    }

    private ReviewRequest reviewRequest(ReviewCycle cycle,
                                        ReviewRequest.ReviewRole reviewerRole,
                                        ReviewRequest.ReviewRole targetRole) {
        ReviewRequest request = new ReviewRequest();
        request.setId(UUID.randomUUID());
        request.setReviewCycle(cycle);
        request.setReviewerProfile(profile());
        request.setReviewerRole(reviewerRole);
        request.setTargetProfile(profile());
        request.setTargetRole(targetRole);
        request.setTemplateName("template");
        return request;
    }

    private ReviewAnswer scoreAnswer(ReviewRequest request, String code, int score, int sortOrder) {
        ReviewAnswer answer = new ReviewAnswer();
        answer.setReviewRequest(request);
        answer.setQuestionCode(code);
        answer.setAnswerType(ReviewAnswer.AnswerType.SCORE);
        answer.setNumericScore(score);
        answer.setSortOrder(sortOrder);
        return answer;
    }

    private ReviewAnswer booleanAnswer(ReviewRequest request, String code, boolean value, int sortOrder) {
        ReviewAnswer answer = new ReviewAnswer();
        answer.setReviewRequest(request);
        answer.setQuestionCode(code);
        answer.setAnswerType(ReviewAnswer.AnswerType.BOOLEAN);
        answer.setBooleanAnswer(value);
        answer.setSortOrder(sortOrder);
        return answer;
    }

    private Profile profile() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@example.com");
        profile.setUsername("user-" + UUID.randomUUID());
        profile.setRole("MENTEE");
        return profile;
    }
}
