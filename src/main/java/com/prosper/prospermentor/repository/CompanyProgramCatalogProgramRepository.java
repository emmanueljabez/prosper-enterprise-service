package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramCatalogProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyProgramCatalogProgramRepository extends JpaRepository<CompanyProgramCatalogProgram, UUID> {

    List<CompanyProgramCatalogProgram> findByCompanyProgram_IdOrderByJourneyOrderAsc(UUID companyProgramId);

    List<CompanyProgramCatalogProgram> findByCompanyProgram_IdInOrderByJourneyOrderAsc(Collection<UUID> companyProgramIds);

    void deleteByCompanyProgram_Id(UUID companyProgramId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyProgramCatalogProgram cpp where cpp.companyProgram.id = :companyProgramId")
    void deleteAllForCompanyProgram(UUID companyProgramId);
}
