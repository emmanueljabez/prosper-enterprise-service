package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanySessionWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanySessionWalletTransactionRepository extends JpaRepository<CompanySessionWalletTransaction, UUID> {

    List<CompanySessionWalletTransaction> findByWallet_IdOrderByCreatedAtDesc(UUID walletId);
}
