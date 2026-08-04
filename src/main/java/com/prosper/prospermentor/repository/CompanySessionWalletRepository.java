package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanySessionWallet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySessionWalletRepository extends JpaRepository<CompanySessionWallet, UUID> {

    @EntityGraph(attributePaths = {"companySubscription", "company"})
    Optional<CompanySessionWallet> findByCompanySubscription_Id(UUID companySubscriptionId);

    @EntityGraph(attributePaths = {"companySubscription", "company"})
    Optional<CompanySessionWallet> findByCompany_Id(UUID companyId);
}
