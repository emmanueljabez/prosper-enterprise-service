package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySessionWallet;
import com.prosper.prospermentor.entity.CompanySessionWalletTransaction;
import com.prosper.prospermentor.entity.CompanySubscription;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletTransactionRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanySessionWalletService {

    private final CompanySessionWalletRepository walletRepository;
    private final CompanySessionWalletTransactionRepository walletTransactionRepository;
    private final CompanySubscriptionRepository companySubscriptionRepository;
    private final CompanyRepository companyRepository;

    public CompanySessionWallet recordPurchase(UUID companySubscriptionId,
                                               UUID companyId,
                                               BigDecimal pricePerSession,
                                               int quantity,
                                               UUID createdByUserId,
                                               String invoiceReference) {
        validateQuantity(quantity);

        CompanySessionWallet wallet = walletRepository.findByCompanySubscription_Id(companySubscriptionId)
                .orElseGet(() -> newWallet(companySubscriptionId, companyId, pricePerSession));

        wallet.setPricePerSessionSnapshot(pricePerSession != null ? pricePerSession : BigDecimal.ZERO);
        wallet.setSessionsPurchasedTotal(wallet.getSessionsPurchasedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() + quantity);

        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(
                saved,
                CompanySessionWalletTransaction.TransactionType.PURCHASE,
                quantity,
                createdByUserId,
                "INVOICE",
                invoiceReference,
                "Corporate session purchase"
        );
        return saved;
    }

    public CompanySessionWallet reserveAllocation(UUID companySubscriptionId,
                                                  int quantity,
                                                  UUID createdByUserId,
                                                  String referenceId) {
        validateQuantity(quantity);

        CompanySessionWallet wallet = walletRepository.findByCompanySubscription_Id(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company wallet not found"));

        if (wallet.getSessionsAvailable() < quantity) {
            throw new IllegalStateException("Insufficient company wallet balance");
        }

        wallet.setSessionsAllocatedTotal(wallet.getSessionsAllocatedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() - quantity);

        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(
                saved,
                CompanySessionWalletTransaction.TransactionType.ALLOCATION_OUT,
                quantity,
                createdByUserId,
                "ALLOCATION",
                referenceId,
                "Reserved sessions for employee allocation"
        );
        return saved;
    }

    public CompanySessionWallet returnAllocation(UUID companySubscriptionId,
                                                 int quantity,
                                                 UUID createdByUserId,
                                                 String referenceId) {
        validateQuantity(quantity);

        CompanySessionWallet wallet = walletRepository.findByCompanySubscription_Id(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company wallet not found"));

        wallet.setSessionsReturnedTotal(wallet.getSessionsReturnedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() + quantity);

        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(
                saved,
                CompanySessionWalletTransaction.TransactionType.ALLOCATION_RETURN,
                quantity,
                createdByUserId,
                "ALLOCATION",
                referenceId,
                "Returned unused employee allocation"
        );
        return saved;
    }

    private CompanySessionWallet newWallet(UUID companySubscriptionId, UUID companyId, BigDecimal pricePerSession) {
        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        CompanySessionWallet wallet = new CompanySessionWallet();
        wallet.setCompanySubscription(companySubscription);
        wallet.setCompany(company);
        wallet.setPricePerSessionSnapshot(pricePerSession != null ? pricePerSession : BigDecimal.ZERO);
        wallet.setSessionsPurchasedTotal(0);
        wallet.setSessionsAllocatedTotal(0);
        wallet.setSessionsReturnedTotal(0);
        wallet.setSessionsAvailable(0);
        return wallet;
    }

    private void appendWalletTransaction(CompanySessionWallet wallet,
                                         CompanySessionWalletTransaction.TransactionType transactionType,
                                         int quantity,
                                         UUID createdByUserId,
                                         String referenceType,
                                         String referenceId,
                                         String notes) {
        CompanySessionWalletTransaction transaction = new CompanySessionWalletTransaction();
        transaction.setWallet(wallet);
        transaction.setCompany(wallet.getCompany());
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setBalanceAfter(wallet.getSessionsAvailable());
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setNotes(notes);
        transaction.setCreatedByUserId(createdByUserId);
        walletTransactionRepository.save(transaction);
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
    }
}
