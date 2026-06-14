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
@Table(name = "journey_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTemplate {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "program_type")
    private String programType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url", length = 1024)
    private String coverImageUrl;

    @Column(name = "default_duration_weeks")
    private Integer defaultDurationWeeks;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "template_snapshot_json", columnDefinition = "TEXT")
    private String templateSnapshotJson;

    @OneToMany(mappedBy = "journeyTemplate", fetch = FetchType.LAZY)
    @OrderBy("defaultSequence ASC")
    private List<JourneyStep> steps = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (templateVersion == null) {
            templateVersion = 1;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
