package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.BadgeAffiliationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BadgeAffiliationRuleRepository extends JpaRepository<BadgeAffiliationRule, UUID> {
    List<BadgeAffiliationRule> findByCompany_IdAndProfileRoleAndStatus(UUID companyId, String profileRole, String status);

    List<BadgeAffiliationRule> findByStatusOrderByCreatedAtDesc(String status);
}
