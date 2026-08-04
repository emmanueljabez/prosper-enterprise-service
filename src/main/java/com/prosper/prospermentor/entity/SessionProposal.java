package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "session_proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionProposal {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_type", nullable = false, length = 30)
    private ProposalType proposalType = ProposalType.SINGLE_SLOT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ProposalStatus status = ProposalStatus.PENDING_MENTEE_RESPONSE;

    @Column(name = "mentor_message", columnDefinition = "TEXT")
    private String mentorMessage;

    @Column(name = "mentee_response", columnDefinition = "TEXT")
    private String menteeResponse;

    @Column(name = "accepted_slot_id")
    private UUID acceptedSlotId;

    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SessionProposalSlot> slots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (proposedAt == null) {
            proposedAt = now;
        }
        if (proposalType == null) {
            proposalType = slots != null && slots.size() > 1 ? ProposalType.MULTIPLE_SLOTS : ProposalType.SINGLE_SLOT;
        }
        if (status == null) {
            status = ProposalStatus.PENDING_MENTEE_RESPONSE;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ProposalType {
        SINGLE_SLOT,
        MULTIPLE_SLOTS
    }

    public enum ProposalStatus {
        PENDING_MENTEE_RESPONSE,
        ACCEPTED,
        DECLINED,
        CANCELLED,
        EXPIRED
    }
}
