package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.PersonalSessionCredit;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.PersonalSessionCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonalSessionCreditService {

    private final PersonalSessionCreditRepository creditRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public int getAvailableCreditCount(UUID profileId) {
        if (profileId == null) {
            return 0;
        }
        return creditRepository.countByProfileIdAndStatus(profileId, PersonalSessionCredit.CreditStatus.AVAILABLE);
    }

    public PersonalSessionCredit issueMentorDeclineCredit(Session session) {
        if (session == null || session.getId() == null || session.getMenteeId() == null) {
            throw new IllegalArgumentException("Session, session ID, and mentee ID are required to issue a credit");
        }

        return creditRepository.findBySourceSessionIdAndCreditReason(
                        session.getId(),
                        PersonalSessionCredit.CreditReason.MENTOR_DECLINED_PAID_BOOKING
                )
                .orElseGet(() -> createMentorDeclineCredit(session));
    }

    public PersonalSessionCredit consumeNextCredit(UUID profileId, UUID consumedSessionId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId is required to consume a session credit");
        }

        PersonalSessionCredit credit = creditRepository
                .findFirstByProfileIdAndStatusOrderByCreatedAtAsc(profileId, PersonalSessionCredit.CreditStatus.AVAILABLE)
                .orElseThrow(() -> new IllegalStateException("No personal session credits available"));

        credit.setStatus(PersonalSessionCredit.CreditStatus.CONSUMED);
        credit.setConsumedSessionId(consumedSessionId);
        credit.setConsumedAt(LocalDateTime.now());
        return creditRepository.save(credit);
    }

    private PersonalSessionCredit createMentorDeclineCredit(Session session) {
        PersonalSessionCredit credit = new PersonalSessionCredit();
        credit.setProfileId(session.getMenteeId());
        credit.setSourceSessionId(session.getId());
        credit.setSourcePaymentId(resolveCompletedSessionPaymentId(session.getId()).orElse(null));
        credit.setCreditReason(PersonalSessionCredit.CreditReason.MENTOR_DECLINED_PAID_BOOKING);
        credit.setStatus(PersonalSessionCredit.CreditStatus.AVAILABLE);
        credit.setNotes("Credited after mentor declined a paid session booking");
        return creditRepository.save(credit);
    }

    private Optional<UUID> resolveCompletedSessionPaymentId(UUID sessionId) {
        return paymentRepository.findTopBySessionIdAndPaymentTypeAndStatusOrderByCompletedAtDesc(
                        sessionId,
                        Payment.PaymentType.SESSION_BOOKING,
                        Payment.PaymentStatus.COMPLETED
                )
                .map(Payment::getId);
    }
}
