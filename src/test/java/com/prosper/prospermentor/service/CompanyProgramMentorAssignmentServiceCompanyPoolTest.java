package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompanyMentorDtos;
import com.prosper.prospermentor.dto.CompanyProgramMentorCandidateDto;
import com.prosper.prospermentor.dto.MentorAssignmentSummaryDto;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.CompanyProgramMentorAssignmentRepository;
import com.prosper.prospermentor.repository.CompanyProgramParticipantRepository;
import com.prosper.prospermentor.repository.CompanyProgramRepository;
import com.prosper.prospermentor.repository.MentorProfileRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.ProgramMentorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProgramMentorAssignmentServiceCompanyPoolTest {

    private static final UUID COMPANY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID PROGRAM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_PROGRAM_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PARTICIPANT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID MENTOR_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID ADMIN_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private CompanyProgramMentorAssignmentRepository assignmentRepository;
    @Mock
    private CompanyProgramParticipantRepository participantRepository;
    @Mock
    private CompanyProgramRepository companyProgramRepository;
    @Mock
    private ProgramMentorRepository programMentorRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private CompanyMentorEnrollmentService companyMentorEnrollmentService;

    @InjectMocks
    private CompanyProgramMentorAssignmentService service;

    @Test
    void getMentorCandidates_shouldIncludeCompanyBookablePrivateMentorsForOwningCompany() {
        CompanyProgram companyProgram = companyProgram(PROGRAM_ID, COMPANY_ID);
        when(companyProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(companyProgram));
        when(mentorProfileRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(companyMentorEnrollmentService.eligibleCompanyMentorPoolMembersByMentorId(COMPANY_ID, PROGRAM_ID))
                .thenReturn(Map.of(MENTOR_ID, poolMember()));
        when(profileRepository.findAllById(Set.of(MENTOR_ID))).thenReturn(List.of(mentor()));
        when(mentorProfileRepository.findAllById(Set.of(MENTOR_ID))).thenReturn(List.of(mentorDetails()));

        List<CompanyProgramMentorCandidateDto> candidates = service.getMentorCandidates(PROGRAM_ID, null);

        assertThat(candidates).hasSize(1);
        CompanyProgramMentorCandidateDto candidate = candidates.get(0);
        assertThat(candidate.getMentorId()).isEqualTo(MENTOR_ID);
        assertThat(candidate.getSource()).isEqualTo("COMPANY_POOL");
        assertThat(candidate.getCompanyMentorMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(candidate.getCompanyBookable()).isTrue();
        assertThat(candidate.getVisibilityMode()).isEqualTo("COMPANY_PRIVATE");
        assertThat(candidate.getPublicApprovalStatus()).isEqualTo("NOT_REQUESTED");
    }

    @Test
    void getMentorCandidates_shouldRespectProgramRestrictedCompanyMentorScopes() {
        when(companyProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(companyProgram(PROGRAM_ID, COMPANY_ID)));
        when(companyProgramRepository.findById(OTHER_PROGRAM_ID)).thenReturn(Optional.of(companyProgram(OTHER_PROGRAM_ID, COMPANY_ID)));
        when(mentorProfileRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(companyMentorEnrollmentService.eligibleCompanyMentorPoolMembersByMentorId(COMPANY_ID, PROGRAM_ID))
                .thenReturn(Map.of(MENTOR_ID, poolMember()));
        when(companyMentorEnrollmentService.eligibleCompanyMentorPoolMembersByMentorId(COMPANY_ID, OTHER_PROGRAM_ID))
                .thenReturn(Map.of());
        when(profileRepository.findAllById(Set.of(MENTOR_ID))).thenReturn(List.of(mentor()));
        when(mentorProfileRepository.findAllById(Set.of(MENTOR_ID))).thenReturn(List.of(mentorDetails()));

        assertThat(service.getMentorCandidates(PROGRAM_ID, null)).hasSize(1);
        assertThat(service.getMentorCandidates(OTHER_PROGRAM_ID, null)).isEmpty();
    }

    @Test
    void getMentorCandidates_shouldNotExposeCompanyPrivateMentorsToOtherCompanies() {
        CompanyProgram otherCompanyProgram = companyProgram(PROGRAM_ID, OTHER_COMPANY_ID);
        when(companyProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(otherCompanyProgram));
        when(mentorProfileRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(companyMentorEnrollmentService.eligibleCompanyMentorPoolMembersByMentorId(OTHER_COMPANY_ID, PROGRAM_ID))
                .thenReturn(Map.of());

        assertThat(service.getMentorCandidates(PROGRAM_ID, null)).isEmpty();
    }

    @Test
    void assignMentor_shouldAllowCompanyBookableMentorOutsideRegularProgramPool() {
        CompanyProgram companyProgram = companyProgram(PROGRAM_ID, COMPANY_ID);
        CompanyProgramParticipant participant = participant(companyProgram);
        CompanyProgramMentorAssignment savedAssignment = new CompanyProgramMentorAssignment();
        savedAssignment.setId(UUID.randomUUID());
        savedAssignment.setParticipant(participant);
        savedAssignment.setMentor(mentor());

        when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participant));
        when(mentorProfileRepository.findByIsAvailableTrue()).thenReturn(List.of());
        when(companyMentorEnrollmentService.canCompanyBookMentor(COMPANY_ID, PROGRAM_ID, MENTOR_ID)).thenReturn(true);
        when(profileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentor()));
        when(mentorProfileRepository.findById(MENTOR_ID)).thenReturn(Optional.of(mentorDetails()));
        when(assignmentRepository.findByParticipant_IdAndJourneyInstanceStepIsNull(PARTICIPANT_ID)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(CompanyProgramMentorAssignment.class))).thenReturn(savedAssignment);

        ApiResponse<MentorAssignmentSummaryDto> response = service.assignMentor(PARTICIPANT_ID, MENTOR_ID, ADMIN_ID);

        assertThat(response.isSuccess()).isTrue();
    }

    private CompanyProgram companyProgram(UUID programId, UUID companyId) {
        Company company = new Company();
        company.setId(companyId);
        CompanyProgram companyProgram = new CompanyProgram();
        companyProgram.setId(programId);
        companyProgram.setCompany(company);
        companyProgram.setName("Leadership Program");
        companyProgram.setStatus(CompanyProgram.CompanyProgramStatus.DRAFT);
        return companyProgram;
    }

    private CompanyProgramParticipant participant(CompanyProgram companyProgram) {
        CompanyProgramParticipant participant = new CompanyProgramParticipant();
        participant.setId(PARTICIPANT_ID);
        participant.setCompanyProgram(companyProgram);
        participant.setProfile(mentee());
        participant.setStatus(CompanyProgramParticipant.ParticipantStatus.ACTIVE);
        return participant;
    }

    private Profile mentor() {
        Profile mentor = new Profile();
        mentor.setId(MENTOR_ID);
        mentor.setRole("mentor");
        mentor.setFirstName("Maya");
        mentor.setLastName("Otieno");
        mentor.setEmail("mentor@example.com");
        return mentor;
    }

    private Profile mentee() {
        Profile mentee = new Profile();
        mentee.setId(UUID.randomUUID());
        mentee.setRole("mentee");
        return mentee;
    }

    private MentorProfile mentorDetails() {
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setId(MENTOR_ID);
        mentorProfile.setTitle("Engineering Lead");
        mentorProfile.setIsAvailable(true);
        return mentorProfile;
    }

    private CompanyMentorDtos.PoolMemberDto poolMember() {
        return CompanyMentorDtos.PoolMemberDto.builder()
                .id(MEMBERSHIP_ID)
                .companyId(COMPANY_ID)
                .mentorProfileId(MENTOR_ID)
                .visibilityMode(CompanyMentorPoolMembership.VisibilityMode.COMPANY_PRIVATE)
                .membershipStatus(CompanyMentorPoolMembership.MembershipStatus.ACTIVE)
                .companyBookable(true)
                .publicApprovalStatus(CompanyMentorPoolMembership.PublicApprovalStatus.NOT_REQUESTED)
                .build();
    }
}
