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
@Table(name = "common_interest_circle_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommonInterestCircleNote {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circle_id", nullable = false)
    private CommonInterestCircle circle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_participant_id")
    private CompanyProgramCohortParticipant cohortParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_profile_id")
    private Profile authorProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false)
    private NoteType noteType = NoteType.ADMIN_NOTE;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (noteType == null) {
            noteType = NoteType.ADMIN_NOTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum NoteType {
        FACILITATOR_NOTE,
        COMPLETION_NOTE,
        ADMIN_NOTE
    }
}
