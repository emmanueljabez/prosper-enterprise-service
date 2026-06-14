package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import com.prosper.prospermentor.repository.ReviewRequestRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewWorkflowServiceTest {

    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private ReviewRequestRepository reviewRequestRepository;
    @Mock
    private CompanyProgramParticipantRepository companyProgramParticipantRepository;
    @Mock
    private CompanyProgramMentorAssignmentRepository companyProgramMentorAssignmentRepository;
    @Mock
    private CompanyProgramRepository companyProgramRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private NautixWhatsAppService nautixWhatsAppService;

    @InjectMocks
    private ReviewWorkflowService reviewWorkflowService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewWorkflowService, "fitReviewTemplateName", "prosper_fit_review_invite");
        ReflectionTestUtils.setField(reviewWorkflowService, "reviewReminderTemplateName", "prosper_review_reminder");
    }

    @Test
    void maybeOpenFitReviewCycle_shouldCreateFitReviewAfterThirdCompletedSession() {
        UUID participantId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();

        Profile mentor = profile("mentor", "+254700000001");
        Profile mentee = profile("mentee", "+254700000002");
        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(UUID.randomUUID());
        companyProgram.setName("Leadership Pilot");

        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(participantId);
        participant.setProfile(mentee);
        participant.setCompanyProgram(companyProgram);

        CompanyProgramMentorAssignment assignment = new CompanyProgramMentorAssignment();
        assignment.setId(assignmentId);
        assignment.setParticipant(participant);
        assignment.setMentor(mentor);
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(mentor.getId());
        assignment.setMentorProfile(mentorProfile);

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setCompanyProgramParticipantId(participantId);
        session.setMentorId(mentor.getId());
        session.setMenteeId(mentee.getId());
        session.setStatus(Session.SessionStatus.COMPLETED);

        when(companyProgramParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(companyProgramMentorAssignmentRepository.findFirstByParticipant_IdAndMentor_IdOrderByAssignedAtDesc(participantId, mentor.getId()))
                .thenReturn(Optional.of(assignment));
        when(reviewCycleRepository.findByMentorAssignment_IdAndType(assignmentId, ReviewCycle.ReviewType.FIT))
                .thenReturn(Optional.empty());
        when(sessionRepository.countByCompanyProgramParticipantIdAndMentorIdAndStatus(
                participantId, mentor.getId(), Session.SessionStatus.COMPLETED))
                .thenReturn(3L);
        when(reviewCycleRepository.save(any(ReviewCycle.class))).thenAnswer(invocation -> {
            ReviewCycle cycle = invocation.getArgument(0);
            if (cycle.getId() == null) {
                cycle.setId(cycleId);
            }
            return cycle;
        });
        when(reviewRequestRepository.save(any(ReviewRequest.class))).thenAnswer(invocation -> {
            ReviewRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(UUID.randomUUID());
            }
            return request;
        });

        ReviewCycle cycle = reviewWorkflowService.maybeOpenFitReviewCycle(session, mentor, mentee);

        assertThat(cycle).isNotNull();
        assertThat(cycle.getType()).isEqualTo(ReviewCycle.ReviewType.FIT);
        assertThat(cycle.getMentorAssignment()).isEqualTo(assignment);
        verify(reviewRequestRepository, atLeast(2)).save(any(ReviewRequest.class));
        verify(nautixWhatsAppService, times(2)).sendTemplateMessage(
                eq("prosper_fit_review_invite"),
                anyString(),
                anyList(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void sendDueReminders_shouldSendFinalReminderForOpenReviewRequest() {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setType(ReviewCycle.ReviewType.SESSION);
        cycle.setStatus(ReviewCycle.ReviewCycleStatus.OPEN);
        cycle.setOpenedAt(LocalDateTime.now().minusHours(45));
        cycle.setExpiresAt(LocalDateTime.now().plusHours(3));

        ReviewRequest request = new ReviewRequest();
        request.setId(UUID.randomUUID());
        request.setReviewCycle(cycle);
        request.setReviewerProfile(profile("reviewer", "+254700000003"));
        request.setReviewerRole(ReviewRequest.ReviewRole.MENTEE);
        request.setTargetProfile(profile("mentor", "+254700000004"));
        request.setTargetRole(ReviewRequest.ReviewRole.MENTOR);
        request.setStatus(ReviewRequest.ReviewRequestStatus.SENT);
        request.setTemplateName("prosper_mentee_after_session_form");
        request.setFlowToken("flow-token");
        request.setSubmissionToken("submission-token");

        when(reviewRequestRepository.findByStatusInAndReviewCycle_StatusInAndReviewCycle_ExpiresAtAfter(any(), any(), any()))
                .thenReturn(List.of(request));
        when(reviewRequestRepository.save(any(ReviewRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sentCount = reviewWorkflowService.sendDueReminders();

        assertThat(sentCount).isEqualTo(1);

        ArgumentCaptor<List<String>> bodyCaptor = ArgumentCaptor.forClass(List.class);
        verify(nautixWhatsAppService).sendTemplateMessage(
                eq("prosper_review_reminder"),
                eq("+254700000003"),
                bodyCaptor.capture(),
                eq("flow-token"),
                anyMap()
        );
        assertThat(bodyCaptor.getValue()).containsExactly("Mentor User", "about 4 hours");
        verify(reviewRequestRepository).save(argThat(saved ->
                saved.getId().equals(request.getId())
                        && saved.getStatus() == ReviewRequest.ReviewRequestStatus.SENT
                        && saved.getLastReminderAt() != null
        ));
    }

    private Profile profile(String prefix, String phone) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName(capitalize(prefix));
        profile.setLastName("User");
        profile.setUsername(prefix + "-" + UUID.randomUUID());
        profile.setEmail(prefix + "@example.com");
        profile.setPhone(phone);
        profile.setRole(prefix.toUpperCase());
        return profile;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
