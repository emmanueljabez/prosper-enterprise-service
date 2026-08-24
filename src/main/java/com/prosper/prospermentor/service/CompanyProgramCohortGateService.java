package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyProgramCohortGateService {

    private final CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    private final CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    private final CommonInterestCircleMembershipRepository membershipRepository;

    public CohortGateStatusDto resolveGateStatusForProgramParticipant(UUID companyProgramParticipantId) {
        List<CompanyProgramCohortParticipant> cohortParticipants =
                cohortParticipantRepository.findByCompanyProgramParticipant_Id(companyProgramParticipantId);
        if (cohortParticipants.isEmpty()) {
            return CohortGateStatusDto.builder()
                    .companyProgramParticipantId(companyProgramParticipantId)
                    .confirmed(true)
                    .plenaryAttended(true)
                    .placedInCircle(true)
                    .circlesFinalized(true)
                    .eligibleForMatching(true)
                    .build();
        }

        CompanyProgramCohortParticipant cohortParticipant = cohortParticipants.stream()
                .max(Comparator.comparing(
                        CompanyProgramCohortParticipant::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .orElse(cohortParticipants.get(0));
        return resolveGateStatus(cohortParticipant, companyProgramParticipantId);
    }

    public CohortGateStatusDto resolveGateStatusForCohortParticipant(UUID cohortParticipantId) {
        CompanyProgramCohortParticipant cohortParticipant = cohortParticipantRepository.findById(cohortParticipantId)
                .orElseThrow(() -> new NoSuchElementException("Cohort participant not found"));
        UUID companyProgramParticipantId = cohortParticipant.getCompanyProgramParticipant() != null
                ? cohortParticipant.getCompanyProgramParticipant().getId()
                : null;
        return resolveGateStatus(cohortParticipant, companyProgramParticipantId);
    }

    private CohortGateStatusDto resolveGateStatus(CompanyProgramCohortParticipant participant,
                                                  UUID companyProgramParticipantId) {
        CompanyProgramCohort cohort = participant.getCohort();
        UUID participantId = participant.getId();
        boolean confirmed = isConfirmed(participant);
        boolean duplicateClear = participant.getDuplicateStatus() != CompanyProgramCohortParticipant.DuplicateStatus.POSSIBLE_DUPLICATE;
        boolean plenaryAttended = attendanceRepository.findByCohortParticipant_Id(participantId)
                .map(attendance -> attendance.getStatus() == CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED)
                .orElse(false);
        boolean placedInCircle = isPlacedStatus(participant)
                || membershipRepository.existsByCohortParticipant_IdAndStatus(
                        participantId,
                        CommonInterestCircleMembership.MembershipStatus.PLACED
                );
        boolean circlesFinalized = isCirclesFinalized(cohort);

        String blockedReason = null;
        if (!confirmed) {
            blockedReason = "NOT_CONFIRMED";
        } else if (!duplicateClear) {
            blockedReason = "DUPLICATE_REVIEW";
        } else if (!plenaryAttended) {
            blockedReason = "PLENARY_NOT_ATTENDED";
        } else if (!placedInCircle) {
            blockedReason = "NOT_PLACED_IN_CIRCLE";
        } else if (Boolean.TRUE.equals(cohort != null ? cohort.getMatchingStartsAfterCirclesFinalized() : null)
                && !circlesFinalized) {
            blockedReason = "CIRCLES_NOT_FINALIZED";
        }

        return CohortGateStatusDto.builder()
                .cohortId(cohort != null ? cohort.getId() : null)
                .cohortParticipantId(participantId)
                .companyProgramParticipantId(companyProgramParticipantId)
                .confirmed(confirmed)
                .plenaryAttended(plenaryAttended)
                .placedInCircle(placedInCircle)
                .circlesFinalized(circlesFinalized)
                .eligibleForMatching(blockedReason == null)
                .blockedReason(blockedReason)
                .build();
    }

    private boolean isConfirmed(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING,
                CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED
        ).contains(participant.getStatus());
    }

    private boolean isPlacedStatus(CompanyProgramCohortParticipant participant) {
        return List.of(
                CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING,
                CompanyProgramCohortParticipant.CohortParticipantStatus.MATCHED,
                CompanyProgramCohortParticipant.CohortParticipantStatus.ACTIVE,
                CompanyProgramCohortParticipant.CohortParticipantStatus.COMPLETED
        ).contains(participant.getStatus());
    }

    private boolean isCirclesFinalized(CompanyProgramCohort cohort) {
        return cohort != null && List.of(
                CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED,
                CompanyProgramCohort.CohortStatus.MATCHING,
                CompanyProgramCohort.CohortStatus.ACTIVE,
                CompanyProgramCohort.CohortStatus.COMPLETED
        ).contains(cohort.getStatus());
    }
}
