# Corporate Session Wallet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace corporate seat-based billing and assignment with a prepaid session wallet model, employee session allocations, and booking-time consumption while preserving existing company program and session booking behavior.

**Architecture:** Keep `SubscriptionPlan` and `CompanySubscription` as the commercial wrapper, then add wallet and employee allocation tables as the authoritative source of corporate session entitlements. Route new backend behavior through focused wallet/allocation services, adapt `SubscriptionService` and `SessionBookingService` to consume and return employee balances, and migrate the frontend from seat-based billing screens to wallet and allocation views without discarding existing company program flows.

**Tech Stack:** Java 17, Spring Boot, JPA/Hibernate, PostgreSQL, Flyway, JUnit 5, Mockito, Nuxt 3, Pinia, Axios, Vue 3

---

## File Structure

### Backend billing and entitlement files

- Create: `src/main/resources/db/migration/V52__Create_company_session_wallets.sql`
- Create: `src/main/resources/db/migration/V53__Create_employee_session_allocations.sql`
- Create: `src/main/resources/db/migration/V54__Track_corporate_allocation_usage_on_sessions.sql`
- Create: `src/main/java/com/prosper/prospermentor/entity/CompanySessionWallet.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/CompanySessionWalletTransaction.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocation.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocationTransaction.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletTransactionRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationTransactionRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/service/CompanySessionWalletService.java`
- Create: `src/main/java/com/prosper/prospermentor/service/EmployeeSessionAllocationService.java`
- Create: `src/main/java/com/prosper/prospermentor/controller/EmployeeSessionAllocationController.java`
- Modify: `src/main/java/com/prosper/prospermentor/entity/Session.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java`
- Modify: `src/main/java/com/prosper/prospermentor/controller/CompanySubscriptionController.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/MpesaService.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/SubscriptionService.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/SessionBookingService.java`
- Modify: `src/main/java/com/prosper/prospermentor/repository/ProfileRepository.java`

### Backend tests

- Create: `src/test/java/com/prosper/prospermentor/service/CompanySessionWalletServiceTest.java`
- Create: `src/test/java/com/prosper/prospermentor/service/CompanySubscriptionServiceSessionWalletTest.java`
- Create: `src/test/java/com/prosper/prospermentor/service/EmployeeSessionAllocationServiceTest.java`
- Create: `src/test/java/com/prosper/prospermentor/service/SubscriptionServiceCorporateAllocationTest.java`
- Create: `src/test/java/com/prosper/prospermentor/service/SessionBookingServiceCorporateAllocationTest.java`

### Frontend billing and allocation files

- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySubscriptions.ts`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySessionAllocations.ts`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/composables/useCompanySessionWalletAdmin.ts`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/settings/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/users/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/participants.vue`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/components/app/admin/CompanyProgramEmployeesPanel.vue`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/[programId]/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/store/modules/company-programs.ts`

---

### Task 1: Create wallet and allocation persistence foundations

**Files:**
- Create: `src/main/resources/db/migration/V52__Create_company_session_wallets.sql`
- Create: `src/main/resources/db/migration/V53__Create_employee_session_allocations.sql`
- Create: `src/main/resources/db/migration/V54__Track_corporate_allocation_usage_on_sessions.sql`
- Create: `src/main/java/com/prosper/prospermentor/entity/CompanySessionWallet.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/CompanySessionWalletTransaction.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocation.java`
- Create: `src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocationTransaction.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletTransactionRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationRepository.java`
- Create: `src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationTransactionRepository.java`
- Modify: `src/main/java/com/prosper/prospermentor/entity/Session.java`
- Test: `src/test/java/com/prosper/prospermentor/service/CompanySessionWalletServiceTest.java`

- [ ] **Step 1: Write the failing wallet persistence tests**

```java
@ExtendWith(MockitoExtension.class)
class CompanySessionWalletServiceTest {

    @Mock
    private CompanySessionWalletRepository walletRepository;
    @Mock
    private CompanySessionWalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private CompanySessionWalletService companySessionWalletService;

    @Test
    void recordPurchase_shouldCreateWalletAndIncreaseAvailableSessions() {
        UUID companySubscriptionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(walletRepository.findByCompanySubscriptionId(companySubscriptionId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(CompanySessionWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySessionWallet wallet = companySessionWalletService.recordPurchase(
                companySubscriptionId,
                companyId,
                new BigDecimal("2500"),
                12,
                UUID.randomUUID(),
                "invoice-123"
        );

        assertThat(wallet.getSessionsPurchasedTotal()).isEqualTo(12);
        assertThat(wallet.getSessionsAvailable()).isEqualTo(12);
    }

    @Test
    void reserveAllocation_shouldReduceWalletAvailability() {
        CompanySessionWallet wallet = new CompanySessionWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setCompanyId(UUID.randomUUID());
        wallet.setCompanySubscriptionId(UUID.randomUUID());
        wallet.setSessionsPurchasedTotal(20);
        wallet.setSessionsAvailable(20);

        when(walletRepository.findByCompanySubscriptionId(wallet.getCompanySubscriptionId())).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(CompanySessionWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySessionWallet updated = companySessionWalletService.reserveAllocation(
                wallet.getCompanySubscriptionId(),
                5,
                UUID.randomUUID(),
                "allocation-1"
        );

        assertThat(updated.getSessionsAvailable()).isEqualTo(15);
    }
}
```

- [ ] **Step 2: Run the wallet test to verify it fails**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.CompanySessionWalletServiceTest
```

Expected: FAIL with missing wallet entities, repositories, or service methods.

- [ ] **Step 3: Add migrations, entities, repositories, and minimal wallet service implementation**

```sql
-- src/main/resources/db/migration/V52__Create_company_session_wallets.sql
CREATE TABLE company_session_wallets (
    id UUID PRIMARY KEY,
    company_subscription_id UUID NOT NULL UNIQUE REFERENCES company_subscriptions(id),
    company_id UUID NOT NULL REFERENCES companies(id),
    price_per_session_snapshot NUMERIC(19, 2) NOT NULL,
    sessions_purchased_total INTEGER NOT NULL DEFAULT 0,
    sessions_allocated_total INTEGER NOT NULL DEFAULT 0,
    sessions_returned_total INTEGER NOT NULL DEFAULT 0,
    sessions_available INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE company_session_wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES company_session_wallets(id),
    company_id UUID NOT NULL REFERENCES companies(id),
    transaction_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type VARCHAR(60),
    reference_id VARCHAR(100),
    notes VARCHAR(255),
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

```sql
-- src/main/resources/db/migration/V53__Create_employee_session_allocations.sql
CREATE TABLE employee_session_allocations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id),
    profile_id UUID NOT NULL REFERENCES profiles(id),
    allocated_total INTEGER NOT NULL DEFAULT 0,
    consumed_total INTEGER NOT NULL DEFAULT 0,
    available_balance INTEGER NOT NULL DEFAULT 0,
    last_allocated_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_employee_session_allocations_company_profile UNIQUE (company_id, profile_id)
);

CREATE TABLE employee_session_allocation_transactions (
    id UUID PRIMARY KEY,
    employee_session_allocation_id UUID NOT NULL REFERENCES employee_session_allocations(id),
    company_id UUID NOT NULL REFERENCES companies(id),
    profile_id UUID NOT NULL REFERENCES profiles(id),
    transaction_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type VARCHAR(60),
    reference_id VARCHAR(100),
    notes VARCHAR(255),
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

```sql
-- src/main/resources/db/migration/V54__Track_corporate_allocation_usage_on_sessions.sql
ALTER TABLE sessions
    ADD COLUMN corporate_allocation_id UUID NULL REFERENCES employee_session_allocations(id),
    ADD COLUMN corporate_allocation_consumed_at TIMESTAMP NULL,
    ADD COLUMN corporate_allocation_returned_at TIMESTAMP NULL;
```

```java
// src/main/java/com/prosper/prospermentor/service/CompanySessionWalletService.java
@Service
@Transactional
public class CompanySessionWalletService {

    public CompanySessionWallet recordPurchase(UUID companySubscriptionId,
                                               UUID companyId,
                                               BigDecimal pricePerSession,
                                               int quantity,
                                               UUID createdByUserId,
                                               String invoiceReference) {
        CompanySessionWallet wallet = walletRepository.findByCompanySubscriptionId(companySubscriptionId)
                .orElseGet(() -> newWallet(companySubscriptionId, companyId, pricePerSession));

        wallet.setPricePerSessionSnapshot(pricePerSession);
        wallet.setSessionsPurchasedTotal(wallet.getSessionsPurchasedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() + quantity);
        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(saved, CompanySessionWalletTransaction.TransactionType.PURCHASE, quantity, createdByUserId, "INVOICE", invoiceReference);
        return saved;
    }

    public CompanySessionWallet reserveAllocation(UUID companySubscriptionId, int quantity, UUID createdByUserId, String referenceId) {
        CompanySessionWallet wallet = walletRepository.findByCompanySubscriptionId(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company wallet not found"));
        if (wallet.getSessionsAvailable() < quantity) {
            throw new IllegalStateException("Insufficient company wallet balance");
        }

        wallet.setSessionsAllocatedTotal(wallet.getSessionsAllocatedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() - quantity);
        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(saved, CompanySessionWalletTransaction.TransactionType.ALLOCATION_OUT, quantity, createdByUserId, "ALLOCATION", referenceId);
        return saved;
    }

    public CompanySessionWallet returnAllocation(UUID companySubscriptionId, int quantity, UUID createdByUserId, String referenceId) {
        CompanySessionWallet wallet = walletRepository.findByCompanySubscriptionId(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company wallet not found"));
        wallet.setSessionsReturnedTotal(wallet.getSessionsReturnedTotal() + quantity);
        wallet.setSessionsAvailable(wallet.getSessionsAvailable() + quantity);
        CompanySessionWallet saved = walletRepository.save(wallet);
        appendWalletTransaction(saved, CompanySessionWalletTransaction.TransactionType.ALLOCATION_RETURN, quantity, createdByUserId, "ALLOCATION", referenceId);
        return saved;
    }
}
```

- [ ] **Step 4: Run the wallet test to verify it passes**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.CompanySessionWalletServiceTest
```

Expected: PASS with both wallet tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V52__Create_company_session_wallets.sql \
  src/main/resources/db/migration/V53__Create_employee_session_allocations.sql \
  src/main/resources/db/migration/V54__Track_corporate_allocation_usage_on_sessions.sql \
  src/main/java/com/prosper/prospermentor/entity/CompanySessionWallet.java \
  src/main/java/com/prosper/prospermentor/entity/CompanySessionWalletTransaction.java \
  src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocation.java \
  src/main/java/com/prosper/prospermentor/entity/EmployeeSessionAllocationTransaction.java \
  src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletRepository.java \
  src/main/java/com/prosper/prospermentor/repository/CompanySessionWalletTransactionRepository.java \
  src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationRepository.java \
  src/main/java/com/prosper/prospermentor/repository/EmployeeSessionAllocationTransactionRepository.java \
  src/main/java/com/prosper/prospermentor/service/CompanySessionWalletService.java \
  src/main/java/com/prosper/prospermentor/entity/Session.java \
  src/test/java/com/prosper/prospermentor/service/CompanySessionWalletServiceTest.java
git commit -m "feat: add company session wallet persistence"
```

### Task 2: Refactor corporate purchase APIs from seats to session wallet top-ups

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java`
- Modify: `src/main/java/com/prosper/prospermentor/controller/CompanySubscriptionController.java`
- Modify: `src/main/java/com/prosper/prospermentor/entity/CompanySubscription.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/MpesaService.java`
- Test: `src/test/java/com/prosper/prospermentor/service/CompanySubscriptionServiceSessionWalletTest.java`

- [ ] **Step 1: Write the failing corporate purchase tests**

```java
@ExtendWith(MockitoExtension.class)
class CompanySubscriptionServiceSessionWalletTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private CompanySessionWalletService companySessionWalletService;
    @InjectMocks private CompanySubscriptionService companySubscriptionService;

    @Test
    void createCompanySubscription_shouldPriceInvoiceUsingSessionCount() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCost(new BigDecimal("2500"));
        plan.setCurrency("KES");

        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionPlanRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(companySubscriptionRepository.save(any(CompanySubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceService.createInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(invoice());

        Map<String, Object> payload = companySubscriptionService.createCompanySubscription(
                company.getId(),
                plan.getId(),
                8,
                BillingInterval.MONTHLY,
                UUID.randomUUID(),
                "https://example.com/success",
                "https://example.com/cancel"
        );

        assertThat(payload).containsEntry("amount", new BigDecimal("20000"));
    }

    @Test
    void applyInvoicePayment_shouldTopUpWalletOnCorporatePurchase() {
        CompanySubscription subscription = activeSubscription();
        when(companySubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findById(subscription.getPlan().getId())).thenReturn(Optional.of(subscription.getPlan()));

        companySubscriptionService.applyInvoicePayment(
                subscription.getId(),
                "COMPANY_SUBSCRIPTION_PURCHASE",
                BillingInterval.MONTHLY,
                subscription.getPlan().getId(),
                null,
                10
        );

        verify(companySessionWalletService).recordPurchase(
                eq(subscription.getId()),
                eq(subscription.getCompany().getId()),
                any(BigDecimal.class),
                eq(10),
                isNull(),
                anyString()
        );
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setAmount(new BigDecimal("20000"));
        invoice.setCurrency("KES");
        invoice.setInvoiceNumber("INV-001");
        return invoice;
    }

    private CompanySubscription activeSubscription() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCost(new BigDecimal("2500"));
        plan.setCurrency("KES");

        CompanySubscription subscription = new CompanySubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setStatus(CompanySubscription.CompanySubscriptionStatus.ACTIVE);
        return subscription;
    }
}
```

- [ ] **Step 2: Run the purchase service test to verify it fails**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.CompanySubscriptionServiceSessionWalletTest
```

Expected: FAIL because `sessionCount` semantics and wallet top-up behavior do not exist yet.

- [ ] **Step 3: Implement session-count purchase handling and wallet payloads**

```java
// src/main/java/com/prosper/prospermentor/controller/CompanySubscriptionController.java
public static class CreateCompanySubscriptionRequest {
    private String companyId;
    private String planId;
    private int sessionCount;
    private String billingInterval;
    private String redirectSuccessUrl;
    private String redirectCancelUrl;
    // getters and setters
}
```

```java
// src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java
public Map<String, Object> createCompanySubscription(UUID companyId,
                                                     UUID planId,
                                                     int sessionCount,
                                                     BillingInterval billingInterval,
                                                     UUID createdByUserId,
                                                     String redirectSuccessUrl,
                                                     String redirectCancelUrl) {
    validateCorporateSessionCount(sessionCount);
    BigDecimal amount = plan.getCost().multiply(BigDecimal.valueOf(sessionCount));
    metadata.put("sessionCount", sessionCount);
    metadata.put("billingInterval", billingInterval != null ? billingInterval.name() : "MONTHLY");
    // keep billingInterval metadata only for compatibility; do not use it in amount calculation
    Invoice invoice = invoiceService.createInvoice(
            createdByUserId,
            company.getId(),
            amount,
            normalizeCurrency(plan.getCurrency()),
            buildCorporateInvoiceDescription(plan, sessionCount, CONTEXT_PURCHASE, "TOP_UP", billingInterval),
            metadata,
            redirectSuccessUrl,
            redirectCancelUrl,
            LocalDateTime.now().plusDays(7)
    );
    response.put("sessionCount", sessionCount);
    response.put("pricePerSession", plan.getCost());
    return response;
}

public void applyInvoicePayment(UUID companySubscriptionId,
                                String invoiceContext,
                                BillingInterval billingInterval,
                                UUID targetPlanId,
                                Integer targetSeatCount,
                                Integer targetSessionCount) {
    int resolvedSessionCount = targetSessionCount != null ? targetSessionCount : 0;
    if (resolvedSessionCount > 0) {
        companySessionWalletService.recordPurchase(
                companySubscription.getId(),
                companySubscription.getCompany().getId(),
                targetPlan.getCost(),
                resolvedSessionCount,
                null,
                invoiceContext
        );
    }
}
```

```java
// src/main/java/com/prosper/prospermentor/service/MpesaService.java
companySubscriptionService.applyInvoicePayment(
        companySubscriptionId,
        invoiceContext,
        billingInterval,
        targetPlanId,
        targetSeatCount,
        targetSessionCount
);
```

```java
// src/main/java/com/prosper/prospermentor/entity/CompanySubscription.java
public void activateCorporateWalletSubscription() {
    this.status = CompanySubscriptionStatus.ACTIVE;
    if (this.startDate == null) {
        this.startDate = LocalDateTime.now();
    }
    this.endDate = null;
    this.currentPeriodStart = this.startDate;
    this.currentPeriodEnd = null;
}
```

- [ ] **Step 4: Run the purchase service test to verify it passes**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.CompanySubscriptionServiceSessionWalletTest
```

Expected: PASS with invoice pricing and wallet top-up assertions green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java \
  src/main/java/com/prosper/prospermentor/controller/CompanySubscriptionController.java \
  src/main/java/com/prosper/prospermentor/entity/CompanySubscription.java \
  src/main/java/com/prosper/prospermentor/service/MpesaService.java \
  src/test/java/com/prosper/prospermentor/service/CompanySubscriptionServiceSessionWalletTest.java
git commit -m "feat: switch corporate purchases to session top-ups"
```

### Task 3: Add employee allocation APIs and business rules

**Files:**
- Create: `src/main/java/com/prosper/prospermentor/service/EmployeeSessionAllocationService.java`
- Create: `src/main/java/com/prosper/prospermentor/controller/EmployeeSessionAllocationController.java`
- Modify: `src/main/java/com/prosper/prospermentor/repository/ProfileRepository.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java`
- Test: `src/test/java/com/prosper/prospermentor/service/EmployeeSessionAllocationServiceTest.java`

- [ ] **Step 1: Write the failing allocation tests**

```java
@ExtendWith(MockitoExtension.class)
class EmployeeSessionAllocationServiceTest {

    @Mock private EmployeeSessionAllocationRepository allocationRepository;
    @Mock private EmployeeSessionAllocationTransactionRepository allocationTransactionRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private CompanySessionWalletService companySessionWalletService;
    @InjectMocks private EmployeeSessionAllocationService employeeSessionAllocationService;

    @Test
    void allocate_shouldReserveWalletAndIncreaseEmployeeBalance() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        Company company = new Company();
        company.setId(UUID.randomUUID());
        profile.setCompany(company);

        when(profileRepository.findByIdWithCompany(profile.getId())).thenReturn(Optional.of(profile));
        when(allocationRepository.findByCompanyIdAndProfileId(company.getId(), profile.getId())).thenReturn(Optional.empty());
        when(allocationRepository.save(any(EmployeeSessionAllocation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeSessionAllocation allocation = employeeSessionAllocationService.allocate(
                UUID.randomUUID(),
                company.getId(),
                profile.getId(),
                4,
                UUID.randomUUID()
        );

        assertThat(allocation.getAllocatedTotal()).isEqualTo(4);
        assertThat(allocation.getAvailableBalance()).isEqualTo(4);
        verify(companySessionWalletService).reserveAllocation(any(), eq(4), any(), any());
    }

    @Test
    void withdraw_shouldRejectIfQuantityExceedsUnusedBalance() {
        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompanyId(UUID.randomUUID());
        allocation.setProfileId(UUID.randomUUID());
        allocation.setAllocatedTotal(10);
        allocation.setConsumedTotal(7);
        allocation.setAvailableBalance(3);

        when(allocationRepository.findByCompanyIdAndProfileId(allocation.getCompanyId(), allocation.getProfileId()))
                .thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> employeeSessionAllocationService.withdraw(
                UUID.randomUUID(),
                allocation.getCompanyId(),
                allocation.getProfileId(),
                4,
                UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unused balance");
    }
}
```

- [ ] **Step 2: Run the allocation service test to verify it fails**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.EmployeeSessionAllocationServiceTest
```

Expected: FAIL because allocation service and endpoints do not exist.

- [ ] **Step 3: Implement allocation service and controller**

```java
// src/main/java/com/prosper/prospermentor/service/EmployeeSessionAllocationService.java
@Service
@Transactional
public class EmployeeSessionAllocationService {

    public EmployeeSessionAllocation allocate(UUID companySubscriptionId,
                                              UUID companyId,
                                              UUID profileId,
                                              int quantity,
                                              UUID actorUserId) {
        Profile profile = requireCompanyProfile(companyId, profileId);
        companySessionWalletService.reserveAllocation(companySubscriptionId, quantity, actorUserId, profileId.toString());

        EmployeeSessionAllocation allocation = allocationRepository.findByCompanyIdAndProfileId(companyId, profileId)
                .orElseGet(() -> newAllocation(companyId, profileId));

        allocation.setAllocatedTotal(allocation.getAllocatedTotal() + quantity);
        allocation.setAvailableBalance(allocation.getAvailableBalance() + quantity);
        allocation.setLastAllocatedAt(LocalDateTime.now());
        allocation.setLastActivityAt(LocalDateTime.now());
        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved, TransactionType.ALLOCATED, quantity, actorUserId, "COMPANY_SUBSCRIPTION", companySubscriptionId.toString());
        return saved;
    }

    public EmployeeSessionAllocation withdraw(UUID companySubscriptionId,
                                              UUID companyId,
                                              UUID profileId,
                                              int quantity,
                                              UUID actorUserId) {
        EmployeeSessionAllocation allocation = allocationRepository.findByCompanyIdAndProfileId(companyId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));
        if (quantity > allocation.getAvailableBalance()) {
            throw new IllegalStateException("Cannot withdraw more than the employee's unused balance");
        }

        allocation.setAllocatedTotal(allocation.getAllocatedTotal() - quantity);
        allocation.setAvailableBalance(allocation.getAvailableBalance() - quantity);
        allocation.setLastActivityAt(LocalDateTime.now());
        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        companySessionWalletService.returnAllocation(companySubscriptionId, quantity, actorUserId, profileId.toString());
        appendTransaction(saved, TransactionType.WITHDRAWN, quantity, actorUserId, "COMPANY_SUBSCRIPTION", companySubscriptionId.toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeSessionAllocation> findActiveAllocationForProfile(UUID profileId) {
        return allocationRepository.findActiveByProfileId(profileId);
    }

    public EmployeeSessionAllocation consumeBooking(UUID allocationId, UUID profileId) {
        EmployeeSessionAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));
        if (allocation.getAvailableBalance() < 1) {
            throw new IllegalStateException("No company-funded sessions available");
        }
        allocation.setAvailableBalance(allocation.getAvailableBalance() - 1);
        allocation.setConsumedTotal(allocation.getConsumedTotal() + 1);
        allocation.setLastActivityAt(LocalDateTime.now());
        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved, TransactionType.BOOKED, 1, profileId, "SESSION", null);
        return saved;
    }

    public EmployeeSessionAllocation returnConsumedBooking(UUID allocationId, UUID sessionId, UUID actorUserId) {
        EmployeeSessionAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Employee allocation not found"));
        allocation.setAvailableBalance(allocation.getAvailableBalance() + 1);
        allocation.setConsumedTotal(Math.max(allocation.getConsumedTotal() - 1, 0));
        allocation.setLastActivityAt(LocalDateTime.now());
        EmployeeSessionAllocation saved = allocationRepository.save(allocation);
        appendTransaction(saved, TransactionType.BOOKING_CANCELLED_RETURN, 1, actorUserId, "SESSION", sessionId.toString());
        return saved;
    }
}
```

```java
// src/main/java/com/prosper/prospermentor/controller/EmployeeSessionAllocationController.java
@RestController
@RequestMapping("/api/v1/companies/{companyId}/employee-session-allocations")
public class EmployeeSessionAllocationController {

    public static class QuantityRequest {
        private UUID companySubscriptionId;
        private int quantity;
        public UUID getCompanySubscriptionId() { return companySubscriptionId; }
        public int getQuantity() { return quantity; }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@PathVariable UUID companyId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) String search,
                                                                 Authentication authentication) {
        SupabaseUserDetails userDetails = requireUser(authentication);
        authorizeCompanyRequest(userDetails, companyId);
        return ResponseEntity.ok(ApiResponse.success(
                "Employee session allocations retrieved successfully",
                employeeSessionAllocationService.list(companyId, page, size, search)
        ));
    }

    @PostMapping("/{profileId}/allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocate(@PathVariable UUID companyId,
                                                                     @PathVariable UUID profileId,
                                                                     @RequestBody QuantityRequest request,
                                                                     Authentication authentication) {
        SupabaseUserDetails userDetails = requireUser(authentication);
        authorizeCompanyRequest(userDetails, companyId);
        return ResponseEntity.ok(ApiResponse.success(
                "Employee sessions allocated successfully",
                employeeSessionAllocationService.allocate(request.getCompanySubscriptionId(), companyId, profileId, request.getQuantity(), userDetails.getUserIdAsUuid())
        ));
    }

    @PostMapping("/{profileId}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(@PathVariable UUID companyId,
                                                                     @PathVariable UUID profileId,
                                                                     @RequestBody QuantityRequest request,
                                                                     Authentication authentication) {
        SupabaseUserDetails userDetails = requireUser(authentication);
        authorizeCompanyRequest(userDetails, companyId);
        return ResponseEntity.ok(ApiResponse.success(
                "Employee sessions withdrawn successfully",
                employeeSessionAllocationService.withdraw(request.getCompanySubscriptionId(), companyId, profileId, request.getQuantity(), userDetails.getUserIdAsUuid())
        ));
    }

    private SupabaseUserDetails requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }
        return userDetails;
    }

    private void authorizeCompanyRequest(SupabaseUserDetails userDetails, UUID companyId) {
        if (userDetails.isAdmin()) {
            return;
        }
        if (!userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }
        UUID userCompanyId = profileService.getProfileWithCompany(userDetails.getUserIdAsUuid())
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);
        if (userCompanyId == null || !userCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to manage this company");
        }
    }
}
```

- [ ] **Step 4: Run the allocation service test to verify it passes**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.EmployeeSessionAllocationServiceTest
```

Expected: PASS with allocation and withdrawal rules enforced.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/prosper/prospermentor/service/EmployeeSessionAllocationService.java \
  src/main/java/com/prosper/prospermentor/controller/EmployeeSessionAllocationController.java \
  src/main/java/com/prosper/prospermentor/repository/ProfileRepository.java \
  src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java \
  src/test/java/com/prosper/prospermentor/service/EmployeeSessionAllocationServiceTest.java
git commit -m "feat: add employee session allocation APIs"
```

### Task 4: Integrate corporate allocation balances into booking and cancellation

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/service/SubscriptionService.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/SessionBookingService.java`
- Modify: `src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java`
- Test: `src/test/java/com/prosper/prospermentor/service/SubscriptionServiceCorporateAllocationTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/SessionBookingServiceCorporateAllocationTest.java`

- [ ] **Step 1: Write the failing entitlement and cancellation tests**

```java
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceCorporateAllocationTest {

    @Mock private EmployeeSessionAllocationService employeeSessionAllocationService;
    @Mock private CompanySubscriptionService companySubscriptionService;
    @InjectMocks private SubscriptionService subscriptionService;

    @Test
    void checkSessionBookingEligibility_shouldUseEmployeeAllocationBalance() {
        UUID userId = UUID.randomUUID();
        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setCompanyId(UUID.randomUUID());
        allocation.setProfileId(userId);
        allocation.setAvailableBalance(2);

        when(employeeSessionAllocationService.findActiveAllocationForProfile(userId)).thenReturn(Optional.of(allocation));

        SessionBookingEligibility eligibility = subscriptionService.checkSessionBookingEligibility(userId);

        assertThat(eligibility.isCanBook()).isTrue();
        assertThat(eligibility.getSessionsRemaining()).isEqualTo(2);
        assertThat(eligibility.getSubscriptionSource()).isEqualTo(SessionBookingEligibility.SubscriptionSource.CORPORATE);
    }
}
```

```java
@ExtendWith(MockitoExtension.class)
class SessionBookingServiceCorporateAllocationTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private EmployeeSessionAllocationService employeeSessionAllocationService;
    @InjectMocks private SessionBookingService sessionBookingService;

    @Test
    void cancelSession_shouldReturnCorporateAllocationOnce() {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(UUID.randomUUID());
        session.setCorporateAllocationId(UUID.randomUUID());
        session.setCorporateAllocationConsumedAt(LocalDateTime.now().minusMinutes(10));
        session.setStatus(Session.SessionStatus.CONFIRMED);

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionBookingService.cancelSession(session.getId(), Session.CancelledBy.MENTEE, "Need to reschedule");

        verify(employeeSessionAllocationService).returnConsumedBooking(session.getCorporateAllocationId(), session.getId(), session.getUserId());
        assertThat(session.getCorporateAllocationReturnedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run the booking tests to verify they fail**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.SubscriptionServiceCorporateAllocationTest --tests com.prosper.prospermentor.service.SessionBookingServiceCorporateAllocationTest
```

Expected: FAIL because allocation-based eligibility and cancellation returns are not wired in yet.

- [ ] **Step 3: Implement allocation-based booking integration**

```java
// src/main/java/com/prosper/prospermentor/service/SubscriptionService.java
private Optional<ResolvedEntitlement> resolveCorporateEntitlement(UUID userId) {
    return employeeSessionAllocationService.findActiveAllocationForProfile(userId)
            .map(allocation -> {
                SessionBookingEligibility eligibility = allocation.getAvailableBalance() > 0
                        ? SessionBookingEligibility.eligible(
                                "You can book a session using your company-funded allocation.",
                                allocation.getAvailableBalance(),
                                0
                        )
                        : SessionBookingEligibility.ineligible(
                                "You have no company-funded sessions available right now.",
                                SessionBookingEligibility.EligibilityReason.SESSIONS_EXHAUSTED
                        );

                eligibility.setSubscriptionSource(SessionBookingEligibility.SubscriptionSource.CORPORATE);
                eligibility.setCompanyId(allocation.getCompanyId());
                return new ResolvedEntitlement(eligibility, null, null, null, allocation.getAvailableBalance(), 0, allocation);
            });
}

public void consumeSession(UUID userId) {
    Optional<ResolvedEntitlement> entitlementOpt = resolveEffectiveEntitlement(userId);
    ResolvedEntitlement entitlement = entitlementOpt
            .orElseThrow(() -> new IllegalStateException("No active subscription or company allocation found for user: " + userId));
    if (entitlement.employeeAllocation != null) {
        employeeSessionAllocationService.consumeBooking(entitlement.employeeAllocation.getId(), userId);
        return;
    }
    consumeIndividualSession(userId, entitlement.subscription);
}

private static final class ResolvedEntitlement {
    private final SessionBookingEligibility eligibility;
    private final Subscription subscription;
    private final CompanySubscriptionMember companyMember;
    private final SubscriptionPlan plan;
    private final int remainingSessions;
    private final int addonSessionsRemaining;
    private final EmployeeSessionAllocation employeeAllocation;

    private ResolvedEntitlement(SessionBookingEligibility eligibility,
                                Subscription subscription,
                                CompanySubscriptionMember companyMember,
                                SubscriptionPlan plan,
                                int remainingSessions,
                                int addonSessionsRemaining,
                                EmployeeSessionAllocation employeeAllocation) {
        this.eligibility = eligibility;
        this.subscription = subscription;
        this.companyMember = companyMember;
        this.plan = plan;
        this.remainingSessions = remainingSessions;
        this.addonSessionsRemaining = addonSessionsRemaining;
        this.employeeAllocation = employeeAllocation;
    }
}
```

```java
// src/main/java/com/prosper/prospermentor/service/SessionBookingService.java
public Session cancelSession(UUID sessionId, Session.CancelledBy cancelledBy, String reason) {
    Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    session.cancel(cancelledBy, reason);

    if (session.getCorporateAllocationId() != null
            && session.getCorporateAllocationConsumedAt() != null
            && session.getCorporateAllocationReturnedAt() == null) {
        employeeSessionAllocationService.returnConsumedBooking(session.getCorporateAllocationId(), session.getId(), session.getUserId());
        session.setCorporateAllocationReturnedAt(LocalDateTime.now());
    }

    return sessionRepository.save(session);
}
```

- [ ] **Step 4: Run the booking tests to verify they pass**

Run:
```bash
./gradlew test --tests com.prosper.prospermentor.service.SubscriptionServiceCorporateAllocationTest --tests com.prosper.prospermentor.service.SessionBookingServiceCorporateAllocationTest
```

Expected: PASS with allocation-based eligibility and cancellation return logic green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/prosper/prospermentor/service/SubscriptionService.java \
  src/main/java/com/prosper/prospermentor/service/SessionBookingService.java \
  src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java \
  src/test/java/com/prosper/prospermentor/service/SubscriptionServiceCorporateAllocationTest.java \
  src/test/java/com/prosper/prospermentor/service/SessionBookingServiceCorporateAllocationTest.java
git commit -m "feat: consume corporate session allocations in booking flow"
```

### Task 5: Replace seat-based frontend billing with session wallet billing

**Files:**
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySubscriptions.ts`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySessionAllocations.ts`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/composables/useCompanySessionWalletAdmin.ts`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/settings/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/users/index.vue`

- [ ] **Step 1: Update the frontend API contracts first**

```ts
// /Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySubscriptions.ts
export interface CompanySessionWalletSummary {
  walletId: string
  companySubscriptionId: string
  companyId: string
  pricePerSession: number
  sessionsPurchased: number
  sessionsAllocated: number
  sessionsReturned: number
  sessionsAvailable: number
}

export interface CompanySubscriptionSummary {
  id: string
  companyId: string
  planId?: string | null
  planName?: string | null
  status: 'PENDING_PAYMENT' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'SUSPENDED'
  latestInvoice?: { invoiceId: string; invoiceNumber: string; status: string; paymentUrl?: string | null } | null
  wallet?: CompanySessionWalletSummary | null
}

async createCompanySubscription(payload: {
  companyId: string
  planId: string
  sessionCount: number
  redirectSuccessUrl?: string
  redirectCancelUrl?: string
}) {
  const { data } = await api.post('/v1/company-subscriptions', payload)
  return data
}
```

```ts
// /Users/macbookpro/WebstormProjects/myProsperV2/http/requests/app/companySessionAllocations.ts
export interface EmployeeSessionAllocationRecord {
  profileId: string
  profileName: string
  profileEmail?: string | null
  allocatedTotal: number
  consumedTotal: number
  availableBalance: number
  lastAllocatedAt?: string | null
}

export default {
  async list(companyId: string, params: { page?: number; size?: number; search?: string }) {
    return api.get(`/v1/companies/${companyId}/employee-session-allocations`, { params })
  },
  async allocate(companyId: string, profileId: string, quantity: number, companySubscriptionId: string) {
    return api.post(`/v1/companies/${companyId}/employee-session-allocations/${profileId}/allocate`, { quantity, companySubscriptionId })
  },
  async withdraw(companyId: string, profileId: string, quantity: number, companySubscriptionId: string) {
    return api.post(`/v1/companies/${companyId}/employee-session-allocations/${profileId}/withdraw`, { quantity, companySubscriptionId })
  },
}
```

- [ ] **Step 2: Build a wallet-focused admin composable**

```ts
// /Users/macbookpro/WebstormProjects/myProsperV2/composables/useCompanySessionWalletAdmin.ts
export const useCompanySessionWalletAdmin = () => {
  const wallet = ref<CompanySessionWalletSummary | null>(null)
  const companySubscriptions = ref<CompanySubscriptionSummary[]>([])
  const allocations = ref<EmployeeSessionAllocationRecord[]>([])

  const primaryCompanySubscription = computed(() =>
    companySubscriptions.value.find(subscription => subscription.status === 'ACTIVE') || companySubscriptions.value[0] || null,
  )

  const loadBilling = async (companyId: string) => {
    const response = await companySubscriptionsApi.getCompanySubscriptions(companyId)
    companySubscriptions.value = response.data || []
    wallet.value = primaryCompanySubscription.value?.wallet || null
  }

  return { wallet, companySubscriptions, allocations, loadBilling }
}
```

- [ ] **Step 3: Replace the settings billing UI**

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/settings/index.vue -->
<Card>
  <CardHeader>
    <CardTitle>Corporate Session Wallet</CardTitle>
    <CardDescription>Buy prepaid company sessions and track wallet usage.</CardDescription>
  </CardHeader>
  <CardContent class="space-y-6">
    <div class="grid gap-4 md:grid-cols-4">
      <div class="rounded-xl border p-4">
        <p class="text-sm text-muted-foreground">Purchased</p>
        <p class="text-2xl font-semibold">{{ managedCompanySubscription?.wallet?.sessionsPurchased || 0 }}</p>
      </div>
      <div class="rounded-xl border p-4">
        <p class="text-sm text-muted-foreground">Allocated</p>
        <p class="text-2xl font-semibold">{{ managedCompanySubscription?.wallet?.sessionsAllocated || 0 }}</p>
      </div>
      <div class="rounded-xl border p-4">
        <p class="text-sm text-muted-foreground">Available</p>
        <p class="text-2xl font-semibold">{{ managedCompanySubscription?.wallet?.sessionsAvailable || 0 }}</p>
      </div>
      <div class="rounded-xl border p-4">
        <p class="text-sm text-muted-foreground">Consumed</p>
        <p class="text-2xl font-semibold">{{ Math.max((managedCompanySubscription?.wallet?.sessionsAllocated || 0) - (managedCompanySubscription?.wallet?.sessionsAvailable || 0), 0) }}</p>
      </div>
    </div>

    <div class="rounded-xl border p-4">
      <p class="text-sm text-muted-foreground">Price per session</p>
      <p class="text-2xl font-semibold">{{ formatCurrency(selectedPlan.cost) }}</p>
      <Input v-model.number="corporateSessionCount" min="1" type="number" />
      <p class="text-sm text-muted-foreground">Total: {{ formatCurrency((selectedPlan.cost || 0) * (corporateSessionCount || 0)) }}</p>
      <Button @click="purchaseCorporateSessions(selectedPlan.id, selectedPlan.name)">Buy sessions</Button>
    </div>
  </CardContent>
</Card>
```

- [ ] **Step 4: Remove seat wording from dashboard and old users page**

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/index.vue -->
<p class="text-sm text-muted-foreground">
  {{ primaryCompanySubscription?.wallet?.sessionsAvailable || 0 }} company-funded sessions available
</p>
```

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/users/index.vue -->
<AlertDescription>
  Company-funded session allocation now lives in Employees. Use the Employees workspace to allocate or withdraw sessions.
</AlertDescription>
```

- [ ] **Step 5: Run the frontend build**

Run:
```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
npm run build
```

Expected: PASS with updated billing API types and settings page rendering cleanly.

- [ ] **Step 6: Commit**

```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
git add http/requests/app/companySubscriptions.ts \
  http/requests/app/companySessionAllocations.ts \
  composables/useCompanySessionWalletAdmin.ts \
  pages/app/admin/settings/index.vue \
  pages/app/admin/index.vue \
  pages/app/admin/users/index.vue
git commit -m "feat: add corporate session wallet admin billing UI"
```

### Task 6: Convert the Employees page to allocations and move roster management into company programs

**Files:**
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/participants.vue`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/components/app/admin/CompanyProgramEmployeesPanel.vue`
- Create: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/[programId]/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/store/modules/company-programs.ts`

- [ ] **Step 1: Rewrite `participants.vue` around allocations**

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/participants.vue -->
<template>
  <div class="container mx-auto space-y-6 px-4 py-6">
    <div>
      <h1 class="text-2xl font-semibold tracking-tight">Employees</h1>
      <p class="text-sm text-muted-foreground">
        Allocate company-funded sessions to employees and withdraw unused balances when needed.
      </p>
    </div>

    <Card>
      <CardHeader>
        <CardTitle>Employee Session Allocations</CardTitle>
        <CardDescription>Session balances are company-wide and usable across live company programs.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Employee</TableHead>
              <TableHead>Allocated</TableHead>
              <TableHead>Used</TableHead>
              <TableHead>Available</TableHead>
              <TableHead>Last Allocation</TableHead>
              <TableHead class="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="employee in allocations" :key="employee.profileId">
              <TableCell>{{ employee.profileName }}</TableCell>
              <TableCell>{{ employee.allocatedTotal }}</TableCell>
              <TableCell>{{ employee.consumedTotal }}</TableCell>
              <TableCell>{{ employee.availableBalance }}</TableCell>
              <TableCell>{{ formatDate(employee.lastAllocatedAt) }}</TableCell>
              <TableCell class="text-right">
                <Button size="sm" @click="openAllocateDialog(employee)">Allocate</Button>
                <Button size="sm" variant="outline" @click="openWithdrawDialog(employee)">Withdraw</Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
```

- [ ] **Step 2: Create a reusable company-program employees panel**

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/components/app/admin/CompanyProgramEmployeesPanel.vue -->
<script setup lang="ts">
const props = defineProps<{ companyProgramId: string }>()
const companyProgramsStore = useCompanyProgramsStore()
onMounted(() => companyProgramsStore.loadProgramParticipants({ companyProgramId: props.companyProgramId, page: 0, size: 50 }))
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle>Program Employees</CardTitle>
      <CardDescription>Enroll employees and manage roster membership for this company program.</CardDescription>
    </CardHeader>
    <CardContent>
      <!-- reuse existing participant search, enroll, remove, and consent status table -->
    </CardContent>
  </Card>
</template>
```

- [ ] **Step 3: Create a company-program detail page and link to it**

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/[programId]/index.vue -->
<script setup lang="ts">
const route = useRoute()
const companyProgramsStore = useCompanyProgramsStore()
const companyProgramId = computed(() => String(route.params.programId || ''))
onMounted(() => companyProgramsStore.loadCompanyProgram(companyProgramId.value))
</script>

<template>
  <div class="container mx-auto space-y-6 px-4 py-6">
    <Card>
      <CardHeader>
        <CardTitle>{{ companyProgramsStore.currentProgram?.name || 'Company Program' }}</CardTitle>
        <CardDescription>{{ companyProgramsStore.currentProgram?.objective || 'Manage program summary and employees.' }}</CardDescription>
      </CardHeader>
    </Card>
    <Tabs default-value="employees">
      <TabsList>
        <TabsTrigger value="summary">Summary</TabsTrigger>
        <TabsTrigger value="employees">Employees</TabsTrigger>
      </TabsList>
      <TabsContent value="summary">
        <Card>
          <CardContent class="pt-6">
            <p class="text-sm text-muted-foreground">{{ companyProgramsStore.currentProgram?.targetAudienceDescription || 'No target audience set.' }}</p>
          </CardContent>
        </Card>
      </TabsContent>
      <TabsContent value="employees">
        <CompanyProgramEmployeesPanel :company-program-id="companyProgramId" />
      </TabsContent>
    </Tabs>
  </div>
</template>
```

```ts
// /Users/macbookpro/WebstormProjects/myProsperV2/store/modules/company-programs.ts
state: () => ({
  currentProgram: null as CompanyProgramRecord | null,
  programs: [],
  isLoading: false,
  error: null,
  participantsLoading: false,
  participantsSaving: false,
  participantsError: null,
  participants: [],
  participantsPagination: { currentPage: 0, pageSize: 50, totalItems: 0, totalPages: 0, hasNext: false, hasPrevious: false },
}),

async loadCompanyProgram(companyProgramId: string) {
  const response = await companyProgramsApi.getCompanyProgram(companyProgramId)
  if (!response.success || !response.data) {
    throw new Error(response.message || 'Failed to load company program')
  }
  this.currentProgram = response.data
}
```

```vue
<!-- /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/programs/index.vue -->
<Button variant="outline" size="sm" @click="navigateTo(`/app/admin/programs/${program.id}`)">
  Employees
</Button>
```

- [ ] **Step 4: Run the frontend build and manual browser verification**

Run:
```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
npm run build
```

Then verify manually with the local stack:
```bash
cd /Users/macbookpro/IdeaProjects/ProsperMentor
./gradlew bootRun
```

```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
NUXT_PUBLIC_API_BASE_URL=http://localhost:8080/api npm run dev -- --port 3001
```

Expected manual checks:
- `Settings > Plans` shows session wallet purchase UI, not seat UI
- `/app/admin/participants` shows allocations and no `Program Context` or `Current Roster`
- `/app/admin/programs/:programId` shows an `Employees` tab with roster management

- [ ] **Step 5: Commit**

```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
git add pages/app/admin/participants.vue \
  components/app/admin/CompanyProgramEmployeesPanel.vue \
  pages/app/admin/programs/[programId]/index.vue \
  pages/app/admin/programs/index.vue \
  store/modules/company-programs.ts
git commit -m "feat: move employee allocations and program rosters to dedicated pages"
```

### Task 7: Run full-stack verification and cleanup compatibility edges

**Files:**
- Modify: `src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/index.vue`
- Modify: `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/users/index.vue`
- Test: `src/test/java/com/prosper/prospermentor/service/CompanySessionWalletServiceTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/CompanySubscriptionServiceSessionWalletTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/EmployeeSessionAllocationServiceTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/SubscriptionServiceCorporateAllocationTest.java`
- Test: `src/test/java/com/prosper/prospermentor/service/SessionBookingServiceCorporateAllocationTest.java`

- [ ] **Step 1: Add temporary compatibility fields only where still required**

```java
// src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java
private Map<String, Object> buildCompanySubscriptionPayload(CompanySubscription companySubscription) {
    Map<String, Object> payload = new LinkedHashMap<>();
    CompanySessionWallet wallet = companySessionWalletRepository.findByCompanySubscriptionId(companySubscription.getId()).orElse(null);
    payload.put("id", companySubscription.getId());
    payload.put("companyId", companySubscription.getCompany().getId());
    payload.put("planId", companySubscription.getPlan() != null ? companySubscription.getPlan().getId() : null);
    payload.put("planName", companySubscription.getPlan() != null ? companySubscription.getPlan().getName() : null);
    payload.put("status", companySubscription.getStatus());
    payload.put("wallet", wallet != null ? buildWalletPayload(wallet) : null);
    payload.put("seatsPurchased", 0); // temporary compatibility for older frontend readers
    payload.put("activeSeats", 0);
    payload.put("availableSeats", 0);
    return payload;
}
```

- [ ] **Step 2: Run the backend test suite for the new slice**

Run:
```bash
./gradlew test \
  --tests com.prosper.prospermentor.service.CompanySessionWalletServiceTest \
  --tests com.prosper.prospermentor.service.CompanySubscriptionServiceSessionWalletTest \
  --tests com.prosper.prospermentor.service.EmployeeSessionAllocationServiceTest \
  --tests com.prosper.prospermentor.service.SubscriptionServiceCorporateAllocationTest \
  --tests com.prosper.prospermentor.service.SessionBookingServiceCorporateAllocationTest
```

Expected: PASS with all corporate wallet and allocation tests green.

- [ ] **Step 3: Run frontend build one more time**

Run:
```bash
cd /Users/macbookpro/WebstormProjects/myProsperV2
npm run build
```

Expected: PASS with no seat-based API type errors remaining.

- [ ] **Step 4: Perform end-to-end regression checks**

Manual checks:
- Buy sessions from `Settings > Plans`
- Confirm invoice payload uses `sessionCount`
- Allocate sessions to an employee from `/app/admin/participants`
- Book a company-funded session as that employee
- Cancel the booking
- Confirm the employee allocation balance returns by `1`
- Open a company program detail page and manage its roster from the `Employees` tab

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/prosper/prospermentor/service/CompanySubscriptionService.java \
  /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/index.vue \
  /Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/users/index.vue
git commit -m "chore: finalize corporate session wallet rollout"
```

---

## Self-Review

### Spec coverage

- Single corporate plan with `price per session`: Task 2 and Task 5
- Shared wallet with persistent transaction history: Task 1 and Task 2
- Employee allocations and withdrawals: Task 3 and Task 6
- Booking confirmation consumption: Task 4
- Cancellation return: Task 4
- Remove `Program Context` and `Current Roster` from employees page: Task 6
- Move program roster management into company program detail: Task 6
- Dashboard and legacy admin screens not left broken: Task 5 and Task 7

### Placeholder scan

- No `TBD`, `TODO`, or “similar to above” placeholders left in the task steps.
- Every task includes concrete files, commands, and code blocks.

### Type consistency

- Corporate purchase request uses `sessionCount` consistently.
- Wallet snapshot uses `sessionsPurchased`, `sessionsAllocated`, `sessionsAvailable`.
- Employee allocation snapshot uses `allocatedTotal`, `consumedTotal`, `availableBalance`.
