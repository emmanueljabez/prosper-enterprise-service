package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.EmployeeSessionAllocationRepository;
import com.prosper.prospermentor.repository.EmployeeSessionAllocationTransactionRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeSessionAllocationServiceTest {

    @Mock
    private EmployeeSessionAllocationRepository allocationRepository;
    @Mock
    private EmployeeSessionAllocationTransactionRepository allocationTransactionRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private CompanySessionWalletService companySessionWalletService;

    @InjectMocks
    private EmployeeSessionAllocationService employeeSessionAllocationService;

    @Test
    void allocate_shouldReserveWalletAndIncreaseEmployeeBalance() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        Company company = new Company();
        company.setId(UUID.randomUUID());
        profile.setCompany(company);
        profile.setEmail("employee@example.com");

        when(profileRepository.findByIdWithCompany(profile.getId())).thenReturn(Optional.of(profile));
        when(allocationRepository.findByCompany_IdAndProfile_Id(company.getId(), profile.getId())).thenReturn(Optional.empty());
        when(allocationRepository.save(any(EmployeeSessionAllocation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> response = employeeSessionAllocationService.allocate(
                UUID.randomUUID(),
                company.getId(),
                profile.getId(),
                4,
                UUID.randomUUID()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> allocation = (Map<String, Object>) response.get("allocation");
        assertThat(allocation).containsEntry("allocatedTotal", 4);
        assertThat(allocation).containsEntry("availableBalance", 4);
        verify(companySessionWalletService).reserveAllocation(any(), eq(4), any(), any());
    }

    @Test
    void withdraw_shouldRejectIfQuantityExceedsUnusedBalance() {
        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        Company company = new Company();
        company.setId(UUID.randomUUID());
        allocation.setCompany(company);
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        allocation.setProfile(profile);
        allocation.setAllocatedTotal(10);
        allocation.setConsumedTotal(7);
        allocation.setAvailableBalance(3);

        when(allocationRepository.findByCompany_IdAndProfile_Id(company.getId(), profile.getId()))
                .thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> employeeSessionAllocationService.withdraw(
                UUID.randomUUID(),
                company.getId(),
                profile.getId(),
                4,
                UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unused balance");
    }

    @Test
    void lookup_shouldReturnAllocationsForRequestedProfilesOnly() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        Profile firstProfile = new Profile();
        firstProfile.setId(UUID.randomUUID());
        firstProfile.setCompany(company);
        firstProfile.setFirstName("Jay");
        firstProfile.setLastName("Otieno");
        firstProfile.setEmail("jay@example.com");

        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompany(company);
        allocation.setProfile(firstProfile);
        allocation.setAllocatedTotal(6);
        allocation.setConsumedTotal(2);
        allocation.setAvailableBalance(4);

        when(allocationRepository.findByCompany_IdAndProfile_IdIn(eq(company.getId()), any()))
                .thenReturn(java.util.List.of(allocation));

        Map<String, Object> response = employeeSessionAllocationService.lookup(
                company.getId(),
                java.util.List.of(firstProfile.getId(), UUID.randomUUID())
        );

        assertThat(response).containsKey("allocations");
        assertThat(response).containsKey("requestedProfileIds");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> allocations = (java.util.List<Map<String, Object>>) response.get("allocations");
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0)).containsEntry("profileId", firstProfile.getId());
        assertThat(allocations.get(0)).containsEntry("availableBalance", 4);
    }

    @Test
    void getMyBalance_shouldReturnAuthenticatedEmployeeAllocationEvenWhenFullyConsumed() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Kenya Airways");

        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setCompany(company);
        profile.setFirstName("Jay");
        profile.setLastName("Otieno");
        profile.setEmail("jay@example.com");

        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompany(company);
        allocation.setProfile(profile);
        allocation.setAllocatedTotal(3);
        allocation.setConsumedTotal(3);
        allocation.setAvailableBalance(0);

        when(profileRepository.findByIdWithCompany(profile.getId())).thenReturn(Optional.of(profile));
        when(allocationRepository.findByProfile_Id(profile.getId())).thenReturn(Optional.of(allocation));

        Map<String, Object> response = employeeSessionAllocationService.getMyBalance(profile.getId());

        assertThat(response).containsEntry("companyId", company.getId());
        assertThat(response).containsEntry("companyName", "Kenya Airways");
        assertThat(response).containsEntry("allocatedTotal", 3);
        assertThat(response).containsEntry("consumedTotal", 3);
        assertThat(response).containsEntry("availableBalance", 0);
    }
}
