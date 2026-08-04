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
@Table(
        name = "session_outcomes",
        uniqueConstraints = @UniqueConstraint(columnNames = "session_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionOutcome {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "reflection_prompt", columnDefinition = "TEXT")
    private String reflectionPrompt;

    @Column(name = "mentor_private_notes", columnDefinition = "TEXT")
    private String mentorPrivateNotes;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sessionOutcome", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SessionOutcomeActionItem> actionItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (recordedAt == null) {
            recordedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
