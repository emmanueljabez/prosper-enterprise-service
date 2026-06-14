package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.AccessAuditLogDto;
import com.prosper.prospermentor.dto.AccessAuditSummaryDto;
import com.prosper.prospermentor.entity.AccessAuditLog;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.AccessAuditLogRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccessAuditService {

    private final AccessAuditLogRepository accessAuditLogRepository;
    private final ProfileRepository profileRepository;

    public void record(SupabaseUserDetails actor,
                       AccessAuditLog.ResourceType resourceType,
                       AccessAuditLog.ActionType actionType,
                       String reasonCode,
                       UUID resourceId,
                       Company company,
                       CompanyProgram companyProgram,
                       CompanyProgramParticipant participant) {
        AccessAuditLog logEntry = new AccessAuditLog();
        logEntry.setCompany(company);
        logEntry.setCompanyProgram(companyProgram);
        logEntry.setParticipant(participant);
        logEntry.setActorId(actor != null ? actor.getUserIdAsUuid() : null);
        logEntry.setActorRole(actor != null ? actor.getRole() : null);
        logEntry.setResourceType(resourceType);
        logEntry.setResourceId(resourceId);
        logEntry.setAction(actionType);
        logEntry.setReasonCode(reasonCode);
        accessAuditLogRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public Page<AccessAuditLogDto> getCompanyAuditLogs(UUID companyId,
                                                       UUID companyProgramId,
                                                       AccessAuditLog.ResourceType resourceType,
                                                       AccessAuditLog.ActionType actionType,
                                                       Pageable pageable) {
        Page<AccessAuditLog> page = accessAuditLogRepository.findCompanyAuditLogs(
                companyId,
                companyProgramId,
                resourceType,
                actionType,
                pageable
        );

        Map<UUID, Profile> actorProfiles = loadActorProfiles(page.getContent().stream()
                .map(AccessAuditLog::getActorId)
                .filter(java.util.Objects::nonNull)
                .toList());

        return page.map(logEntry -> toDto(logEntry, actorProfiles.get(logEntry.getActorId())));
    }

    @Transactional(readOnly = true)
    public AccessAuditSummaryDto getCompanyAuditSummary(UUID companyId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        long totalEvents = accessAuditLogRepository.countByCompany_Id(companyId);
        long last7DaysEvents = accessAuditLogRepository.countByCompany_IdAndCreatedAtAfter(companyId, threshold);
        long distinctActorsLast7Days = accessAuditLogRepository.countDistinctActorsSince(companyId, threshold);
        long participantRosterViewsLast7Days = accessAuditLogRepository.countByCompany_IdAndResourceTypeAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ResourceType.COMPANY_PROGRAM_PARTICIPANT_ROSTER,
                threshold
        );
        long participantConsentViewsLast7Days = accessAuditLogRepository.countByCompany_IdAndResourceTypeAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ResourceType.PARTICIPANT_CONSENTS,
                threshold
        );
        long participantConsentUpdatesLast7Days = accessAuditLogRepository.countByCompany_IdAndActionAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ActionType.UPDATE,
                threshold
        );
        long reviewAlertViewsLast7Days = accessAuditLogRepository.countByCompany_IdAndResourceTypeAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ResourceType.REVIEW_ALERT_QUEUE,
                threshold
        ) + accessAuditLogRepository.countByCompany_IdAndResourceTypeAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ResourceType.REVIEW_ALERT_SUMMARY,
                threshold
        );
        long rematchActionsLast7Days = accessAuditLogRepository.countByCompany_IdAndActionAndCreatedAtAfter(
                companyId,
                AccessAuditLog.ActionType.REMATCH,
                threshold
        );

        var recentLogs = accessAuditLogRepository.findTop10ByCompany_IdOrderByCreatedAtDesc(companyId);
        Map<UUID, Profile> actorProfiles = loadActorProfiles(recentLogs.stream()
                .map(AccessAuditLog::getActorId)
                .filter(java.util.Objects::nonNull)
                .toList());

        return AccessAuditSummaryDto.builder()
                .companyId(companyId)
                .totalEvents(totalEvents)
                .last7DaysEvents(last7DaysEvents)
                .distinctActorsLast7Days(distinctActorsLast7Days)
                .participantRosterViewsLast7Days(participantRosterViewsLast7Days)
                .participantConsentViewsLast7Days(participantConsentViewsLast7Days)
                .participantConsentUpdatesLast7Days(participantConsentUpdatesLast7Days)
                .reviewAlertViewsLast7Days(reviewAlertViewsLast7Days)
                .rematchActionsLast7Days(rematchActionsLast7Days)
                .recentLogs(recentLogs.stream()
                        .map(logEntry -> toDto(logEntry, actorProfiles.get(logEntry.getActorId())))
                        .toList())
                .build();
    }

    private Map<UUID, Profile> loadActorProfiles(Collection<UUID> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) {
            return Map.of();
        }

        return profileRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));
    }

    private AccessAuditLogDto toDto(AccessAuditLog logEntry, Profile actorProfile) {
        return AccessAuditLogDto.builder()
                .id(logEntry.getId())
                .companyId(logEntry.getCompany() != null ? logEntry.getCompany().getId() : null)
                .companyProgramId(logEntry.getCompanyProgram() != null ? logEntry.getCompanyProgram().getId() : null)
                .companyProgramName(logEntry.getCompanyProgram() != null ? logEntry.getCompanyProgram().getName() : null)
                .participantId(logEntry.getParticipant() != null ? logEntry.getParticipant().getId() : null)
                .participantName(buildParticipantName(logEntry.getParticipant()))
                .actorId(logEntry.getActorId())
                .actorName(buildActorName(actorProfile, logEntry.getActorRole()))
                .actorRole(logEntry.getActorRole())
                .resourceType(logEntry.getResourceType())
                .resourceId(logEntry.getResourceId())
                .action(logEntry.getAction())
                .reasonCode(logEntry.getReasonCode())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }

    private String buildParticipantName(CompanyProgramParticipant participant) {
        if (participant == null || participant.getProfile() == null) {
            return null;
        }

        Profile profile = participant.getProfile();
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
        return profile.getEmail();
    }

    private String buildActorName(Profile actorProfile, String actorRole) {
        if (actorProfile != null) {
            String fullName = String.join(" ",
                    actorProfile.getFirstName() != null ? actorProfile.getFirstName().trim() : "",
                    actorProfile.getLastName() != null ? actorProfile.getLastName().trim() : ""
            ).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
            if (actorProfile.getUsername() != null && !actorProfile.getUsername().isBlank()) {
                return actorProfile.getUsername();
            }
            if (actorProfile.getEmail() != null && !actorProfile.getEmail().isBlank()) {
                return actorProfile.getEmail().toLowerCase(Locale.ROOT);
            }
        }

        return actorRole != null ? actorRole : "Unknown";
    }
}
