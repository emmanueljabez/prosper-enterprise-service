package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramMentorAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProgramMentorAssignmentRepository extends JpaRepository<CompanyProgramMentorAssignment, UUID> {

    boolean existsByParticipant_Id(UUID participantId);
    boolean existsByParticipant_IdAndJourneyInstanceStepIsNull(UUID participantId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    Optional<CompanyProgramMentorAssignment> findByParticipant_Id(UUID participantId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    Optional<CompanyProgramMentorAssignment> findByParticipant_IdAndJourneyInstanceStepIsNull(UUID participantId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    Optional<CompanyProgramMentorAssignment> findByParticipant_IdAndJourneyInstanceStep_Id(UUID participantId,
                                                                                           UUID journeyInstanceStepId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    Optional<CompanyProgramMentorAssignment> findFirstByParticipant_IdAndMentor_IdOrderByAssignedAtDesc(UUID participantId,
                                                                                                        UUID mentorId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_IdIn(Collection<UUID> participantIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_IdInAndJourneyInstanceStepIsNull(Collection<UUID> participantIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_IdInAndJourneyInstanceStep_IdIn(Collection<UUID> participantIds,
                                                                                           Collection<UUID> journeyInstanceStepIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_IdAndJourneyInstanceStep_IdIn(UUID participantId,
                                                                                         Collection<UUID> journeyInstanceStepIds);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_CompanyProgram_Company_Id(UUID companyId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.companyProgram.program",
            "participant.profile",
            "mentor",
            "mentorProfile",
            "journeyInstanceStep"
    })
    List<CompanyProgramMentorAssignment> findByParticipant_Profile_Id(UUID profileId);
}
