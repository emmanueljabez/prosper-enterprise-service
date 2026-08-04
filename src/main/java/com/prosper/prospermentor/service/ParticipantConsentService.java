package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.ParticipantConsentDecisionDto;
import com.prosper.prospermentor.dto.ParticipantConsentSummaryDto;
import com.prosper.prospermentor.dto.ParticipantConsentWorkspaceDto;
import com.prosper.prospermentor.dto.UpdateParticipantConsentRequest;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ConsentRecord;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParticipantConsentService {

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> VISIBLE_EMPLOYEE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final ConsentRecordRepository consentRecordRepository;
    private final CompanyProgramParticipantRepository participantRepository;

    @Transactional(readOnly = true)
    public ParticipantConsentWorkspaceDto getConsentWorkspace(UUID participantId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        Map<ConsentRecord.ConsentType, ConsentRecord> latestConsents = resolveLatestConsents(
                consentRecordRepository.findByParticipant_IdOrderByCapturedAtDesc(participantId)
        );

        return toWorkspace(participant, latestConsents);
    }

    @Transactional(readOnly = true)
    public List<ParticipantConsentWorkspaceDto> getConsentWorkspacesForProfile(UUID profileId) {
        List<CompanyProgramParticipant> participants = participantRepository.findByProfileIdAndStatusIn(profileId, VISIBLE_EMPLOYEE_STATUSES);
        if (participants.isEmpty()) {
            return List.of();
        }

        Map<UUID, Map<ConsentRecord.ConsentType, ConsentRecord>> consentMaps = resolveLatestConsentsByParticipant(
                consentRecordRepository.findByParticipant_IdInOrderByCapturedAtDesc(
                        participants.stream().map(CompanyProgramParticipant::getId).toList()
                )
        );

        return participants.stream()
                .map(participant -> toWorkspace(participant, consentMaps.getOrDefault(participant.getId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, ParticipantConsentSummaryDto> getConsentSummaries(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<ConsentRecord.ConsentType, ConsentRecord>> consentMaps = resolveLatestConsentsByParticipant(
                consentRecordRepository.findByParticipant_IdInOrderByCapturedAtDesc(participantIds)
        );

        Map<UUID, ParticipantConsentSummaryDto> result = new LinkedHashMap<>();
        for (UUID participantId : participantIds) {
            result.put(participantId, buildSummary(participantId, consentMaps.getOrDefault(participantId, Map.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean hasGrantedConsent(UUID participantId, ConsentRecord.ConsentType consentType) {
        if (participantId == null || consentType == null) {
            return false;
        }

        ConsentRecord current = resolveLatestConsents(
                consentRecordRepository.findByParticipant_IdOrderByCapturedAtDesc(participantId)
        ).get(consentType);

        return current != null && current.getStatus() == ConsentRecord.ConsentStatus.GRANTED;
    }

    public ParticipantConsentWorkspaceDto recordConsent(UUID participantId,
                                                        UpdateParticipantConsentRequest request,
                                                        UUID capturedByUserId) {
        CompanyProgramParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NoSuchElementException("Company program participant not found"));

        Map<ConsentRecord.ConsentType, ConsentRecord> latestConsents = resolveLatestConsents(
                consentRecordRepository.findByParticipant_IdOrderByCapturedAtDesc(participantId)
        );

        ConsentRecord existing = latestConsents.get(request.getConsentType());
        if (existing != null
                && existing.getStatus() == request.getStatus()
                && sameExpiry(existing.getExpiresAt(), request.getExpiresAt())) {
            return toWorkspace(participant, latestConsents);
        }

        ConsentRecord record = new ConsentRecord();
        record.setParticipant(participant);
        record.setConsentType(request.getConsentType());
        record.setStatus(request.getStatus());
        record.setCapturedByUserId(capturedByUserId);
        record.setExpiresAt(request.getExpiresAt());
        consentRecordRepository.save(record);

        if (request.getConsentType() == ConsentRecord.ConsentType.PROGRAM_PARTICIPATION) {
            applyProgramParticipationStatus(participant, request.getStatus());
        }

        latestConsents.put(request.getConsentType(), record);
        log.info("Recorded {} consent {} for participant {} by user {}",
                request.getConsentType(), request.getStatus(), participantId, capturedByUserId);

        return toWorkspace(participant, latestConsents);
    }

    private void applyProgramParticipationStatus(CompanyProgramParticipant participant,
                                                 ConsentRecord.ConsentStatus status) {
        if (participant == null || status == null) {
            return;
        }

        if (status == ConsentRecord.ConsentStatus.GRANTED
                && participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ENROLLED) {
            participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
            participantRepository.save(participant);
            return;
        }

        if (status == ConsentRecord.ConsentStatus.REVOKED
                && participant.getStatus() == CompanyProgramParticipant.ParticipantStatus.ACTIVE) {
            participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ENROLLED);
            participantRepository.save(participant);
        }
    }

    private Map<ConsentRecord.ConsentType, ConsentRecord> resolveLatestConsents(List<ConsentRecord> records) {
        Map<ConsentRecord.ConsentType, ConsentRecord> latest = new EnumMap<>(ConsentRecord.ConsentType.class);
        for (ConsentRecord record : records) {
            latest.putIfAbsent(record.getConsentType(), record);
        }
        return latest;
    }

    private Map<UUID, Map<ConsentRecord.ConsentType, ConsentRecord>> resolveLatestConsentsByParticipant(List<ConsentRecord> records) {
        Map<UUID, Map<ConsentRecord.ConsentType, ConsentRecord>> grouped = new LinkedHashMap<>();
        for (ConsentRecord record : records) {
            UUID participantId = record.getParticipant() != null ? record.getParticipant().getId() : null;
            if (participantId == null) {
                continue;
            }

            grouped.computeIfAbsent(participantId, ignored -> new EnumMap<>(ConsentRecord.ConsentType.class))
                    .putIfAbsent(record.getConsentType(), record);
        }
        return grouped;
    }

    private ParticipantConsentWorkspaceDto toWorkspace(CompanyProgramParticipant participant,
                                                       Map<ConsentRecord.ConsentType, ConsentRecord> latestConsents) {
        return ParticipantConsentWorkspaceDto.builder()
                .participantId(participant.getId())
                .companyProgramId(participant.getCompanyProgram() != null ? participant.getCompanyProgram().getId() : null)
                .companyProgramName(participant.getCompanyProgram() != null ? participant.getCompanyProgram().getName() : null)
                .participantStatus(participant.getStatus())
                .summary(buildSummary(participant.getId(), latestConsents))
                .consents(buildDecisionList(latestConsents))
                .build();
    }

    private ParticipantConsentSummaryDto buildSummary(UUID participantId,
                                                      Map<ConsentRecord.ConsentType, ConsentRecord> latestConsents) {
        ConsentRecord programParticipation = latestConsents.get(ConsentRecord.ConsentType.PROGRAM_PARTICIPATION);
        ConsentRecord aggregatedAnalytics = latestConsents.get(ConsentRecord.ConsentType.AGGREGATED_ANALYTICS);
        ConsentRecord employerVisibility = latestConsents.get(ConsentRecord.ConsentType.EMPLOYER_PROGRESS_VISIBILITY);

        List<ConsentRecord> currentRecords = Stream.of(programParticipation, aggregatedAnalytics, employerVisibility)
                .filter(java.util.Objects::nonNull)
                .toList();

        int grantedCount = (int) currentRecords.stream()
                .filter(record -> record.getStatus() == ConsentRecord.ConsentStatus.GRANTED)
                .count();
        int revokedCount = (int) currentRecords.stream()
                .filter(record -> record.getStatus() == ConsentRecord.ConsentStatus.REVOKED)
                .count();
        int pendingCount = ConsentRecord.ConsentType.values().length - currentRecords.size();

        return ParticipantConsentSummaryDto.builder()
                .participantId(participantId)
                .programParticipationStatus(programParticipation != null ? programParticipation.getStatus() : null)
                .aggregatedAnalyticsStatus(aggregatedAnalytics != null ? aggregatedAnalytics.getStatus() : null)
                .employerProgressVisibilityStatus(employerVisibility != null ? employerVisibility.getStatus() : null)
                .grantedCount(grantedCount)
                .revokedCount(revokedCount)
                .pendingCount(Math.max(pendingCount, 0))
                .programParticipationGranted(programParticipation != null && programParticipation.getStatus() == ConsentRecord.ConsentStatus.GRANTED)
                .aggregatedAnalyticsGranted(aggregatedAnalytics != null && aggregatedAnalytics.getStatus() == ConsentRecord.ConsentStatus.GRANTED)
                .employerProgressVisibilityGranted(employerVisibility != null && employerVisibility.getStatus() == ConsentRecord.ConsentStatus.GRANTED)
                .build();
    }

    private List<ParticipantConsentDecisionDto> buildDecisionList(Map<ConsentRecord.ConsentType, ConsentRecord> latestConsents) {
        List<ParticipantConsentDecisionDto> decisions = new ArrayList<>();
        for (ConsentRecord.ConsentType consentType : ConsentRecord.ConsentType.values()) {
            ConsentRecord record = latestConsents.get(consentType);
            decisions.add(ParticipantConsentDecisionDto.builder()
                    .consentType(consentType)
                    .status(record != null ? record.getStatus() : null)
                    .capturedAt(record != null ? record.getCapturedAt() : null)
                    .expiresAt(record != null ? record.getExpiresAt() : null)
                    .pending(record == null)
                    .build());
        }
        return decisions;
    }

    private boolean sameExpiry(java.time.LocalDateTime left, java.time.LocalDateTime right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.isEqual(right);
    }
}
