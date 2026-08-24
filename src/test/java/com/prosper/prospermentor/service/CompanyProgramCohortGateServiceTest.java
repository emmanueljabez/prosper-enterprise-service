package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CohortGateStatusDto;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramCohortGateServiceTest {

    @Mock
    private CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    @Mock
    private CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    @Mock
    private CommonInterestCircleMembershipRepository membershipRepository;

    @InjectMocks
    private CompanyProgramCohortGateService service;

    @Test
    void resolveGateStatus_shouldBlockMatchingBeforePlenaryAttendance() {
        UUID programParticipantId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        CompanyProgramCohortParticipant participant = cohortParticipant(programParticipantId);

        when(cohortParticipantRepository.findByCompanyProgramParticipant_Id(programParticipantId))
                .thenReturn(List.of(participant));
        when(attendanceRepository.findByCohortParticipant_Id(participant.getId())).thenReturn(Optional.empty());

        CohortGateStatusDto status = service.resolveGateStatusForProgramParticipant(programParticipantId);

        assertThat(status.isEligibleForMatching()).isFalse();
        assertThat(status.getBlockedReason()).isEqualTo("PLENARY_NOT_ATTENDED");
    }

    @Test
    void resolveGateStatus_shouldAllowMatchingAfterAttendanceAndCirclePlacement() {
        UUID programParticipantId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        CompanyProgramCohortParticipant participant = cohortParticipant(programParticipantId);
        CompanyProgramCohortPlenaryAttendance attendance = new CompanyProgramCohortPlenaryAttendance();
        attendance.setStatus(CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED);

        when(cohortParticipantRepository.findByCompanyProgramParticipant_Id(programParticipantId))
                .thenReturn(List.of(participant));
        when(attendanceRepository.findByCohortParticipant_Id(participant.getId())).thenReturn(Optional.of(attendance));
        when(membershipRepository.existsByCohortParticipant_IdAndStatus(
                participant.getId(),
                CommonInterestCircleMembership.MembershipStatus.PLACED
        )).thenReturn(true);

        CohortGateStatusDto status = service.resolveGateStatusForProgramParticipant(programParticipantId);

        assertThat(status.isEligibleForMatching()).isTrue();
        assertThat(status.getBlockedReason()).isNull();
    }

    private CompanyProgramCohortParticipant cohortParticipant(UUID companyProgramParticipantId) {
        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        cohort.setStatus(CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED);
        cohort.setMatchingStartsAfterCirclesFinalized(true);

        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        participant.setCohort(cohort);
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        return participant;
    }
}
