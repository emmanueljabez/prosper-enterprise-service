package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private CompanyProgramParticipant participant;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 48)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConsentStatus status;

    @Column(name = "captured_by_user_id", columnDefinition = "uuid")
    private UUID capturedByUserId;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now();
        }
    }

    public enum ConsentType {
        PROGRAM_PARTICIPATION,
        AGGREGATED_ANALYTICS,
        EMPLOYER_PROGRESS_VISIBILITY
    }

    public enum ConsentStatus {
        GRANTED,
        REVOKED
    }
}
