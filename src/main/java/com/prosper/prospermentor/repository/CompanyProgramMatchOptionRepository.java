package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanyProgramMatchOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyProgramMatchOptionRepository extends JpaRepository<CompanyProgramMatchOption, UUID> {

    List<CompanyProgramMatchOption> findByWorkspace_IdAndActiveTrueOrderByRankOrderAsc(UUID workspaceId);

    void deleteByWorkspace_Id(UUID workspaceId);

    long countByWorkspace_IdAndActiveTrue(UUID workspaceId);

    @Query("""
            SELECT matchOption.workspace.id, COUNT(matchOption.id)
            FROM CompanyProgramMatchOption matchOption
            WHERE matchOption.workspace.id IN :workspaceIds
              AND matchOption.active = true
            GROUP BY matchOption.workspace.id
            """)
    List<Object[]> countActiveByWorkspaceIds(@Param("workspaceIds") Collection<UUID> workspaceIds);
}
