package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyMentorProgramScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyMentorProgramScopeRepository extends JpaRepository<CompanyMentorProgramScope, UUID> {

    List<CompanyMentorProgramScope> findByMembership_Id(UUID membershipId);

    List<CompanyMentorProgramScope> findByCompanyProgram_Id(UUID companyProgramId);
}
