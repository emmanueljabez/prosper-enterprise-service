package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyWalkthroughProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyWalkthroughProgressRepository extends JpaRepository<CompanyWalkthroughProgress, UUID> {
    Optional<CompanyWalkthroughProgress> findByCompanyIdAndProfileIdAndVersion(UUID companyId, UUID profileId, String version);
}
