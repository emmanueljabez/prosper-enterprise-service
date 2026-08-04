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
@Table(name = "access_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessAuditLog {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id")
    private CompanyProgram companyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private CompanyProgramParticipant participant;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 64)
    private ResourceType resourceType;

    @Column(name = "resource_id", columnDefinition = "uuid")
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 24)
    private ActionType action;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum ResourceType {
        COMPANY_PROGRAM_PARTICIPANT_ROSTER,
        PARTICIPANT_CONSENTS,
        REVIEW_ALERT_QUEUE,
        REVIEW_ALERT_SUMMARY
    }

    public enum ActionType {
        VIEW,
        UPDATE,
        REMATCH
    }
}
