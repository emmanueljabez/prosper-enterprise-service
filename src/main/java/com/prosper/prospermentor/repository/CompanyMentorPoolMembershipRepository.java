package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyMentorPoolMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyMentorPoolMembershipRepository extends JpaRepository<CompanyMentorPoolMembership, UUID> {

    List<CompanyMentorPoolMembership> findByCompany_IdAndMembershipStatusIn(
            UUID companyId,
            Collection<CompanyMentorPoolMembership.MembershipStatus> statuses
    );

    Optional<CompanyMentorPoolMembership> findByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
            UUID companyId,
            UUID mentorProfileId,
            Collection<CompanyMentorPoolMembership.MembershipStatus> statuses
    );

    List<CompanyMentorPoolMembership> findByMentorProfile_IdAndMembershipStatusIn(
            UUID mentorProfileId,
            Collection<CompanyMentorPoolMembership.MembershipStatus> statuses
    );

    boolean existsByCompany_IdAndMentorProfile_IdAndMembershipStatusIn(
            UUID companyId,
            UUID mentorProfileId,
            Collection<CompanyMentorPoolMembership.MembershipStatus> statuses
    );
}
