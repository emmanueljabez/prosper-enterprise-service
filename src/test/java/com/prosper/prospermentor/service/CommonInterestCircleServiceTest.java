package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CircleSuggestionResultDto;
import com.prosper.prospermentor.dto.CommonInterestCircleDto;
import com.prosper.prospermentor.entity.CommonInterestCircle;
import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.CommonInterestCircleMembershipRepository;
import com.prosper.prospermentor.repository.CommonInterestCircleRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramCohortRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
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
    private ProfileRepository profileRepository;

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
