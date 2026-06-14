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
@Table(name = "journey_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyStep {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_template_id", nullable = false)
    private JourneyTemplate journeyTemplate;

    @Column(name = "step_key", nullable = false)
    private String stepKey;

    @Column(name = "default_sequence", nullable = false)
    private Integer defaultSequence;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private StepType stepType;

    @Column(name = "required", nullable = false)
    private Boolean required = true;

    @Column(name = "default_due_offset_days")
    private Integer defaultDueOffsetDays;

    @Column(name = "step_config_json", columnDefinition = "TEXT")
    private String stepConfigJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (required == null) {
            required = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StepType {
        SESSION,
        CHECK_IN,
        ACTION_ITEM,
        SURVEY,
        REFLECTION
    }
}
