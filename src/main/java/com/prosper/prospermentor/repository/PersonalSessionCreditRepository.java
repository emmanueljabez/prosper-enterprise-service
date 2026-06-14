package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.PersonalSessionCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalSessionCreditRepository extends JpaRepository<PersonalSessionCredit, UUID> {

    int countByProfileIdAndStatus(UUID profileId, PersonalSessionCredit.CreditStatus status);

    Optional<PersonalSessionCredit> findFirstByProfileIdAndStatusOrderByCreatedAtAsc(
            UUID profileId,
            PersonalSessionCredit.CreditStatus status
    );

    Optional<PersonalSessionCredit> findBySourceSessionIdAndCreditReason(
            UUID sourceSessionId,
            PersonalSessionCredit.CreditReason creditReason
    );
}
