package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CreateCompanyRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySignupIntent;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.repository.CompanySignupIntentRepository;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CompanySignupIntentService {

    private final CompanySignupIntentRepository companySignupIntentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final CompanyService companyService;

    public CompanySignupIntentService(CompanySignupIntentRepository companySignupIntentRepository,
                                      SubscriptionPlanRepository subscriptionPlanRepository,
                                      CompanyService companyService) {
        this.companySignupIntentRepository = companySignupIntentRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.companyService = companyService;
    }

    public CompanySignupIntent createIntent(String companyName,
                                            String workEmail,
                                            String phoneNumber,
                                            String adminFirstName,
                                            String adminLastName,
                                            UUID targetPlanId,
                                            Integer targetSessionCount) {
        SubscriptionPlan plan = null;
        if (targetPlanId != null) {
            plan = subscriptionPlanRepository.findById(targetPlanId)
                    .filter(SubscriptionPlan::supportsCorporatePurchases)
                    .orElseThrow(() -> new IllegalArgumentException("Selected corporate plan is not available"));
        }

        Company company = companyService.createPendingCompanyRegistration(
                new CreateCompanyRequest(
                        companyName,
                        workEmail,
                        phoneNumber,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        CompanySignupIntent intent = new CompanySignupIntent();
        intent.setCompany(company);
        intent.setToken(UUID.randomUUID().toString());
        intent.setCompanyRegistrationToken(company.getRegistrationToken());
        intent.setAdminEmail(workEmail);
        intent.setAdminFirstName(adminFirstName);
        intent.setAdminLastName(adminLastName);
        intent.setAdminPhoneNumber(phoneNumber);
        intent.setTargetPlanId(plan != null ? plan.getId() : null);
        intent.setTargetSessionCount(targetSessionCount);
        intent.setStatus(CompanySignupIntent.SignupIntentStatus.PENDING);
        intent.setExpiresAt(LocalDateTime.now().plusDays(7));
        return companySignupIntentRepository.save(intent);
    }

    @Transactional(readOnly = true)
    public CompanySignupIntent requireActiveIntent(String token) {
        CompanySignupIntent intent = requireExistingIntent(token);

        if (intent.getStatus() != CompanySignupIntent.SignupIntentStatus.PENDING) {
            throw new IllegalStateException("Company signup intent is no longer active");
        }

        return validateNotExpired(intent);
    }

    @Transactional(readOnly = true)
    public CompanySignupIntent requirePurchasableIntent(String token) {
        CompanySignupIntent intent = requireExistingIntent(token);

        if (intent.getStatus() != CompanySignupIntent.SignupIntentStatus.PENDING
                && intent.getStatus() != CompanySignupIntent.SignupIntentStatus.COMPLETED) {
            throw new IllegalStateException("Company signup intent is no longer available for activation");
        }

        return validateNotExpired(intent);
    }

    private CompanySignupIntent requireExistingIntent(String token) {
        CompanySignupIntent intent = companySignupIntentRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Company signup intent not found"));
        return intent;
    }

    private CompanySignupIntent validateNotExpired(CompanySignupIntent intent) {
        if (intent.getExpiresAt().isBefore(LocalDateTime.now())) {
            intent.setStatus(CompanySignupIntent.SignupIntentStatus.EXPIRED);
            companySignupIntentRepository.save(intent);
            throw new IllegalStateException("Company signup intent has expired");
        }

        return intent;
    }

    public void markCompleted(String token, UUID linkedUserId, UUID linkedProfileId) {
        CompanySignupIntent intent = requireActiveIntent(token);
        intent.setStatus(CompanySignupIntent.SignupIntentStatus.COMPLETED);
        intent.setLinkedUserId(linkedUserId);
        intent.setLinkedProfileId(linkedProfileId);
        intent.setCompletedAt(LocalDateTime.now());
        companySignupIntentRepository.save(intent);
    }

    public CompanySignupIntent updateTargetSessionCount(String token, Integer targetSessionCount) {
        CompanySignupIntent intent = requirePurchasableIntent(token);
        intent.setTargetSessionCount(targetSessionCount);
        return companySignupIntentRepository.save(intent);
    }

    public Map<String, Object> toPublicPayload(CompanySignupIntent intent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", intent.getToken());
        payload.put("companyRegistrationToken", intent.getCompanyRegistrationToken());
        payload.put("companyName", intent.getCompany().getName());
        payload.put("workEmail", intent.getAdminEmail());
        payload.put("firstName", intent.getAdminFirstName());
        payload.put("lastName", intent.getAdminLastName());
        payload.put("planId", intent.getTargetPlanId());
        payload.put("sessionCount", intent.getTargetSessionCount());
        payload.put("status", intent.getStatus() != null ? intent.getStatus().name() : null);
        payload.put("expiresAt", intent.getExpiresAt());
        return payload;
    }
}
