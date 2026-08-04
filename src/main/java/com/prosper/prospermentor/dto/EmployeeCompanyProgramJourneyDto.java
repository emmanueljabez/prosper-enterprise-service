package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.JourneyInstance;
import com.prosper.prospermentor.entity.JourneyInstanceStep;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionOutcomeActionItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCompanyProgramJourneyDto {
    private UUID participantId;
    private UUID companyProgramId;
    private UUID companyId;
    private String companyName;
    private String programName;
    private String templateProgramName;
    private String catalogJourneySummary;
    private List<CompanyProgramCatalogStageDto> catalogStages;
    private UUID journeyTemplateId;
    private String journeyTemplateName;
    private UUID journeyInstanceId;
    private JourneyInstance.JourneyStatus journeyStatus;
    private CompanyProgram.CompanyProgramStatus programStatus;
    private CompanyProgramParticipant.ParticipantStatus participantStatus;
    private MentorAssignmentSummaryDto mentorAssignment;
    private Integer totalSessions;
    private Integer completedSessions;
    private Integer totalJourneySteps;
    private Integer completedJourneySteps;
    private Integer readyJourneySteps;
    private Integer openActionItemCount;
    private Integer completedActionItemCount;
    private Integer progressPercent;
    private String latestOutcomeSummary;
    private String latestReflectionPrompt;
    private JourneySessionDto nextSession;
    private List<JourneySessionDto> recentSessions;
    private List<JourneyStepProgressDto> steps;
    private List<JourneyActionItemDto> actionItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JourneySessionDto {
        private UUID sessionId;
        private String title;
        private ZonedDateTime scheduledStart;
        private ZonedDateTime scheduledEnd;
        private Session.SessionStatus status;
        private String outcomeSummary;
        private String reflectionPrompt;
        private Integer actionItemCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JourneyActionItemDto {
        private UUID actionItemId;
        private UUID sessionId;
        private String sessionTitle;
        private String description;
        private SessionOutcomeActionItem.ActionItemOwnerType ownerType;
        private ZonedDateTime dueAt;
        private LocalDateTime completedAt;
        private boolean completed;
        private boolean canBeCompletedByEmployee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JourneyStepProgressDto {
        private UUID journeyInstanceStepId;
        private UUID journeyStepId;
        private String stepKey;
        private Integer defaultSequence;
        private String title;
        private String description;
        private JourneyStep.StepType stepType;
        private boolean required;
        private JourneyInstanceStep.StepStatus status;
        private ZonedDateTime dueAt;
        private ZonedDateTime completedAt;
        private String skippedReason;
        private String blockedReason;
        private boolean canBeCompletedByEmployee;
        private MentorAssignmentSummaryDto mentorAssignment;
        private JourneyStepActionDto primaryAction;
    }

    public enum JourneyStepActionType {
        BOOK_SESSION,
        BUY_SESSION,
        COMPLETE_STEP,
        VIEW_MATCHES,
        VIEW_SESSIONS,
        WAIT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JourneyStepActionDto {
        private JourneyStepActionType actionType;
        private String label;
        private String description;
        private boolean enabled;
        private String disabledReason;
        private String targetRoute;
        private UUID journeyInstanceStepId;
        private UUID mentorId;
        private UUID companyProgramId;
        private UUID companyProgramParticipantId;
        private Integer availableSessionBalance;
        private Integer assignedSessionTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateJourneyActionItemRequest {
        private Boolean completed;
    }
}
