package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyMentorInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyMentorInvitationRepository extends JpaRepository<CompanyMentorInvitation, UUID> {

    Optional<CompanyMentorInvitation> findByInvitationTokenHash(String invitationTokenHash);

    Optional<CompanyMentorInvitation> findByCompany_IdAndEmailIgnoreCaseAndStatusIn(
            UUID companyId,
            String email,
            Collection<CompanyMentorInvitation.InvitationStatus> statuses
    );

    boolean existsByCompany_IdAndEmailIgnoreCaseAndStatusIn(
            UUID companyId,
            String email,
            Collection<CompanyMentorInvitation.InvitationStatus> statuses
    );

    boolean existsByCompany_IdAndPhoneAndStatusIn(
            UUID companyId,
            String phone,
            Collection<CompanyMentorInvitation.InvitationStatus> statuses
    );

    Page<CompanyMentorInvitation> findByCompany_Id(UUID companyId, Pageable pageable);
}
