package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "badge_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BadgeType {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "award_method", nullable = false)
    private String awardMethod;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "color_hex", nullable = false)
    private String colorHex = "#8f1f74";

    @Column(name = "background_hex", nullable = false)
    private String backgroundHex = "#f7e8f3";

    @Column(name = "text_hex", nullable = false)
    private String textHex = "#6f1859";

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
        if (colorHex == null) {
            colorHex = "#8f1f74";
        }
        if (backgroundHex == null) {
            backgroundHex = "#f7e8f3";
        }
        if (textHex == null) {
            textHex = "#6f1859";
        }
        if (displayOrder == null) {
            displayOrder = 100;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
