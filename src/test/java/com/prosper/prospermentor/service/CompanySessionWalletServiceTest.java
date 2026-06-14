package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySessionWallet;
import com.prosper.prospermentor.entity.CompanySubscription;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletTransactionRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySessionWalletServiceTest {

    @Mock
    private CompanySessionWalletRepository walletRepository;
    @Mock
    private CompanySessionWalletTransactionRepository walletTransactionRepository;
    @Mock
    private CompanySubscriptionRepository companySubscriptionRepository;
    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanySessionWalletService companySessionWalletService;

    @Test
    void recordPurchase_shouldCreateWalletAndIncreaseAvailableSessions() {
        UUID companySubscriptionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        CompanySubscription companySubscription = new CompanySubscription();
        companySubscription.setId(companySubscriptionId);

        Company company = new Company();
        company.setId(companyId);

        when(walletRepository.findByCompanySubscription_Id(companySubscriptionId)).thenReturn(Optional.empty());
        when(companySubscriptionRepository.findById(companySubscriptionId)).thenReturn(Optional.of(companySubscription));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(walletRepository.save(any(CompanySessionWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySessionWallet wallet = companySessionWalletService.recordPurchase(
                companySubscriptionId,
                companyId,
                new BigDecimal("4500"),
                12,
                UUID.randomUUID(),
                "invoice-123"
        );

        assertThat(wallet.getSessionsPurchasedTotal()).isEqualTo(12);
        assertThat(wallet.getSessionsAvailable()).isEqualTo(12);
        assertThat(wallet.getCompanySubscription()).isEqualTo(companySubscription);
        assertThat(wallet.getCompany()).isEqualTo(company);
    }

    @Test
    void reserveAllocation_shouldReduceWalletAvailability() {
        CompanySubscription companySubscription = new CompanySubscription();
        companySubscription.setId(UUID.randomUUID());

        Company company = new Company();
        company.setId(UUID.randomUUID());

        CompanySessionWallet wallet = new CompanySessionWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setCompany(company);
        wallet.setCompanySubscription(companySubscription);
        wallet.setSessionsPurchasedTotal(20);
        wallet.setSessionsAllocatedTotal(0);
        wallet.setSessionsReturnedTotal(0);
        wallet.setSessionsAvailable(20);

        when(walletRepository.findByCompanySubscription_Id(companySubscription.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(CompanySessionWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySessionWallet updated = companySessionWalletService.reserveAllocation(
                companySubscription.getId(),
                5,
                UUID.randomUUID(),
                "allocation-1"
        );

        assertThat(updated.getSessionsAvailable()).isEqualTo(15);
        assertThat(updated.getSessionsAllocatedTotal()).isEqualTo(5);
    }
}
