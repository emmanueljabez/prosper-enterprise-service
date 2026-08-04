package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanySubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, UUID> {

    List<CompanySubscription> findByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    @Query("SELECT cs FROM CompanySubscription cs " +
            "WHERE cs.company.id = :companyId " +
            "AND cs.status = 'ACTIVE' " +
            "AND (cs.startDate IS NULL OR cs.startDate <= :now) " +
            "AND (cs.endDate IS NULL OR cs.endDate >= :now) " +
            "ORDER BY cs.createdAt DESC")
    List<CompanySubscription> findActiveByCompanyId(@Param("companyId") UUID companyId,
                                                    @Param("now") LocalDateTime now);

    @Query("SELECT cs FROM CompanySubscription cs " +
            "WHERE cs.id = :id AND cs.company.id = :companyId")
    Optional<CompanySubscription> findByIdAndCompanyId(@Param("id") UUID id,
                                                       @Param("companyId") UUID companyId);
}
