package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ConsentRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.profile"
    })
    List<ConsentRecord> findByParticipant_IdOrderByCapturedAtDesc(UUID participantId);

    @EntityGraph(attributePaths = {
            "participant",
            "participant.companyProgram",
            "participant.companyProgram.company",
            "participant.profile"
    })
    List<ConsentRecord> findByParticipant_IdInOrderByCapturedAtDesc(Collection<UUID> participantIds);
}
