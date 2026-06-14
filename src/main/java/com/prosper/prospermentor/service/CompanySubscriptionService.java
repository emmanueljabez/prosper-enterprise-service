package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySessionWalletTransaction;
import com.prosper.prospermentor.entity.CompanySessionWallet;
import com.prosper.prospermentor.entity.CompanySubscription;
import com.prosper.prospermentor.entity.CompanySubscriptionMember;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletTransactionRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionMemberRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionRepository;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class CompanySubscriptionService {

    private static final String CONTEXT_PURCHASE = "COMPANY_SUBSCRIPTION_PURCHASE";
    private static final String CONTEXT_CHANGE = "COMPANY_SUBSCRIPTION_CHANGE";
    private static final String CONTEXT_RENEWAL = "COMPANY_SUBSCRIPTION_RENEWAL";
    private static final int DEFAULT_BILLING_DASHBOARD_PAGE_SIZE = 10;
    private static final int MAX_BILLING_DASHBOARD_PAGE_SIZE = 50;
    private static final int MONTHLY_TREND_MONTH_COUNT = 6;
    private static final int PROJECTED_USAGE_WINDOW_DAYS = 30;
    private static final BigDecimal USD_EXCHANGE_RATE = new BigDecimal("133.00");

    private final CompanySubscriptionRepository companySubscriptionRepository;
    private final CompanySubscriptionMemberRepository companySubscriptionMemberRepository;
    private final CompanyRepository companyRepository;
    private final CompanySessionWalletRepository companySessionWalletRepository;
    private final CompanySessionWalletTransactionRepository companySessionWalletTransactionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ProfileRepository profileRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRepository sessionRepository;
    private final InvoiceService invoiceService;
    private final CompanySessionWalletService companySessionWalletService;
    private final ObjectMapper objectMapper;

    public CompanySubscriptionService(CompanySubscriptionRepository companySubscriptionRepository,
                                      CompanySubscriptionMemberRepository companySubscriptionMemberRepository,
                                      CompanyRepository companyRepository,
                                      CompanySessionWalletRepository companySessionWalletRepository,
                                      CompanySessionWalletTransactionRepository companySessionWalletTransactionRepository,
                                      SubscriptionPlanRepository subscriptionPlanRepository,
                                      ProfileRepository profileRepository,
                                      InvoiceRepository invoiceRepository,
                                      PaymentRepository paymentRepository,
                                      SessionRepository sessionRepository,
                                      InvoiceService invoiceService,
                                      CompanySessionWalletService companySessionWalletService,
                                      ObjectMapper objectMapper) {
        this.companySubscriptionRepository = companySubscriptionRepository;
        this.companySubscriptionMemberRepository = companySubscriptionMemberRepository;
        this.companyRepository = companyRepository;
        this.companySessionWalletRepository = companySessionWalletRepository;
        this.companySessionWalletTransactionRepository = companySessionWalletTransactionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.profileRepository = profileRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.sessionRepository = sessionRepository;
        this.invoiceService = invoiceService;
        this.companySessionWalletService = companySessionWalletService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<CompanySubscriptionMember> findActiveMembershipForUser(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<CompanySubscriptionMember> memberships =
                companySubscriptionMemberRepository.findActiveMembershipsByProfileId(userId, LocalDateTime.now());
        return memberships.stream().findFirst();
    }

    public Map<String, Object> createCompanySubscription(UUID companyId,
                                                         UUID planId,
                                                         int sessionCount,
                                                         BillingInterval billingInterval,
                                                         UUID createdByUserId,
                                                         String redirectSuccessUrl,
                                                         String redirectCancelUrl) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));

        validateCorporatePlan(plan);
        validateCorporateSessionCount(sessionCount);
        BillingInterval resolvedInterval = billingInterval != null ? billingInterval : BillingInterval.MONTHLY;

        Optional<CompanySubscription> currentCompanySubscription = findCurrentCompanySubscription(companyId);
        CompanySubscription companySubscription = currentCompanySubscription
                .orElseGet(() -> initializeCompanySubscription(company, createdByUserId));

        String invoiceContext = CONTEXT_PURCHASE;

        companySubscription.setPlan(plan);
        companySubscription.setBillingInterval(resolvedInterval);
        if (currentCompanySubscription.isPresent()) {
            if (companySubscription.getStatus() == CompanySubscription.CompanySubscriptionStatus.ACTIVE) {
                companySubscription.activateCorporateWalletSubscription();
            }
        } else {
            companySubscription.setStatus(CompanySubscription.CompanySubscriptionStatus.PENDING_PAYMENT);
        }
        companySubscription.setCreatedByUserId(createdByUserId);
        companySubscription.setSeatsPurchased(sessionCount);

        companySubscription = companySubscriptionRepository.save(companySubscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invoiceContext", invoiceContext);
        metadata.put("source", "COMPANY_SUBSCRIPTION");
        metadata.put("companyId", company.getId());
        metadata.put("companySubscriptionId", companySubscription.getId());
        metadata.put("planId", companySubscription.getPlan() != null ? companySubscription.getPlan().getId() : plan.getId());
        metadata.put("targetPlanId", plan.getId());
        metadata.put("sessionCount", sessionCount);
        metadata.put("targetSessionCount", sessionCount);
        metadata.put("seatCount", sessionCount);
        metadata.put("targetSeatCount", sessionCount);
        metadata.put("billingInterval", resolvedInterval.name());
        metadata.put("changeType", "TOP_UP");

        Invoice invoice = invoiceService.createInvoice(
                createdByUserId,
                company.getId(),
                resolveCorporateAmount(plan, sessionCount),
                normalizeCurrency(plan.getCurrency()),
                buildCorporateInvoiceDescription(plan, sessionCount, invoiceContext, "TOP_UP", resolvedInterval),
                metadata,
                redirectSuccessUrl,
                redirectCancelUrl,
                LocalDateTime.now().plusDays(7)
        );

        companySubscription.setLatestInvoiceId(invoice.getId());
        companySubscriptionRepository.save(companySubscription);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companySubscription", buildCompanySubscriptionPayload(companySubscription));
        response.put("invoiceId", invoice.getId());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("publicToken", invoice.getPublicToken());
        response.put("paymentUrl", invoiceService.buildPaymentUrl(invoice));
        response.put("amount", invoice.getAmount());
        response.put("currency", invoice.getCurrency());
        response.put("changeType", "TOP_UP");
        response.put("sessionCount", sessionCount);
        response.put("pricePerSession", plan.getCost());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCompanySubscriptions(UUID companyId) {
        return companySubscriptionRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .map(this::buildCompanySubscriptionPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCompanySubscriptionDetails(UUID companySubscriptionId) {
        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));

        Map<String, Object> payload = buildCompanySubscriptionPayload(companySubscription);
        payload.put("members", getMembers(companySubscriptionId));
        return payload;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMembers(UUID companySubscriptionId) {
        return companySubscriptionMemberRepository
                .findByCompanySubscription_IdOrderByAssignedAtAsc(companySubscriptionId)
                .stream()
                .map(this::buildMemberPayload)
                .toList();
    }

    public Map<String, Object> assignMember(UUID companySubscriptionId, UUID profileId, UUID assignedByUserId) {
        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));

        if (!companySubscription.isActive()) {
            throw new IllegalStateException("Company subscription is not active");
        }

        Profile profile = profileRepository.findByIdWithCompany(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (profile.getCompany() == null || !companySubscription.getCompany().getId().equals(profile.getCompany().getId())) {
            throw new IllegalStateException("Profile is not linked to this company");
        }

        Optional<CompanySubscriptionMember> existingMembershipOpt =
                companySubscriptionMemberRepository.findByCompanySubscription_IdAndProfile_Id(companySubscriptionId, profileId);

        if (existingMembershipOpt.isPresent()
                && existingMembershipOpt.get().getStatus() == CompanySubscriptionMember.CompanySubscriptionMemberStatus.ACTIVE) {
            throw new IllegalStateException("Profile already has an active seat in this company subscription");
        }

        long activeSeats = companySubscriptionMemberRepository.countByCompanySubscription_IdAndStatus(
                companySubscriptionId,
                CompanySubscriptionMember.CompanySubscriptionMemberStatus.ACTIVE
        );
        if (activeSeats >= companySubscription.getSeatsPurchased()) {
            throw new IllegalStateException("No available seats remaining in this company subscription");
        }

        CompanySubscriptionMember member = existingMembershipOpt.orElseGet(CompanySubscriptionMember::new);
        member.setCompanySubscription(companySubscription);
        member.setProfile(profile);
        member.activate(assignedByUserId);
        member = companySubscriptionMemberRepository.save(member);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("member", buildMemberPayload(member));
        response.put("subscription", buildCompanySubscriptionPayload(companySubscription));
        return response;
    }

    public void revokeMember(UUID companySubscriptionId, UUID profileId) {
        CompanySubscriptionMember member = companySubscriptionMemberRepository
                .findByCompanySubscription_IdAndProfile_Id(companySubscriptionId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("Seat assignment not found"));

        if (member.getStatus() == CompanySubscriptionMember.CompanySubscriptionMemberStatus.REVOKED) {
            return;
        }

        member.revoke();
        companySubscriptionMemberRepository.save(member);
    }

    public void consumeMemberSession(UUID memberId) {
        CompanySubscriptionMember member = companySubscriptionMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription member not found"));

        if (member.getCompanySubscription() != null
                && member.getCompanySubscription().getPlan() != null
                && member.getCompanySubscription().getPlan().isUnlimited()) {
            return;
        }

        member.incrementSessionsUsed();
        companySubscriptionMemberRepository.save(member);
    }

    public Map<String, Object> createRenewalInvoice(UUID companySubscriptionId,
                                                    UUID requestedByUserId,
                                                    BillingInterval billingInterval,
                                                    String redirectSuccessUrl,
                                                    String redirectCancelUrl) {
        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));

        SubscriptionPlan plan = companySubscription.getPlan();
        validateCorporatePlan(plan);
        int sessionCount = companySubscription.getSeatsPurchased() != null ? companySubscription.getSeatsPurchased() : 0;
        validateCorporateSessionCount(sessionCount);
        BillingInterval resolvedInterval = billingInterval != null ? billingInterval : resolveBillingInterval(companySubscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invoiceContext", CONTEXT_RENEWAL);
        metadata.put("source", "COMPANY_SUBSCRIPTION_RENEWAL");
        metadata.put("companyId", companySubscription.getCompany().getId());
        metadata.put("companySubscriptionId", companySubscription.getId());
        metadata.put("planId", plan.getId());
        metadata.put("sessionCount", sessionCount);
        metadata.put("targetSessionCount", sessionCount);
        metadata.put("seatCount", sessionCount);
        metadata.put("billingInterval", resolvedInterval.name());

        Invoice invoice = invoiceService.createInvoice(
                requestedByUserId,
                companySubscription.getCompany().getId(),
                resolveCorporateAmount(plan, sessionCount),
                normalizeCurrency(plan.getCurrency()),
                buildCorporateInvoiceDescription(plan, sessionCount, CONTEXT_RENEWAL, "RENEWAL", resolvedInterval),
                metadata,
                redirectSuccessUrl,
                redirectCancelUrl,
                LocalDateTime.now().plusDays(7)
        );

        companySubscription.setLatestInvoiceId(invoice.getId());
        companySubscriptionRepository.save(companySubscription);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companySubscription", buildCompanySubscriptionPayload(companySubscription));
        response.put("invoiceId", invoice.getId());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("publicToken", invoice.getPublicToken());
        response.put("paymentUrl", invoiceService.buildPaymentUrl(invoice));
        response.put("amount", invoice.getAmount());
        response.put("currency", invoice.getCurrency());
        return response;
    }

    public void applyInvoicePayment(UUID companySubscriptionId,
                                    String invoiceContext,
                                    BillingInterval billingInterval,
                                    UUID targetPlanId,
                                    Integer targetSeatCount,
                                    Integer targetSessionCount) {
        if (companySubscriptionId == null || invoiceContext == null || invoiceContext.isBlank()) {
            return;
        }

        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));

        String normalizedContext = invoiceContext.trim().toUpperCase(Locale.ROOT);
        SubscriptionPlan targetPlan = targetPlanId != null
                ? subscriptionPlanRepository.findById(targetPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"))
                : companySubscription.getPlan();

        validateCorporatePlan(targetPlan);

        int resolvedSessionCount = targetSessionCount != null && targetSessionCount > 0
                ? targetSessionCount
                : (targetSeatCount != null && targetSeatCount > 0
                ? targetSeatCount
                : (companySubscription.getSeatsPurchased() != null ? companySubscription.getSeatsPurchased() : 0));

        validateCorporateSessionCount(resolvedSessionCount);

        BillingInterval resolvedInterval = billingInterval != null ? billingInterval : resolveBillingInterval(companySubscription);

        companySubscription.setPlan(targetPlan);
        companySubscription.setSeatsPurchased(resolvedSessionCount);
        companySubscription.setBillingInterval(resolvedInterval);

        if (CONTEXT_PURCHASE.equals(normalizedContext) || CONTEXT_CHANGE.equals(normalizedContext)) {
            companySubscription.activateCorporateWalletSubscription();
        } else if (CONTEXT_RENEWAL.equals(normalizedContext)) {
            companySubscription.activateCorporateWalletSubscription();
        } else {
            return;
        }

        companySubscriptionRepository.save(companySubscription);
        companySessionWalletService.recordPurchase(
                companySubscription.getId(),
                companySubscription.getCompany().getId(),
                targetPlan.getCost(),
                resolvedSessionCount,
                null,
                normalizedContext
        );
        log.info("Applied {} invoice payment for company subscription {}", normalizedContext, companySubscriptionId);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getCurrentCompanySubscriptionForUser(UUID userId) {
        return findActiveMembershipForUser(userId)
                .map(member -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("member", buildMemberPayload(member));
                    payload.put("subscription", buildCompanySubscriptionPayload(member.getCompanySubscription()));
                    return payload;
                });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCompanyBillingDashboard(UUID companyId, Integer page, Integer size) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Optional<CompanySubscription> currentSubscriptionOpt = findCurrentCompanySubscription(companyId);
        CompanySubscription currentSubscription = currentSubscriptionOpt.orElseGet(() ->
                companySubscriptionRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                        .findFirst()
                        .orElse(null)
        );

        CompanySessionWallet wallet = resolveCompanyWallet(companyId, currentSubscription);
        BigDecimal pricePerSession = resolvePricePerSession(wallet, currentSubscription);
        String currency = normalizeCurrency(currentSubscription != null && currentSubscription.getPlan() != null
                ? currentSubscription.getPlan().getCurrency()
                : "KES");

        int sessionsPurchased = wallet != null
                ? safeInteger(wallet.getSessionsPurchasedTotal())
                : safeInteger(currentSubscription != null ? currentSubscription.getSeatsPurchased() : 0);
        int sessionsAllocated = wallet != null ? safeInteger(wallet.getSessionsAllocatedTotal()) : 0;
        int sessionsReturned = wallet != null ? safeInteger(wallet.getSessionsReturnedTotal()) : 0;
        int sessionsAvailable = wallet != null ? safeInteger(wallet.getSessionsAvailable()) : 0;
        int sessionsReserved = Math.max(0, sessionsAllocated - sessionsReturned);
        int sessionsUsed = Math.max(0, sessionsPurchased - sessionsAvailable);

        BigDecimal availableBalance = computeAmount(pricePerSession, sessionsAvailable);
        BigDecimal usedBalance = computeAmount(pricePerSession, sessionsUsed);
        BigDecimal availableBalanceUsd = availableBalance
                .divide(USD_EXCHANGE_RATE, 2, RoundingMode.HALF_UP);

        List<Profile> companyProfiles = profileRepository.findByCompanyId(companyId);
        Map<UUID, Profile> employeesById = companyProfiles.stream()
                .filter(this::isEmployeeProfile)
                .collect(Collectors.toMap(Profile::getId, profile -> profile, (left, right) -> left));
        List<UUID> employeeIds = new ArrayList<>(employeesById.keySet());

        List<Session> companySessions = employeeIds.isEmpty()
                ? List.of()
                : sessionRepository.findByMenteeIdIn(employeeIds);

        List<Session> completedSessions = companySessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.COMPLETED)
                .filter(session -> session.getScheduledStart() != null)
                .toList();

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(MONTHLY_TREND_MONTH_COUNT - 1L);
        List<Map<String, Object>> monthlySpendTrend = buildMonthlySpendTrend(
                completedSessions,
                pricePerSession,
                startMonth,
                currentMonth
        );
        List<Map<String, Object>> departmentAllocation = buildDepartmentAllocation(
                completedSessions,
                employeesById,
                pricePerSession
        );
        Map<String, Object> projectedUsage = buildProjectedUsage(
                completedSessions,
                pricePerSession,
                employeesById.size()
        );

        List<CompanySessionWalletTransaction> walletTransactions = wallet != null
                ? companySessionWalletTransactionRepository.findByWallet_IdOrderByCreatedAtDesc(wallet.getId())
                : List.of();
        double balanceChangePercent = calculatePurchaseDeltaVsLastMonth(walletTransactions, pricePerSession);

        int safePage = Math.max(page != null ? page : 0, 0);
        int safeSize = Math.max(1, Math.min(size != null ? size : DEFAULT_BILLING_DASHBOARD_PAGE_SIZE, MAX_BILLING_DASHBOARD_PAGE_SIZE));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> paymentPage = paymentRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
        List<Map<String, Object>> recentTransactions = buildRecentTransactions(paymentPage.getContent());

        int triggerSessions = sessionsPurchased > 0
                ? Math.max(1, (int) Math.ceil(sessionsPurchased * 0.2d))
                : 10;
        int topUpSessions = currentSubscription != null
                ? Math.max(1, safeInteger(currentSubscription.getSeatsPurchased()))
                : Math.max(50, triggerSessions * 2);

        Map<String, Object> autoRefill = new LinkedHashMap<>();
        autoRefill.put("enabled", currentSubscription != null && Boolean.TRUE.equals(currentSubscription.getAutoRenew()));
        autoRefill.put("triggerSessions", triggerSessions);
        autoRefill.put("triggerAmount", computeAmount(pricePerSession, triggerSessions));
        autoRefill.put("topUpSessions", topUpSessions);
        autoRefill.put("topUpAmount", computeAmount(pricePerSession, topUpSessions));
        autoRefill.put("currency", currency);

        Map<String, Object> subscriptionData = currentSubscription != null
                ? buildCompanySubscriptionPayload(currentSubscription)
                : null;

        Map<String, Object> walletData = new LinkedHashMap<>();
        walletData.put("pricePerSession", pricePerSession);
        walletData.put("currency", currency);
        walletData.put("sessionsPurchased", sessionsPurchased);
        walletData.put("sessionsAllocated", sessionsAllocated);
        walletData.put("sessionsReturned", sessionsReturned);
        walletData.put("sessionsReserved", sessionsReserved);
        walletData.put("sessionsAvailable", sessionsAvailable);
        walletData.put("sessionsUsed", sessionsUsed);
        walletData.put("capacityPercent", percentage(sessionsUsed, sessionsPurchased));
        walletData.put("availableBalance", availableBalance);
        walletData.put("usedBalance", usedBalance);
        walletData.put("availableBalanceUsd", availableBalanceUsd);
        walletData.put("balanceChangePercent", BigDecimal.valueOf(balanceChangePercent).setScale(1, RoundingMode.HALF_UP));

        Map<String, Object> transactionPagination = new LinkedHashMap<>();
        transactionPagination.put("page", paymentPage.getNumber());
        transactionPagination.put("size", paymentPage.getSize());
        transactionPagination.put("totalItems", paymentPage.getTotalElements());
        transactionPagination.put("totalPages", paymentPage.getTotalPages());
        transactionPagination.put("hasNext", paymentPage.hasNext());
        transactionPagination.put("hasPrevious", paymentPage.hasPrevious());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyId", company.getId());
        payload.put("companyName", company.getName());
        payload.put("subscription", subscriptionData);
        payload.put("wallet", walletData);
        payload.put("projectedUsage", projectedUsage);
        payload.put("autoRefill", autoRefill);
        payload.put("recentTransactions", recentTransactions);
        payload.put("recentTransactionsPagination", transactionPagination);
        payload.put("monthlySpendTrend", monthlySpendTrend);
        payload.put("departmentAllocation", departmentAllocation);
        payload.put("snapshotAt", LocalDateTime.now());
        return payload;
    }

    public Map<String, Object> updateCompanySubscriptionAutoRenew(UUID companySubscriptionId, Boolean autoRenew) {
        if (autoRenew == null) {
            throw new IllegalArgumentException("autoRenew is required");
        }

        CompanySubscription companySubscription = companySubscriptionRepository.findById(companySubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Company subscription not found"));

        companySubscription.setAutoRenew(autoRenew);
        CompanySubscription updatedSubscription = companySubscriptionRepository.save(companySubscription);
        return buildCompanySubscriptionPayload(updatedSubscription);
    }

    public UUID extractCompanySubscriptionIdFromInvoice(Invoice invoice) {
        if (invoice == null || invoice.getMetadata() == null || invoice.getMetadata().isBlank()) {
            return null;
        }

        try {
            JsonNode metadata = objectMapper.readTree(invoice.getMetadata());
            JsonNode value = metadata.get("companySubscriptionId");
            if (value == null || value.isNull()) {
                return null;
            }
            return UUID.fromString(value.asText());
        } catch (Exception ex) {
            log.warn("Failed to read companySubscriptionId from invoice {} metadata: {}", invoice.getId(), ex.getMessage());
            return null;
        }
    }

    private BigDecimal resolveCorporateAmount(SubscriptionPlan plan, int sessionCount) {
        BigDecimal cost = plan.getCost() != null ? plan.getCost() : BigDecimal.ZERO;
        return cost.multiply(BigDecimal.valueOf(sessionCount)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private CompanySubscription initializeCompanySubscription(Company company, UUID createdByUserId) {
        CompanySubscription companySubscription = new CompanySubscription();
        companySubscription.setCompany(company);
        companySubscription.setStatus(CompanySubscription.CompanySubscriptionStatus.PENDING_PAYMENT);
        companySubscription.setAutoRenew(false);
        companySubscription.setCreatedByUserId(createdByUserId);
        return companySubscription;
    }

    private Optional<CompanySubscription> findCurrentCompanySubscription(UUID companyId) {
        return companySubscriptionRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .filter(subscription -> subscription.getStatus() == CompanySubscription.CompanySubscriptionStatus.ACTIVE
                        || subscription.getStatus() == CompanySubscription.CompanySubscriptionStatus.PENDING_PAYMENT
                        || subscription.getStatus() == CompanySubscription.CompanySubscriptionStatus.SUSPENDED)
                .sorted((left, right) -> Integer.compare(statusPriority(left.getStatus()), statusPriority(right.getStatus())))
                .findFirst();
    }

    private int statusPriority(CompanySubscription.CompanySubscriptionStatus status) {
        if (status == CompanySubscription.CompanySubscriptionStatus.ACTIVE) {
            return 0;
        }
        if (status == CompanySubscription.CompanySubscriptionStatus.PENDING_PAYMENT) {
            return 1;
        }
        if (status == CompanySubscription.CompanySubscriptionStatus.SUSPENDED) {
            return 2;
        }
        return 3;
    }

    private void validateCorporatePlan(SubscriptionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Subscription plan not found");
        }
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalStateException("Subscription plan is not active");
        }
        if (!plan.supportsCorporatePurchases()) {
            throw new IllegalStateException("Selected plan is not available for corporate purchase");
        }
    }

    private void validateCorporateSessionCount(int sessionCount) {
        if (sessionCount <= 0) {
            throw new IllegalArgumentException("sessionCount must be greater than 0");
        }
    }

    private String buildCorporateInvoiceDescription(SubscriptionPlan plan,
                                                   int sessionCount,
                                                   String invoiceContext,
                                                   String changeType,
                                                   BillingInterval billingInterval) {
        String action;
        if (CONTEXT_RENEWAL.equals(invoiceContext)) {
            action = "Top up corporate session wallet";
        } else if ("UPGRADE".equalsIgnoreCase(changeType)) {
            action = "Top up corporate session wallet";
        } else if ("DOWNGRADE".equalsIgnoreCase(changeType)) {
            action = "Top up corporate session wallet";
        } else if ("CHANGE".equalsIgnoreCase(changeType)) {
            action = "Top up corporate session wallet";
        } else {
            action = "Purchase corporate sessions";
        }
        String planName = plan != null && plan.getName() != null ? plan.getName() : "Plan";
        return String.format("%s: %s (%d session%s)", action, planName, sessionCount, sessionCount == 1 ? "" : "s");
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "KES";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private BillingInterval resolveBillingInterval(CompanySubscription companySubscription) {
        if (companySubscription == null || companySubscription.getBillingInterval() == null) {
            return BillingInterval.MONTHLY;
        }
        return companySubscription.getBillingInterval();
    }

    private CompanySessionWallet resolveCompanyWallet(UUID companyId, CompanySubscription companySubscription) {
        if (companySubscription != null && companySubscription.getId() != null) {
            Optional<CompanySessionWallet> bySubscription =
                    companySessionWalletRepository.findByCompanySubscription_Id(companySubscription.getId());
            if (bySubscription.isPresent()) {
                return bySubscription.get();
            }
        }

        return companySessionWalletRepository.findByCompany_Id(companyId).orElse(null);
    }

    private BigDecimal resolvePricePerSession(CompanySessionWallet wallet, CompanySubscription companySubscription) {
        if (wallet != null && wallet.getPricePerSessionSnapshot() != null
                && wallet.getPricePerSessionSnapshot().compareTo(BigDecimal.ZERO) > 0) {
            return wallet.getPricePerSessionSnapshot().setScale(2, RoundingMode.HALF_UP);
        }

        if (companySubscription != null
                && companySubscription.getPlan() != null
                && companySubscription.getPlan().getCost() != null) {
            return companySubscription.getPlan().getCost().setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAmount(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private int percentage(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            return 0;
        }

        return (int) Math.round((numerator * 100.0d) / denominator);
    }

    private boolean isEmployeeProfile(Profile profile) {
        String role = profile != null ? profile.getRole() : null;
        if (role == null) {
            return false;
        }

        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return "employee".equals(normalized) || "mentee".equals(normalized);
    }

    private String buildProfileDepartment(Profile profile) {
        if (profile == null) {
            return "General";
        }

        if (profile.getIndustry() != null && !profile.getIndustry().isBlank()) {
            return profile.getIndustry().trim();
        }

        if (profile.getLocation() != null && !profile.getLocation().isBlank()) {
            return profile.getLocation().trim();
        }

        return "General";
    }

    private Map<String, Object> buildProjectedUsage(List<Session> completedSessions,
                                                    BigDecimal pricePerSession,
                                                    int activeEmployees) {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(PROJECTED_USAGE_WINDOW_DAYS);
        long sessionsInWindow = completedSessions.stream()
                .filter(session -> session.getScheduledStart() != null)
                .filter(session -> !session.getScheduledStart().isBefore(cutoff))
                .count();

        int projectedSessions = (int) Math.max(0, Math.round((sessionsInWindow * 30.0d) / PROJECTED_USAGE_WINDOW_DAYS));
        BigDecimal projectedSpend = computeAmount(pricePerSession, projectedSessions);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowDays", PROJECTED_USAGE_WINDOW_DAYS);
        payload.put("sessionsInWindow", sessionsInWindow);
        payload.put("projectedSessions", projectedSessions);
        payload.put("projectedSpend", projectedSpend);
        payload.put("activeEmployees", activeEmployees);
        return payload;
    }

    private double calculatePurchaseDeltaVsLastMonth(List<CompanySessionWalletTransaction> transactions,
                                                     BigDecimal pricePerSession) {
        if (transactions == null || transactions.isEmpty()) {
            return 0.0d;
        }

        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        BigDecimal currentAmount = BigDecimal.ZERO;
        BigDecimal previousAmount = BigDecimal.ZERO;

        for (CompanySessionWalletTransaction transaction : transactions) {
            if (transaction.getTransactionType() != CompanySessionWalletTransaction.TransactionType.PURCHASE
                    || transaction.getCreatedAt() == null) {
                continue;
            }

            YearMonth month = YearMonth.from(transaction.getCreatedAt().toLocalDate());
            BigDecimal amount = computeAmount(pricePerSession, safeInteger(transaction.getQuantity()));

            if (month.equals(currentMonth)) {
                currentAmount = currentAmount.add(amount);
            } else if (month.equals(previousMonth)) {
                previousAmount = previousAmount.add(amount);
            }
        }

        if (previousAmount.compareTo(BigDecimal.ZERO) > 0) {
            return currentAmount.subtract(previousAmount)
                    .divide(previousAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        if (currentAmount.compareTo(BigDecimal.ZERO) > 0) {
            return 100.0d;
        }

        return 0.0d;
    }

    private List<Map<String, Object>> buildMonthlySpendTrend(List<Session> completedSessions,
                                                             BigDecimal pricePerSession,
                                                             YearMonth startMonth,
                                                             YearMonth endMonth) {
        Map<YearMonth, Integer> sessionsByMonth = new LinkedHashMap<>();
        YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            sessionsByMonth.put(cursor, 0);
            cursor = cursor.plusMonths(1);
        }

        for (Session session : completedSessions) {
            if (session.getScheduledStart() == null) {
                continue;
            }

            YearMonth month = YearMonth.from(session.getScheduledStart().toLocalDate());
            if (month.isBefore(startMonth) || month.isAfter(endMonth)) {
                continue;
            }

            sessionsByMonth.computeIfPresent(month, (key, value) -> value + 1);
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<YearMonth, Integer> entry : sessionsByMonth.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", entry.getKey().toString());
            row.put("label", entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT));
            row.put("sessions", entry.getValue());
            row.put("amount", computeAmount(pricePerSession, entry.getValue()));
            trend.add(row);
        }

        return trend;
    }

    private List<Map<String, Object>> buildDepartmentAllocation(List<Session> completedSessions,
                                                                Map<UUID, Profile> employeesById,
                                                                BigDecimal pricePerSession) {
        Map<String, Integer> sessionsByDepartment = new HashMap<>();
        for (Session session : completedSessions) {
            String department = buildProfileDepartment(employeesById.get(session.getMenteeId()));
            sessionsByDepartment.merge(department, 1, Integer::sum);
        }

        int totalSessions = sessionsByDepartment.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        return sessionsByDepartment.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("department", entry.getKey());
                    row.put("sessions", entry.getValue());
                    row.put("amount", computeAmount(pricePerSession, entry.getValue()));
                    row.put("percentage", percentage(entry.getValue(), totalSessions));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> buildRecentTransactions(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }

        Set<UUID> invoiceIds = payments.stream()
                .map(Payment::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Invoice> invoicesById = invoiceIds.isEmpty()
                ? Map.of()
                : invoiceRepository.findAllById(invoiceIds).stream()
                .collect(Collectors.toMap(Invoice::getId, invoice -> invoice, (left, right) -> left));

        return payments.stream()
                .map(payment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", payment.getId());
                    row.put("transactionId", buildTransactionCode(payment.getId()));
                    row.put("date", payment.getCreatedAt());
                    row.put("description", resolvePaymentDescription(payment));
                    row.put("amount", payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO);
                    row.put("currency", normalizeCurrency(payment.getCurrency()));
                    row.put("status", payment.getStatus() != null ? payment.getStatus().name() : "PENDING");
                    row.put("paymentType", payment.getPaymentType() != null ? payment.getPaymentType().name() : null);
                    row.put("paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null);
                    row.put("reference", resolvePaymentReference(payment));

                    Invoice invoice = payment.getInvoiceId() != null ? invoicesById.get(payment.getInvoiceId()) : null;
                    row.put("invoiceId", payment.getInvoiceId());
                    row.put("invoiceNumber", invoice != null ? invoice.getInvoiceNumber() : null);
                    row.put("invoiceStatus", invoice != null && invoice.getStatus() != null ? invoice.getStatus().name() : null);
                    row.put("invoiceUrl", invoice != null ? invoiceService.buildPaymentUrl(invoice) : null);
                    return row;
                })
                .toList();
    }

    private String resolvePaymentDescription(Payment payment) {
        if (payment == null) {
            return "Corporate wallet transaction";
        }

        if (payment.getDescription() != null && !payment.getDescription().isBlank()) {
            return payment.getDescription().trim();
        }

        if (payment.getPaymentType() == null) {
            return "Corporate wallet transaction";
        }

        return switch (payment.getPaymentType()) {
            case TOP_UP -> "Wallet top-up";
            case SUBSCRIPTION -> "Subscription payment";
            case INVOICE -> "Invoice payment";
            case SESSION_BOOKING -> "Mentoring session charge";
            case UPGRADE -> "Plan upgrade";
            case ADDON -> "Plan add-on";
            case REFUND -> "Refund";
        };
    }

    private String resolvePaymentReference(Payment payment) {
        if (payment == null) {
            return null;
        }

        if (payment.getGatewayReference() != null && !payment.getGatewayReference().isBlank()) {
            return payment.getGatewayReference();
        }

        if (payment.getMpesaReceiptNumber() != null && !payment.getMpesaReceiptNumber().isBlank()) {
            return payment.getMpesaReceiptNumber();
        }

        if (payment.getCheckoutRequestId() != null && !payment.getCheckoutRequestId().isBlank()) {
            return payment.getCheckoutRequestId();
        }

        return buildTransactionCode(payment.getId());
    }

    private String buildTransactionCode(UUID paymentId) {
        if (paymentId == null) {
            return "TX-UNKNOWN";
        }

        String compact = paymentId.toString().replace("-", "").toUpperCase(Locale.ROOT);
        int tokenLength = Math.min(8, compact.length());
        return "TX-" + compact.substring(0, tokenLength);
    }

    private Map<String, Object> buildCompanySubscriptionPayload(CompanySubscription companySubscription) {
        Map<String, Object> payload = new LinkedHashMap<>();
        CompanySessionWallet wallet = companySessionWalletRepository.findByCompanySubscription_Id(companySubscription.getId())
                .orElse(null);
        payload.put("id", companySubscription.getId());
        payload.put("companyId", companySubscription.getCompany() != null ? companySubscription.getCompany().getId() : null);
        payload.put("companyName", companySubscription.getCompany() != null ? companySubscription.getCompany().getName() : null);
        payload.put("planId", companySubscription.getPlan() != null ? companySubscription.getPlan().getId() : null);
        payload.put("planName", companySubscription.getPlan() != null ? companySubscription.getPlan().getName() : null);
        payload.put("status", companySubscription.getStatus());
        payload.put("billingInterval", companySubscription.getBillingInterval());
        payload.put("seatsPurchased", companySubscription.getSeatsPurchased() != null ? companySubscription.getSeatsPurchased() : 0);
        payload.put("activeSeats", 0);
        payload.put("availableSeats", 0);
        payload.put("autoRenew", companySubscription.getAutoRenew());
        payload.put("createdByUserId", companySubscription.getCreatedByUserId());
        payload.put("latestInvoiceId", companySubscription.getLatestInvoiceId());
        payload.put("startDate", companySubscription.getStartDate());
        payload.put("endDate", companySubscription.getEndDate());
        payload.put("currentPeriodStart", companySubscription.getCurrentPeriodStart());
        payload.put("currentPeriodEnd", companySubscription.getCurrentPeriodEnd());
        payload.put("createdAt", companySubscription.getCreatedAt());
        payload.put("updatedAt", companySubscription.getUpdatedAt());

        if (companySubscription.getLatestInvoiceId() != null) {
            invoiceRepository.findById(companySubscription.getLatestInvoiceId()).ifPresent(invoice -> {
                Map<String, Object> latestInvoice = new LinkedHashMap<>();
                latestInvoice.put("invoiceId", invoice.getId());
                latestInvoice.put("invoiceNumber", invoice.getInvoiceNumber());
                latestInvoice.put("publicToken", invoice.getPublicToken());
                latestInvoice.put("status", invoice.getStatus());
                latestInvoice.put("sessionCount", companySubscription.getSeatsPurchased() != null ? companySubscription.getSeatsPurchased() : 0);
                latestInvoice.put("paymentUrl", invoiceService.buildPaymentUrl(invoice));
                payload.put("latestInvoice", latestInvoice);
            });
        }

        payload.put("wallet", buildWalletPayload(wallet));

        return payload;
    }

    private Map<String, Object> buildWalletPayload(CompanySessionWallet wallet) {
        if (wallet == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("walletId", wallet.getId());
        payload.put("companySubscriptionId", wallet.getCompanySubscription() != null ? wallet.getCompanySubscription().getId() : null);
        payload.put("companyId", wallet.getCompany() != null ? wallet.getCompany().getId() : null);
        payload.put("pricePerSession", wallet.getPricePerSessionSnapshot());
        payload.put("sessionsPurchased", wallet.getSessionsPurchasedTotal());
        payload.put("sessionsAllocated", wallet.getSessionsAllocatedTotal());
        payload.put("sessionsReturned", wallet.getSessionsReturnedTotal());
        payload.put("sessionsAvailable", wallet.getSessionsAvailable());
        payload.put("createdAt", wallet.getCreatedAt());
        payload.put("updatedAt", wallet.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> buildMemberPayload(CompanySubscriptionMember member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", member.getId());
        payload.put("profileId", member.getProfile() != null ? member.getProfile().getId() : null);
        payload.put("email", member.getProfile() != null ? member.getProfile().getEmail() : null);
        payload.put("firstName", member.getProfile() != null ? member.getProfile().getFirstName() : null);
        payload.put("lastName", member.getProfile() != null ? member.getProfile().getLastName() : null);
        payload.put("status", member.getStatus());
        payload.put("sessionsUsed", member.getSessionsUsed());
        payload.put("remainingSessions", member.getRemainingSessionsCount());
        payload.put("assignedAt", member.getAssignedAt());
        payload.put("revokedAt", member.getRevokedAt());
        payload.put("assignedByUserId", member.getAssignedByUserId());
        payload.put("companySubscriptionId", member.getCompanySubscription() != null ? member.getCompanySubscription().getId() : null);
        return payload;
    }
}
