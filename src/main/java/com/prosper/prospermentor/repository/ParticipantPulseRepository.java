package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ParticipantPulse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantPulseRepository extends JpaRepository<ParticipantPulse, UUID> {

    List<ParticipantPulse> findByParticipant_Profile_IdOrderByCreatedAtDesc(UUID profileId);

    List<ParticipantPulse> findByParticipant_IdOrderByCreatedAtDesc(UUID participantId);

    List<ParticipantPulse> findByParticipant_CompanyProgram_Company_IdOrderByCreatedAtDesc(UUID companyId);

    @Query("""
            SELECT pulse
            FROM ParticipantPulse pulse
            JOIN pulse.participant participant
            JOIN participant.companyProgram companyProgram
            WHERE companyProgram.company.id = :companyId
              AND pulse.createdAt >= :startAt
              AND pulse.createdAt < :endAt
            ORDER BY pulse.createdAt DESC
            """)
    List<ParticipantPulse> findByCompanyIdWithinCreatedAt(@Param("companyId") UUID companyId,
                                                          @Param("startAt") LocalDateTime startAt,
                                                          @Param("endAt") LocalDateTime endAt);

    List<ParticipantPulse> findByParticipant_IdInAndPulseType(Collection<UUID> participantIds,
                                                             ParticipantPulse.PulseType pulseType);

    Optional<ParticipantPulse> findByParticipant_IdAndPulseType(UUID participantId,
                                                                ParticipantPulse.PulseType pulseType);

    @Query("""
            select pulse
            from ParticipantPulse pulse
            left join fetch pulse.participant participant
            left join fetch participant.profile
            left join fetch participant.companyProgram companyProgram
            left join fetch companyProgram.company
            where pulse.id = :pulseId
            """)
    Optional<ParticipantPulse> findDetailedById(@Param("pulseId") UUID pulseId);
}
