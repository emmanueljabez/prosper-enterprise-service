package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyParticipantPulseSummaryDto;
import com.prosper.prospermentor.dto.CompanyProgramPulseSummaryDto;
import com.prosper.prospermentor.dto.ParticipantPulseDto;
import com.prosper.prospermentor.dto.ParticipantPulseSummaryDto;
import com.prosper.prospermentor.dto.SubmitParticipantPulseResponseRequest;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.ParticipantPulse;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.ParticipantPulseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParticipantPulseService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private static final EnumSet<CompanyProgramParticipant.ParticipantStatus> PROGRAM_END_ELIGIBLE_STATUSES = EnumSet.of(
            CompanyProgramParticipant.ParticipantStatus.ENROLLED,
            CompanyProgramParticipant.ParticipantStatus.ACTIVE,
            CompanyProgramParticipant.ParticipantStatus.COMPLETED
    );

    private final ParticipantPulseRepository participantPulseRepository;
    private final CompanyProgramParticipantRepository companyProgramParticipantRepository;

    public void ensureBaselinePulsesForParticipants(Collection<CompanyProgramParticipant> participants) {
        List<CompanyProgramParticipant> eligibleParticipants = participants == null
                ? List.of()
                : participants.stream().filter(Objects::nonNull).toList();

        if (eligibleParticipants.isEmpty()) {
            return;
        }

        List<UUID> participantIds = eligibleParticipants.stream()
                .map(CompanyProgramParticipant::getId)
                .toList();

        Set<UUID> existingParticipantIds = participantPulseRepository.findByParticipant_IdInAndPulseType(
                        participantIds,
                        ParticipantPulse.PulseType.BASELINE
                ).stream()
                .map(pulse -> pulse.getParticipant().getId())
                .collect(Collectors.toSet());

        List<ParticipantPulse> pulsesToSave = eligibleParticipants.stream()
                .filter(participant -> !existingParticipantIds.contains(participant.getId()))
                .map(participant -> newPendingPulse(participant, ParticipantPulse.PulseType.BASELINE))
                .toList();

        if (!pulsesToSave.isEmpty()) {
            participantPulseRepository.saveAll(pulsesToSave);
            log.info("Created {} baseline pulse(s)", pulsesToSave.size());
        }
    }

    public void createProgramEndPulsesForProgram(CompanyProgram companyProgram) {
        if (companyProgram == null || companyProgram.getId() == null) {
            return;
        }

        List<CompanyProgramParticipant> participants = companyProgramParticipantRepository.findByCompanyProgram_IdAndStatusIn(
                companyProgram.getId(),
                PROGRAM_END_ELIGIBLE_STATUSES
        );

        if (participants.isEmpty()) {
            return;
        }

        List<UUID> participantIds = participants.stream().map(CompanyProgramParticipant::getId).toList();
        Set<UUID> existingParticipantIds = participantPulseRepository.findByParticipant_IdInAndPulseType(
                        participantIds,
                        ParticipantPulse.PulseType.PROGRAM_END
                ).stream()
                .map(pulse -> pulse.getParticipant().getId())
                .collect(Collectors.toSet());

        List<ParticipantPulse> pulsesToSave = participants.stream()
                .filter(participant -> !existingParticipantIds.contains(participant.getId()))
                .map(participant -> newPendingPulse(participant, ParticipantPulse.PulseType.PROGRAM_END))
                .toList();

        if (!pulsesToSave.isEmpty()) {
            participantPulseRepository.saveAll(pulsesToSave);
            log.info("Created {} program-end pulse(s) for company program {}", pulsesToSave.size(), companyProgram.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<ParticipantPulseDto> getPulsesForProfile(UUID profileId) {
        return participantPulseRepository.findByParticipant_Profile_IdOrderByCreatedAtDesc(profileId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantPulseDto> getPulsesForParticipant(UUID participantId) {
        return participantPulseRepository.findByParticipant_IdOrderByCreatedAtDesc(participantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParticipantPulseSummaryDto summarizePulses(Collection<ParticipantPulseDto> pulses) {
        List<ParticipantPulseDto> pulseList = pulses == null ? List.of() : pulses.stream().filter(Objects::nonNull).toList();

        long total = pulseList.size();
        long pending = pulseList.stream().filter(pulse -> pulse.getStatus() == ParticipantPulse.PulseStatus.PENDING).count();
        long completed = pulseList.stream().filter(pulse -> pulse.getStatus() == ParticipantPulse.PulseStatus.COMPLETED).count();
        long expired = pulseList.stream().filter(pulse -> pulse.getStatus() == ParticipantPulse.PulseStatus.EXPIRED).count();
        long baselinePending = pulseList.stream()
                .filter(pulse -> pulse.getPulseType() == ParticipantPulse.PulseType.BASELINE
                        && pulse.getStatus() == ParticipantPulse.PulseStatus.PENDING)
                .count();
        long programEndPending = pulseList.stream()
                .filter(pulse -> pulse.getPulseType() == ParticipantPulse.PulseType.PROGRAM_END
                        && pulse.getStatus() == ParticipantPulse.PulseStatus.PENDING)
                .count();

        return ParticipantPulseSummaryDto.builder()
                .totalPulses(total)
                .pendingPulses(pending)
                .completedPulses(completed)
                .expiredPulses(expired)
                .baselinePendingPulses(baselinePending)
                .programEndPendingPulses(programEndPending)
                .completionRate(percentage(completed, total))
                .averageConfidenceScore(averageScore(pulseList, "confidence"))
                .averageSatisfactionScore(averageScore(pulseList, "satisfaction"))
                .averageGoalClarityScore(averageScore(pulseList, "goalClarity"))
                .build();
    }

    @Transactional(readOnly = true)
    public CompanyParticipantPulseSummaryDto getCompanySummary(UUID companyId) {
        return getCompanySummary(companyId, null, null);
    }

    @Transactional(readOnly = true)
    public CompanyParticipantPulseSummaryDto getCompanySummary(UUID companyId,
                                                               LocalDate startDate,
                                                               LocalDate endDate) {
        LocalDate resolvedEndDate = resolveEndDate(startDate, endDate);
        LocalDate resolvedStartDate = resolveStartDate(startDate, resolvedEndDate);
        LocalDateTime rangeStart = resolvedStartDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = resolvedEndDate.plusDays(1).atStartOfDay();

        List<ParticipantPulseDto> pulses = participantPulseRepository.findByCompanyIdWithinCreatedAt(
                        companyId,
                        rangeStart,
                        rangeEndExclusive
                )
                .stream()
                .map(this::toDto)
                .toList();

        ParticipantPulseSummaryDto summary = summarizePulses(pulses);

        Map<UUID, List<ParticipantPulseDto>> pulsesByProgram = pulses.stream()
                .filter(pulse -> pulse.getCompanyProgramId() != null)
                .collect(Collectors.groupingBy(
                        ParticipantPulseDto::getCompanyProgramId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CompanyProgramPulseSummaryDto> programSummaries = pulsesByProgram.entrySet().stream()
                .map(entry -> {
                    List<ParticipantPulseDto> programPulses = entry.getValue();
                    ParticipantPulseSummaryDto programSummary = summarizePulses(programPulses);
                    return CompanyProgramPulseSummaryDto.builder()
                            .companyProgramId(entry.getKey())
                            .companyProgramName(programPulses.stream()
                                    .map(ParticipantPulseDto::getCompanyProgramName)
                                    .filter(name -> name != null && !name.isBlank())
                                    .findFirst()
                                    .orElse("Program"))
                            .totalPulses(programSummary.getTotalPulses())
                            .pendingPulses(programSummary.getPendingPulses())
                            .completedPulses(programSummary.getCompletedPulses())
                            .expiredPulses(programSummary.getExpiredPulses())
                            .baselinePulses(programPulses.stream()
                                    .filter(pulse -> pulse.getPulseType() == ParticipantPulse.PulseType.BASELINE)
                                    .count())
                            .programEndPulses(programPulses.stream()
                                    .filter(pulse -> pulse.getPulseType() == ParticipantPulse.PulseType.PROGRAM_END)
                                    .count())
                            .completionRate(programSummary.getCompletionRate())
                            .build();
                })
                .toList();

        return CompanyParticipantPulseSummaryDto.builder()
                .companyId(companyId)
                .totalPulses(summary.getTotalPulses())
                .pendingPulses(summary.getPendingPulses())
                .completedPulses(summary.getCompletedPulses())
                .expiredPulses(summary.getExpiredPulses())
                .completionRate(summary.getCompletionRate())
                .averageConfidenceScore(summary.getAverageConfidenceScore())
                .averageSatisfactionScore(summary.getAverageSatisfactionScore())
                .averageGoalClarityScore(summary.getAverageGoalClarityScore())
                .programs(programSummaries)
                .build();
    }

    public ParticipantPulseDto submitPulseResponse(UUID pulseId, SubmitParticipantPulseResponseRequest request) {
        ParticipantPulse pulse = participantPulseRepository.findById(pulseId)
                .orElseThrow(() -> new NoSuchElementException("Participant pulse not found"));

        pulse.setConfidenceScore(request.getConfidenceScore());
        pulse.setSatisfactionScore(request.getSatisfactionScore());
        pulse.setGoalClarityScore(request.getGoalClarityScore());
        pulse.setFreeTextFeedback(trimToNull(request.getFreeTextFeedback()));
        pulse.setStatus(ParticipantPulse.PulseStatus.COMPLETED);
        if (pulse.getSentAt() == null) {
            pulse.setSentAt(LocalDateTime.now());
        }
        pulse.setCompletedAt(LocalDateTime.now());
        pulse.setExpiresAt(null);

        ParticipantPulse saved = participantPulseRepository.save(pulse);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ParticipantPulse getPulse(UUID pulseId) {
        return participantPulseRepository.findDetailedById(pulseId)
                .orElseThrow(() -> new NoSuchElementException("Participant pulse not found"));
    }

    private ParticipantPulse newPendingPulse(CompanyProgramParticipant participant,
                                             ParticipantPulse.PulseType pulseType) {
        ParticipantPulse pulse = new ParticipantPulse();
        pulse.setParticipant(participant);
        pulse.setPulseType(pulseType);
        pulse.setStatus(ParticipantPulse.PulseStatus.PENDING);
        pulse.setSentAt(LocalDateTime.now());
        pulse.setExpiresAt(LocalDateTime.now().plusDays(7));
        return pulse;
    }

    private ParticipantPulseDto toDto(ParticipantPulse pulse) {
        CompanyProgramParticipant participant = pulse.getParticipant();
        CompanyProgram companyProgram = participant != null ? participant.getCompanyProgram() : null;

        return ParticipantPulseDto.builder()
                .id(pulse.getId())
                .participantId(participant != null ? participant.getId() : null)
                .companyProgramId(companyProgram != null ? companyProgram.getId() : null)
                .companyProgramName(companyProgram != null ? companyProgram.getName() : null)
                .sessionId(pulse.getSession() != null ? pulse.getSession().getId() : null)
                .pulseType(pulse.getPulseType())
                .status(pulse.getStatus())
                .confidenceScore(pulse.getConfidenceScore())
                .satisfactionScore(pulse.getSatisfactionScore())
                .goalClarityScore(pulse.getGoalClarityScore())
                .freeTextFeedback(pulse.getFreeTextFeedback())
                .sentAt(pulse.getSentAt())
                .expiresAt(pulse.getExpiresAt())
                .completedAt(pulse.getCompletedAt())
                .createdAt(pulse.getCreatedAt())
                .updatedAt(pulse.getUpdatedAt())
                .build();
    }

    private Double averageScore(List<ParticipantPulseDto> pulses, String scoreType) {
        List<Integer> scores = switch (scoreType) {
            case "confidence" -> pulses.stream()
                    .map(ParticipantPulseDto::getConfidenceScore)
                    .filter(Objects::nonNull)
                    .toList();
            case "satisfaction" -> pulses.stream()
                    .map(ParticipantPulseDto::getSatisfactionScore)
                    .filter(Objects::nonNull)
                    .toList();
            case "goalClarity" -> pulses.stream()
                    .map(ParticipantPulseDto::getGoalClarityScore)
                    .filter(Objects::nonNull)
                    .toList();
            default -> List.of();
        };

        if (scores.isEmpty()) {
            return null;
        }

        double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(average * 10.0) / 10.0;
    }

    private Double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((numerator * 100.0) / denominator) * 10.0) / 10.0;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
