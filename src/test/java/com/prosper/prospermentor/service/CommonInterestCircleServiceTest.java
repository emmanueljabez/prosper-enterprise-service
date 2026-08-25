package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CircleSuggestionResultDto;
import com.prosper.prospermentor.dto.CommonInterestCircleDto;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.CompanyProgramCohortPlenaryAttendance;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortPlenaryAttendanceRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.service.notification.CompanyProgramCohortNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.prosper.prospermentor.entity.CommonInterestCircleMembership.PlacementSource.ADMIN_PLACED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonInterestCircleServiceTest {

    @Mock
    private CompanyProgramCohortRepository cohortRepository;
    @Mock
    private CompanyProgramCohortParticipantRepository cohortParticipantRepository;
    @Mock
    private CommonInterestCircleRepository circleRepository;
    @Mock
    private CommonInterestCircleMembershipRepository membershipRepository;
    @Mock
    private CompanyProgramCohortPlenaryAttendanceRepository attendanceRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private CompanyProgramCohortNotificationService notificationService;

    @InjectMocks
    private CommonInterestCircleService service;

    @Test
    void suggestCircles_shouldGroupByStrongestInterestTag() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = cohort(cohortId);
        cohort.setCircleMinSize(3);
        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(cohortParticipantRepository.findByCohort_Id(cohortId)).thenReturn(List.of(
                participant("Faith Mwangi", "STEM"),
                participant("Diana Njoki", "Career readiness"),
                participant("Joy Adhiambo", "STEM"),
                participant("Naomi Wafula", "Public speaking"),
                participant("Sarah Kimani", "STEM")
        ));

        CircleSuggestionResultDto result = service.suggestCircles(cohortId);

        assertThat(result.getSuggestedCircles()).anySatisfy(circle -> {
            assertThat(circle.getName()).contains("STEM");
            assertThat(circle.getParticipantNames()).contains("Faith Mwangi", "Joy Adhiambo", "Sarah Kimani");
        });
        assertThat(result.getUnplacedParticipantNames()).contains("Diana Njoki", "Naomi Wafula");
    }

    @Test
    void placeParticipant_shouldRejectFullCircle() {
        UUID circleId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID participantId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        CommonInterestCircle circle = new CommonInterestCircle();
        circle.setId(circleId);
        circle.setMaxSize(5);

        when(circleRepository.findByIdForUpdate(circleId)).thenReturn(Optional.of(circle));
        when(membershipRepository.countByCircle_IdAndStatus(circleId, CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.placeParticipant(circleId, participantId, ADMIN_PLACED, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circle is full");
    }

    @Test
    void finalizeCircles_shouldRejectCircleWithoutFacilitator() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = cohort(cohortId);
        CommonInterestCircle circle = circle(cohort, "STEM Circle");
        circle.setFacilitatorProfile(null);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId)).thenReturn(List.of(circle));

        assertThatThrownBy(() -> service.finalizeCircles(cohortId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("facilitator");
    }

    @Test
    void finalizeCircles_shouldMarkAttendedMembersEligibleForMatching() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = cohort(cohortId);
        CommonInterestCircle circle = circle(cohort, "STEM Circle");
        circle.setMinSize(1);
        circle.setMaxSize(5);
        CompanyProgramCohortParticipant participant = participant("Faith Mwangi", "STEM");
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLENARY_ATTENDED);
        CommonInterestCircleMembership membership = membership(circle, participant);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId)).thenReturn(List.of(circle));
        when(membershipRepository.findByCircle_Cohort_Id(cohortId)).thenReturn(List.of(membership));
        when(membershipRepository.countByCircle_IdAndStatus(circle.getId(), CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(1L);
        when(attendanceRepository.findByCohortParticipant_Id(participant.getId()))
                .thenReturn(Optional.of(attendance(participant)));
        when(circleRepository.save(any(CommonInterestCircle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortRepository.save(any(CompanyProgramCohort.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<CommonInterestCircleDto> finalized = service.finalizeCircles(cohortId, UUID.randomUUID());

        assertThat(cohort.getStatus()).isEqualTo(CompanyProgramCohort.CohortStatus.CIRCLES_FINALIZED);
        assertThat(circle.getStatus()).isEqualTo(CommonInterestCircle.CircleStatus.FINALIZED);
        assertThat(participant.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.ELIGIBLE_FOR_MATCHING);
        assertThat(finalized).hasSize(1);
        verify(cohortParticipantRepository).save(participant);
    }

    @Test
    void placeParticipant_shouldRestoreRemovedMembershipForSameCircle() {
        UUID circleId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID participantId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        CompanyProgramCohort cohort = cohort(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        CommonInterestCircle circle = circle(cohort, "STEM Circle");
        circle.setId(circleId);
        CompanyProgramCohortParticipant participant = participant("Faith Mwangi", "STEM");
        participant.setId(participantId);
        participant.setCohort(cohort);
        CommonInterestCircleMembership removedMembership = membership(circle, participant);
        removedMembership.setStatus(CommonInterestCircleMembership.MembershipStatus.REMOVED);

        when(circleRepository.findByIdForUpdate(circleId)).thenReturn(Optional.of(circle));
        when(membershipRepository.countByCircle_IdAndStatus(circleId, CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(0L);
        when(cohortParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(membershipRepository.findByCohortParticipant_IdAndStatus(participantId, CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(Optional.empty());
        when(membershipRepository.findByCircle_IdAndCohortParticipant_Id(circleId, participantId))
                .thenReturn(Optional.of(removedMembership));
        when(membershipRepository.save(any(CommonInterestCircleMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.placeParticipant(circleId, participantId, ADMIN_PLACED, UUID.randomUUID());

        assertThat(removedMembership.getStatus()).isEqualTo(CommonInterestCircleMembership.MembershipStatus.PLACED);
        verify(membershipRepository).save(removedMembership);
        verify(notificationService).sendCircleAssigned(removedMembership);
    }

    @Test
    void moveMembership_shouldNotifyParticipantAfterCircleMove() {
        UUID membershipId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetCircleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID movedByUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CompanyProgramCohort cohort = cohort(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        CompanyProgramCohortParticipant participant = participant("Faith Mwangi", "STEM");
        participant.setCohort(cohort);
        CommonInterestCircle sourceCircle = circle(cohort, "STEM Circle");
        CommonInterestCircle targetCircle = circle(cohort, "Leadership Lab");
        targetCircle.setId(targetCircleId);
        CommonInterestCircleMembership membership = membership(sourceCircle, participant);
        membership.setId(membershipId);

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));
        when(circleRepository.findByIdForUpdate(targetCircleId)).thenReturn(Optional.of(targetCircle));
        when(membershipRepository.countByCircle_IdAndStatus(targetCircleId, CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(0L);
        when(membershipRepository.save(any(CommonInterestCircleMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceRepository.findByCohortParticipant_Id(participant.getId())).thenReturn(Optional.empty());
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.findByCircle_Cohort_Id(cohort.getId())).thenReturn(List.of(membership));

        CommonInterestCircleDto response = service.moveMembership(membershipId, targetCircleId, movedByUserId);

        assertThat(response.getId()).isEqualTo(targetCircleId);
        assertThat(membership.getCircle()).isEqualTo(targetCircle);
        assertThat(membership.getPlacementSource()).isEqualTo(CommonInterestCircleMembership.PlacementSource.ADMIN_MOVED);
        verify(notificationService).sendCircleAssigned(membership);
    }

    @Test
    void finalizeCircles_shouldNotMarkPlacedOnlyMemberEligibleForMatching() {
        UUID cohortId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CompanyProgramCohort cohort = cohort(cohortId);
        CommonInterestCircle circle = circle(cohort, "STEM Circle");
        circle.setMinSize(1);
        circle.setMaxSize(5);
        CompanyProgramCohortParticipant participant = participant("Faith Mwangi", "STEM");
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE);
        CommonInterestCircleMembership membership = membership(circle, participant);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(circleRepository.findByCohort_IdOrderByCreatedAtAsc(cohortId)).thenReturn(List.of(circle));
        when(membershipRepository.findByCircle_Cohort_Id(cohortId)).thenReturn(List.of(membership));
        when(membershipRepository.countByCircle_IdAndStatus(circle.getId(), CommonInterestCircleMembership.MembershipStatus.PLACED))
                .thenReturn(1L);
        when(attendanceRepository.findByCohortParticipant_Id(participant.getId())).thenReturn(Optional.empty());
        when(circleRepository.save(any(CommonInterestCircle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortRepository.save(any(CompanyProgramCohort.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cohortParticipantRepository.save(any(CompanyProgramCohortParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.finalizeCircles(cohortId, UUID.randomUUID());

        assertThat(participant.getStatus()).isEqualTo(CompanyProgramCohortParticipant.CohortParticipantStatus.PLACED_IN_CIRCLE);
    }

    private CompanyProgramCohort cohort(UUID cohortId) {
        CompanyProgramCohort cohort = new CompanyProgramCohort();
        cohort.setId(cohortId);
        cohort.setName("G4G Nairobi - Q3 2026");
        cohort.setCode("G4G-NBO-Q3-2026");
        cohort.setCircleMinSize(5);
        cohort.setCircleMaxSize(10);
        cohort.setMatchingStartsAfterCirclesFinalized(true);
        return cohort;
    }

    private CommonInterestCircle circle(CompanyProgramCohort cohort, String name) {
        CommonInterestCircle circle = new CommonInterestCircle();
        circle.setId(UUID.randomUUID());
        circle.setCohort(cohort);
        circle.setName(name);
        circle.setMinSize(5);
        circle.setMaxSize(10);
        Profile facilitator = new Profile();
        facilitator.setId(UUID.fromString("88888888-8888-8888-8888-888888888888"));
        facilitator.setFirstName("Joyce");
        facilitator.setLastName("Kariuki");
        circle.setFacilitatorProfile(facilitator);
        return circle;
    }

    private CommonInterestCircleMembership membership(CommonInterestCircle circle,
                                                      CompanyProgramCohortParticipant participant) {
        CommonInterestCircleMembership membership = new CommonInterestCircleMembership();
        membership.setId(UUID.randomUUID());
        membership.setCircle(circle);
        membership.setCohortParticipant(participant);
        membership.setStatus(CommonInterestCircleMembership.MembershipStatus.PLACED);
        return membership;
    }

    private CompanyProgramCohortPlenaryAttendance attendance(CompanyProgramCohortParticipant participant) {
        CompanyProgramCohortPlenaryAttendance attendance = new CompanyProgramCohortPlenaryAttendance();
        attendance.setCohortParticipant(participant);
        attendance.setStatus(CompanyProgramCohortPlenaryAttendance.AttendanceStatus.ATTENDED);
        return attendance;
    }

    private CompanyProgramCohortParticipant participant(String name, String interestTag) {
        String[] parts = name.split(" ", 2);
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(parts[0].toLowerCase() + "@example.com");
        profile.setFirstName(parts[0]);
        profile.setLastName(parts.length > 1 ? parts[1] : null);

        CompanyProgramCohortParticipant participant = new CompanyProgramCohortParticipant();
        participant.setId(UUID.randomUUID());
        participant.setProfile(profile);
        participant.setStatus(CompanyProgramCohortParticipant.CohortParticipantStatus.CONFIRMED);
        participant.setInterestTags(List.of(interestTag));
        return participant;
    }
}
