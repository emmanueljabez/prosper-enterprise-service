package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanySubscriptionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySubscriptionMemberRepository extends JpaRepository<CompanySubscriptionMember, UUID> {

    List<CompanySubscriptionMember> findByCompanySubscription_IdOrderByAssignedAtAsc(UUID companySubscriptionId);

    Optional<CompanySubscriptionMember> findByCompanySubscription_IdAndProfile_Id(UUID companySubscriptionId, UUID profileId);

    long countByCompanySubscription_IdAndStatus(UUID companySubscriptionId,
                                                CompanySubscriptionMember.CompanySubscriptionMemberStatus status);

    @Query("SELECT csm FROM CompanySubscriptionMember csm " +
            "WHERE csm.profile.id = :profileId " +
            "AND csm.status = 'ACTIVE' " +
            "AND csm.companySubscription.status = 'ACTIVE' " +
            "AND (csm.companySubscription.startDate IS NULL OR csm.companySubscription.startDate <= :now) " +
            "AND (csm.companySubscription.endDate IS NULL OR csm.companySubscription.endDate >= :now) " +
            "ORDER BY csm.assignedAt DESC")
    List<CompanySubscriptionMember> findActiveMembershipsByProfileId(@Param("profileId") UUID profileId,
                                                                     @Param("now") LocalDateTime now);

    @Query("SELECT csm FROM CompanySubscriptionMember csm " +
            "WHERE csm.companySubscription.id = :companySubscriptionId " +
            "AND csm.status = 'ACTIVE'")
    List<CompanySubscriptionMember> findActiveByCompanySubscriptionId(@Param("companySubscriptionId") UUID companySubscriptionId);
}
