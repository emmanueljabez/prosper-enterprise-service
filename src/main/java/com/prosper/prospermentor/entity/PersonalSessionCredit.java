package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "personal_session_credits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalSessionCredit {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "source_session_id")
    private UUID sourceSessionId;

    @Column(name = "source_payment_id")
    private UUID sourcePaymentId;

    @Column(name = "consumed_session_id")
    private UUID consumedSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_reason", nullable = false)
    private CreditReason creditReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CreditStatus status = CreditStatus.AVAILABLE;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = CreditStatus.AVAILABLE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CreditStatus {
        AVAILABLE,
        CONSUMED
    }

    public enum CreditReason {
        MENTOR_DECLINED_PAID_BOOKING
    }
}
