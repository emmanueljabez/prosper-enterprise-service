package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.ReviewAlertAdminDto;
import com.prosper.prospermentor.dto.ReviewAlertRematchResultDto;
import com.prosper.prospermentor.dto.ReviewAlertSummaryDto;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.ReviewAlertRepository;
import com.prosper.prospermentor.repository.ReviewCycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReviewAlertAdminService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private static final EnumSet<ReviewCycle.ReviewCycleStatus> REVEALED_CYCLE_STATUSES = EnumSet.of(
            ReviewCycle.ReviewCycleStatus.REVEALED,
            ReviewCycle.ReviewCycleStatus.EXPIRED_PARTIAL
    );

    private static final EnumSet<ReviewCycle.ReviewCycleStatus> PENDING_CYCLE_STATUSES = EnumSet.of(
            ReviewCycle.ReviewCycleStatus.OPEN,
            ReviewCycle.ReviewCycleStatus.PARTIALLY_SUBMITTED
    );

    private final ReviewAlertRepository reviewAlertRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final CompanyProgramMentorAssignmentService mentorAssignmentService;

    @Transactional(readOnly = true)
    public Page<ReviewAlertAdminDto> getCompanyAlerts(UUID companyId,
                                                      UUID companyProgramId,
                                                      ReviewAlert.ReviewAlertStatus status,
                                                      ReviewAlert.Severity severity,
                                                      ReviewAlert.ReviewAlertType alertType,
                                                      Pageable pageable) {
        return reviewAlertRepository.findCompanyAlerts(companyId, companyProgramId, status, severity, alertType, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ReviewAlertSummaryDto getCompanyAlertSummary(UUID companyId, UUID companyProgramId) {
        return getCompanyAlertSummary(companyId, companyProgramId, null, null);
    }

    @Transactional(readOnly = true)
    public ReviewAlertSummaryDto getCompanyAlertSummary(UUID companyId,
                                                        UUID companyProgramId,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        LocalDate resolvedEndDate = resolveEndDate(startDate, endDate);
        LocalDate resolvedStartDate = resolveStartDate(startDate, resolvedEndDate);
        LocalDateTime rangeStart = resolvedStartDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = resolvedEndDate.plusDays(1).atStartOfDay();

        List<ReviewAlert> alerts = reviewAlertRepository.findCompanyAlertsForSummary(
                companyId,
                companyProgramId,
                rangeStart,
                rangeEndExclusive
        );

        List<ReviewCycle> cycles = reviewCycleRepository.findCompanyCyclesForSummary(
                companyId,
                companyProgramId,
                rangeStart,
                rangeEndExclusive
        );

        return ReviewAlertSummaryDto.builder()
                .companyId(companyId)
                .companyProgramId(companyProgramId)
                .totalReviewCycles(cycles.size())
                .revealedReviewCycles(cycles.stream().filter(cycle -> REVEALED_CYCLE_STATUSES.contains(cycle.getStatus())).count())
                .pendingReviewCycles(cycles.stream().filter(cycle -> PENDING_CYCLE_STATUSES.contains(cycle.getStatus())).count())
                .totalAlerts(alerts.size())
                .openAlerts(alerts.stream().filter(alert -> alert.getStatus() == ReviewAlert.ReviewAlertStatus.OPEN).count())
                .acknowledgedAlerts(alerts.stream().filter(alert -> alert.getStatus() == ReviewAlert.ReviewAlertStatus.ACKNOWLEDGED).count())
                .resolvedAlerts(alerts.stream().filter(alert -> alert.getStatus() == ReviewAlert.ReviewAlertStatus.RESOLVED).count())
                .highSeverityAlerts(alerts.stream().filter(alert -> alert.getSeverity() == ReviewAlert.Severity.HIGH).count())
                .lowMentorScoreAlerts(countAlertsByType(alerts, ReviewAlert.ReviewAlertType.LOW_MENTOR_SCORE))
                .lowMenteeScoreAlerts(countAlertsByType(alerts, ReviewAlert.ReviewAlertType.LOW_MENTEE_SCORE))
                .lowFitAlerts(countAlertsByType(alerts, ReviewAlert.ReviewAlertType.LOW_FIT_SCORE))
                .doNotContinueAlerts(countAlertsByType(alerts, ReviewAlert.ReviewAlertType.DO_NOT_CONTINUE))
                .rematchRecommendedAlerts(alerts.stream()
                        .filter(alert -> alert.getStatus() != ReviewAlert.ReviewAlertStatus.RESOLVED)
                        .filter(alert -> alert.getAlertType() == ReviewAlert.ReviewAlertType.DO_NOT_CONTINUE
                                || alert.getAlertType() == ReviewAlert.ReviewAlertType.LOW_FIT_SCORE)
                        .count())
                .recentAlerts(alerts.stream().limit(5).map(this::toDto).toList())
                .build();
    }

    public ApiResponse<ReviewAlertAdminDto> updateAlertStatus(UUID companyId,
                                                              UUID alertId,
                                                              ReviewAlert.ReviewAlertStatus status) {
        ReviewAlert alert = reviewAlertRepository.findByIdAndCompanyProgram_Company_Id(alertId, companyId)
                .orElseThrow(() -> new NoSuchElementException("Review alert not found"));

        alert.setStatus(status);
        ReviewAlert saved = reviewAlertRepository.save(alert);
        return ApiResponse.success("Review alert updated successfully", toDto(saved));
    }

    public ApiResponse<ReviewAlertRematchResultDto> triggerRematch(UUID companyId, UUID alertId, UUID actingUserId) {
        ReviewAlert alert = reviewAlertRepository.findByIdAndCompanyProgram_Company_Id(alertId, companyId)
                .orElseThrow(() -> new NoSuchElementException("Review alert not found"));

        CompanyProgramParticipant participant = alert.getParticipant();
        if (participant == null) {
            return ApiResponse.error("Alert is not linked to a participant");
        }

        CompanyProgramMentorAssignment assignment = alert.getMentorAssignment();
        if (assignment == null) {
            return ApiResponse.error("Alert does not have an active mentor assignment to rematch");
        }

        UUID participantId = participant.getId();
        UUID companyProgramId = participant.getCompanyProgram() != null ? participant.getCompanyProgram().getId() : null;
        UUID previousMentorId = assignment.getMentor() != null ? assignment.getMentor().getId() : null;
        String previousMentorName = assignment.getMentor() != null ? buildProfileName(assignment.getMentor()) : "Mentor";

        ApiResponse<Void> removeResponse = mentorAssignmentService.removeMentorAssignment(participantId);
        if (!removeResponse.isSuccess()) {
            return ApiResponse.error(removeResponse.getMessage());
        }

        List<ReviewAlert> relatedAlerts = reviewAlertRepository.findByParticipant_IdAndStatusIn(
                participantId,
                List.of(ReviewAlert.ReviewAlertStatus.OPEN, ReviewAlert.ReviewAlertStatus.ACKNOWLEDGED)
        );
        relatedAlerts.forEach(relatedAlert -> relatedAlert.setStatus(ReviewAlert.ReviewAlertStatus.RESOLVED));
        reviewAlertRepository.saveAll(relatedAlerts);

        log.info("Triggered rematch for participant {} from alert {} by user {}",
                participantId, alertId, actingUserId);

        return ApiResponse.success("Mentor assignment removed. Reassign a new mentor from the matches workspace.",
                ReviewAlertRematchResultDto.builder()
                        .alertId(alertId)
                        .companyProgramId(companyProgramId)
                        .participantId(participantId)
                        .previousMentorId(previousMentorId)
                        .previousMentorName(previousMentorName)
                        .resolvedAlertCount(relatedAlerts.size())
                        .mentorAssignmentRemoved(true)
                        .build());
    }

    private long countAlertsByType(List<ReviewAlert> alerts, ReviewAlert.ReviewAlertType type) {
        return alerts.stream().filter(alert -> alert.getAlertType() == type).count();
    }

    private ReviewAlertAdminDto toDto(ReviewAlert alert) {
        CompanyProgramParticipant participant = alert.getParticipant();
        CompanyProgramMentorAssignment mentorAssignment = alert.getMentorAssignment();
        Profile participantProfile = participant != null ? participant.getProfile() : null;
        Profile mentorProfile = mentorAssignment != null ? mentorAssignment.getMentor() : null;

        return ReviewAlertAdminDto.builder()
                .id(alert.getId())
                .reviewCycleId(alert.getReviewCycle() != null ? alert.getReviewCycle().getId() : null)
                .reviewType(alert.getReviewCycle() != null ? alert.getReviewCycle().getType() : null)
                .reviewRequestId(alert.getReviewRequest() != null ? alert.getReviewRequest().getId() : null)
                .reviewerRole(alert.getReviewRequest() != null ? alert.getReviewRequest().getReviewerRole() : null)
                .targetRole(alert.getReviewRequest() != null ? alert.getReviewRequest().getTargetRole() : null)
                .companyProgramId(alert.getCompanyProgram() != null ? alert.getCompanyProgram().getId() : null)
                .companyProgramName(alert.getCompanyProgram() != null ? alert.getCompanyProgram().getName() : null)
                .participantId(participant != null ? participant.getId() : null)
                .participantName(buildProfileName(participantProfile))
                .participantEmail(participantProfile != null ? participantProfile.getEmail() : null)
                .participantStatus(participant != null && participant.getStatus() != null ? participant.getStatus().name() : null)
                .mentorAssignmentId(mentorAssignment != null ? mentorAssignment.getId() : null)
                .mentorId(mentorProfile != null ? mentorProfile.getId() : null)
                .mentorName(buildProfileName(mentorProfile))
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .questionCode(alert.getQuestionCode())
                .scoreValue(alert.getScoreValue())
                .booleanValue(alert.getBooleanValue())
                .details(alert.getDetails())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    private String buildProfileName(Profile profile) {
        if (profile == null) {
            return null;
        }

        String fullName = String.join(" ",
                profile.getFirstName() != null ? profile.getFirstName().trim() : "",
                profile.getLastName() != null ? profile.getLastName().trim() : ""
        ).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail().toLowerCase(Locale.ROOT);
        }
        return "Unknown";
    }

    private LocalDate resolveEndDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null) {
            return endDate;
        }
        if (startDate != null) {
            return startDate.plusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        return LocalDate.now(ZoneId.systemDefault());
    }

    private LocalDate resolveStartDate(LocalDate startDate, LocalDate resolvedEndDate) {
        LocalDate resolvedStartDate = startDate != null
                ? startDate
                : resolvedEndDate.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (resolvedEndDate.isBefore(resolvedStartDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        return resolvedStartDate;
    }
}
