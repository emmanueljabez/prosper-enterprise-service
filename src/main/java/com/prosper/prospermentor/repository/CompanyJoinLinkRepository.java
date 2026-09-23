package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyJoinLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyJoinLinkRepository extends JpaRepository<CompanyJoinLink, UUID> {

    Optional<CompanyJoinLink> findFirstByCompanyIdAndStatus(UUID companyId, CompanyJoinLink.Status status);

    Optional<CompanyJoinLink> findByIdAndStatus(UUID id, CompanyJoinLink.Status status);

    Optional<CompanyJoinLink> findByTokenHashAndStatus(String tokenHash, CompanyJoinLink.Status status);
}
