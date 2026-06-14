package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_outcome_action_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionOutcomeActionItem {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_outcome_id", nullable = false)
    private SessionOutcome sessionOutcome;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private ActionItemOwnerType ownerType = ActionItemOwnerType.MENTEE;

    @Column(name = "due_at")
    private ZonedDateTime dueAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_user_id")
    private UUID completedByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (ownerType == null) {
            ownerType = ActionItemOwnerType.MENTEE;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void markCompleted(UUID userId) {
        this.completedAt = LocalDateTime.now();
        this.completedByUserId = userId;
    }

    public void reopen() {
        this.completedAt = null;
        this.completedByUserId = null;
    }

    public enum ActionItemOwnerType {
        MENTEE,
        MENTOR,
        SHARED
    }
}
