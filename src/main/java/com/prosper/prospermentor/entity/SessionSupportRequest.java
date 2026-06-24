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
@Table(name = "session_support_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionSupportRequest {

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
    @Column(name = "requester_type", nullable = false, length = 20)
    private RequesterType requesterType;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SupportStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RequesterType {
        MENTOR,
        MENTEE
    }

    public enum SupportStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED
    }
}
