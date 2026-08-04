package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.EmployeeSessionAllocationTransaction;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.repository.EmployeeSessionAllocationRepository;
import com.prosper.prospermentor.repository.EmployeeSessionAllocationTransactionRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeSessionAllocationService {

    private final EmployeeSessionAllocationRepository allocationRepository;
    private final EmployeeSessionAllocationTransactionRepository allocationTransactionRepository;
    private final ProfileRepository profileRepository;
    private final CompanySessionWalletService companySessionWalletService;

    @Transactional(readOnly = true)
    public Map<String, Object> list(UUID companyId, int page, int size, String searchTerm) {
        int resolvedPage = Math.max(0, page);
        int resolvedSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        String resolvedSearch = searchTerm != null ? searchTerm.trim() : "";

        Page<EmployeeSessionAllocation> allocationPage = allocationRepository.findByCompanyIdWithSearch(companyId, resolvedSearch, pageable);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allocations", allocationPage.getContent().stream().map(this::toPayload).toList());
        payload.put("currentPage", allocationPage.getNumber());
        payload.put("pageSize", allocationPage.getSize());
        payload.put("totalPages", allocationPage.getTotalPages());
        payload.put("totalItems", allocationPage.getTotalElements());
        payload.put("hasNext", allocationPage.hasNext());
        payload.put("hasPrevious", allocationPage.hasPrevious());
        payload.put("search", resolvedSearch);
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lookup(UUID companyId, Collection<UUID> profileIds) {
        List<UUID> requestedProfileIds = profileIds == null
                ? List.of()
                : profileIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allocations", allocationRepository.findByCompany_IdAndProfile_IdIn(companyId, requestedProfileIds)
                .stream()
                .map(this::toPayload)
                .toList());
        payload.put("requestedProfileIds", new LinkedHashSet<>(requestedProfileIds));
        return payload;
    }

    public Map<String, Object> allocate(UUID companySubscriptionId,
                                        UUID companyId,
                                        UUID profileId,
                                        int quantity,
                                        UUID actorUserId) {
        validateQuantity(quantity);
        Profile profile = requireCompanyProfile(companyId, profileId);
        companySessionWalletService.reserveAllocation(companySubscriptionId, quantity, actorUserId, profileId.toString());

        EmployeeSessionAllocation allocation = allocationRepository.findByCompany_IdAndProfile_Id(companyId, profileId)
                .orElseGet(() -> newAllocation(profile.getCompany(), profile));

        allocation.setAllocatedTotal(allocation.getAllocatedTotal() + quantity);
        allocation.setAvailableBalance(allocation.getAvailableBalance() + quantity);
        allocation.setLastAllocatedAt(LocalDateTime.now());
        allocation.setLastActivityAt(LocalDateTime.now());

        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved,
                EmployeeSessionAllocationTransaction.TransactionType.ALLOCATED,
                quantity,
                actorUserId,
                "COMPANY_SUBSCRIPTION",
                companySubscriptionId != null ? companySubscriptionId.toString() : null,
                "Allocated company-funded sessions");

        return responsePayload(saved);
    }

    public Map<String, Object> withdraw(UUID companySubscriptionId,
                                        UUID companyId,
                                        UUID profileId,
                                        int quantity,
                                        UUID actorUserId) {
        validateQuantity(quantity);

        EmployeeSessionAllocation allocation = allocationRepository.findByCompany_IdAndProfile_Id(companyId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));

        if (quantity > allocation.getAvailableBalance()) {
            throw new IllegalStateException("Cannot withdraw more than the employee's unused balance");
        }

        allocation.setAllocatedTotal(allocation.getAllocatedTotal() - quantity);
        allocation.setAvailableBalance(allocation.getAvailableBalance() - quantity);
        allocation.setLastActivityAt(LocalDateTime.now());

        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        companySessionWalletService.returnAllocation(companySubscriptionId, quantity, actorUserId, profileId.toString());
        appendTransaction(saved,
                EmployeeSessionAllocationTransaction.TransactionType.WITHDRAWN,
                quantity,
                actorUserId,
                "COMPANY_SUBSCRIPTION",
                companySubscriptionId != null ? companySubscriptionId.toString() : null,
                "Returned unused company-funded sessions");

        return responsePayload(saved);
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeSessionAllocation> findActiveAllocationForProfile(UUID profileId) {
        return allocationRepository.findActiveByProfileId(profileId);
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeSessionAllocation> findAllocationForProfile(UUID profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return allocationRepository.findByProfile_Id(profileId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyBalance(UUID profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required");
        }

        Profile profile = profileRepository.findByIdWithCompany(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        return allocationRepository.findByProfile_Id(profileId)
                .map(this::toBalancePayload)
                .orElseGet(() -> emptyBalancePayload(profile));
    }

    public EmployeeSessionAllocation consumeBooking(UUID allocationId, UUID actorUserId) {
        EmployeeSessionAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));
        if (allocation.getAvailableBalance() < 1) {
            throw new IllegalStateException("No company-funded sessions available");
        }

        allocation.setAvailableBalance(allocation.getAvailableBalance() - 1);
        allocation.setConsumedTotal(allocation.getConsumedTotal() + 1);
        allocation.setLastActivityAt(LocalDateTime.now());

        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved,
                EmployeeSessionAllocationTransaction.TransactionType.BOOKED,
                1,
                actorUserId,
                "SESSION",
                null,
                "Consumed company-funded booking");
        return saved;
    }

    public EmployeeSessionAllocation returnConsumedBooking(UUID allocationId, UUID sessionId, UUID actorUserId) {
        EmployeeSessionAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));

        allocation.setAvailableBalance(allocation.getAvailableBalance() + 1);
        allocation.setConsumedTotal(Math.max(0, allocation.getConsumedTotal() - 1));
        allocation.setLastActivityAt(LocalDateTime.now());

        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved,
                EmployeeSessionAllocationTransaction.TransactionType.BOOKING_CANCELLED_RETURN,
                1,
                actorUserId,
                "SESSION",
                sessionId != null ? sessionId.toString() : null,
                "Returned cancelled company-funded booking");
        return saved;
    }

    private Profile requireCompanyProfile(UUID companyId, UUID profileId) {
        Profile profile = profileRepository.findByIdWithCompany(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
        if (profile.getCompany() == null || !companyId.equals(profile.getCompany().getId())) {
            throw new IllegalStateException("Profile is not linked to this company");
        }
        return profile;
    }

    private EmployeeSessionAllocation newAllocation(Company company, Profile profile) {
        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setCompany(company);
        allocation.setProfile(profile);
        allocation.setAllocatedTotal(0);
        allocation.setConsumedTotal(0);
        allocation.setAvailableBalance(0);
        return allocation;
    }

    private void appendTransaction(EmployeeSessionAllocation allocation,
                                   EmployeeSessionAllocationTransaction.TransactionType transactionType,
                                   int quantity,
                                   UUID actorUserId,
                                   String referenceType,
                                   String referenceId,
                                   String notes) {
        EmployeeSessionAllocationTransaction transaction = new EmployeeSessionAllocationTransaction();
        transaction.setEmployeeSessionAllocation(allocation);
        transaction.setCompany(allocation.getCompany());
        transaction.setProfile(allocation.getProfile());
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setBalanceAfter(allocation.getAvailableBalance());
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setNotes(notes);
        transaction.setCreatedByUserId(actorUserId);
        allocationTransactionRepository.save(transaction);
    }

    private Map<String, Object> responsePayload(EmployeeSessionAllocation allocation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allocation", toPayload(allocation));
        return payload;
    }

    private Map<String, Object> toBalancePayload(EmployeeSessionAllocation allocation) {
        Map<String, Object> payload = toPayload(allocation);
        payload.put("companyName", allocation.getCompany() != null ? allocation.getCompany().getName() : null);
        return payload;
    }

    private Map<String, Object> emptyBalancePayload(Profile profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Company company = profile.getCompany();
        payload.put("id", null);
        payload.put("companyId", company != null ? company.getId() : null);
        payload.put("companyName", company != null ? company.getName() : null);
        payload.put("profileId", profile.getId());
        payload.put("profileName", profileName(profile));
        payload.put("profileEmail", profile.getEmail());
        payload.put("allocatedTotal", 0);
        payload.put("consumedTotal", 0);
        payload.put("availableBalance", 0);
        payload.put("lastAllocatedAt", null);
        payload.put("lastActivityAt", null);
        payload.put("createdAt", null);
        payload.put("updatedAt", null);
        return payload;
    }

    private Map<String, Object> toPayload(EmployeeSessionAllocation allocation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", allocation.getId());
        payload.put("companyId", allocation.getCompany() != null ? allocation.getCompany().getId() : null);
        payload.put("profileId", allocation.getProfile() != null ? allocation.getProfile().getId() : null);
        payload.put("profileName", profileName(allocation.getProfile()));
        payload.put("profileEmail", allocation.getProfile() != null ? allocation.getProfile().getEmail() : null);
        payload.put("allocatedTotal", allocation.getAllocatedTotal());
        payload.put("consumedTotal", allocation.getConsumedTotal());
        payload.put("availableBalance", allocation.getAvailableBalance());
        payload.put("lastAllocatedAt", allocation.getLastAllocatedAt());
        payload.put("lastActivityAt", allocation.getLastActivityAt());
        payload.put("createdAt", allocation.getCreatedAt());
        payload.put("updatedAt", allocation.getUpdatedAt());
        return payload;
    }

    private String profileName(Profile profile) {
        if (profile == null) {
            return "Employee";
        }
        String firstName = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String lastName = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String fullName = String.join(" ", java.util.List.of(firstName, lastName)).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        return profile.getEmail() != null ? profile.getEmail() : "Employee";
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
    }
}
