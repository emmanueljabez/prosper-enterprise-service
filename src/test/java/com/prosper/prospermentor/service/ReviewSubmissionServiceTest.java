package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ReviewAnswer;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.repository.ReviewAnswerRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ReviewRequestRepository reviewRequestRepository;
    @Mock
    private ReviewAnswerRepository reviewAnswerRepository;
    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private ReviewLifecycleService reviewLifecycleService;

    @InjectMocks
    private ReviewSubmissionService reviewSubmissionService;

    @Test
    void submitFlowReview_shouldAcceptUppercaseSnakeCaseFields() {
        ReviewCycle cycle = reviewCycle();
        ReviewRequest request = reviewRequest(cycle);

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("REVIEW_REQUEST_ID", request.getId().toString());
        payload.put("REVIEW_TOKEN", "review-secret");
        payload.put("QUALITY_OF_GUIDANCE", "1");
        payload.put("LISTENED_AND_ADAPTED", "3");
        payload.put("PRESENCE_AND_PUNCTUALITY", "1");
        payload.put("KNOWLEDGE_GENEROSITY", "2");
        payload.put("SPACE_FOR_MY_INSIGHTS", "1");
        payload.put("RECOMMEND_CONTINUE", "2");
        payload.put("OPTIONAL_COMMENT", "Helpful session");

        when(reviewRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(reviewRequestRepository.findByReviewCycle_IdOrderByCreatedAtAsc(cycle.getId())).thenReturn(List.of(request));
        when(reviewRequestRepository.save(any(ReviewRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewCycleRepository.save(any(ReviewCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewAnswerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewSubmissionService.ReviewSubmissionResult result = reviewSubmissionService.submitFlowReview(payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewAnswerRepository).saveAll(answersCaptor.capture());

        List<ReviewAnswer> answers = answersCaptor.getValue();
        assertThat(answers).hasSize(7);
        assertThat(answers)
                .extracting(ReviewAnswer::getQuestionCode)
                .containsExactly(
                        "quality_of_guidance",
                        "listened_and_adapted",
                        "presence_and_punctuality",
                        "knowledge_generosity",
                        "space_for_my_insights",
                        "recommend_continue",
                        "optional_comment"
                );
        assertThat(answers)
                .filteredOn(answer -> answer.getQuestionCode().equals("quality_of_guidance"))
                .singleElement()
                .extracting(ReviewAnswer::getNumericScore)
                .isEqualTo(1);
        assertThat(answers)
                .filteredOn(answer -> answer.getQuestionCode().equals("recommend_continue"))
                .singleElement()
                .extracting(ReviewAnswer::getBooleanAnswer)
                .isEqualTo(false);
        assertThat(answers)
                .filteredOn(answer -> answer.getQuestionCode().equals("optional_comment"))
                .singleElement()
                .extracting(ReviewAnswer::getTextAnswer)
                .isEqualTo("Helpful session");

        assertThat(result.requestStatus()).isEqualTo(ReviewRequest.ReviewRequestStatus.SUBMITTED.name());
        assertThat(result.cycleStatus()).isEqualTo(ReviewCycle.ReviewCycleStatus.REVEALED.name());
        assertThat(result.revealed()).isTrue();

        verify(reviewLifecycleService).processRevealedCycle(cycle);
    }

    private ReviewCycle reviewCycle() {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setType(ReviewCycle.ReviewType.SESSION);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.OPEN);
        cycle.setOpenedAt(LocalDateTime.now().minusMinutes(5));
        cycle.setExpiresAt(LocalDateTime.now().plusHours(48));
        cycle.setMentorProfile(profile("mentor"));
        cycle.setMenteeProfile(profile("mentee"));
        return cycle;
    }

    private ReviewRequest reviewRequest(ReviewCycle cycle) {
        ReviewRequest request = new ReviewRequest();
        request.setId(UUID.randomUUID());
        request.setReviewCycle(cycle);
        request.setReviewerProfile(profile("reviewer"));
        request.setReviewerRole(ReviewRequest.ReviewRole.MENTEE);
        request.setTargetProfile(profile("target"));
        request.setTargetRole(ReviewRequest.ReviewRole.MENTOR);
        request.setTemplateName("prosper_mentee_after_session_form");
        request.setSubmissionToken("review-secret");
        request.setStatus(ReviewRequest.ReviewRequestStatus.SENT);
        return request;
    }

    private Profile profile(String prefix) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(prefix + "@example.com");
        profile.setUsername(prefix + "-" + UUID.randomUUID());
        profile.setRole(prefix.toUpperCase());
        return profile;
    }
}
