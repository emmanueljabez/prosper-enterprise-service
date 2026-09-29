package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "profile_badge_awards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileBadgeAward {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_type_id", nullable = false)
    private BadgeType badgeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_ref_id", columnDefinition = "uuid")
    private UUID sourceRefId;

    @Column(name = "source_label")
    private String sourceLabel;

    @Column(name = "award_note", columnDefinition = "TEXT")
    private String awardNote;

    @Column(name = "revoke_note", columnDefinition = "TEXT")
    private String revokeNote;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "primary_selected_by_user", nullable = false)
    private Boolean primarySelectedByUser = false;

    @Column(name = "visibility", nullable = false)
    private String visibility = "SHOWN";

    @Column(name = "awarded_by", columnDefinition = "uuid")
    private UUID awardedBy;

    @Column(name = "awarded_at", nullable = false)
    private LocalDateTime awardedAt;

    @Column(name = "revoked_by", columnDefinition = "uuid")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (awardedAt == null) {
            awardedAt = now;
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (isPrimary == null) {
            isPrimary = false;
        }
        if (primarySelectedByUser == null) {
            primarySelectedByUser = false;
        }
        if (visibility == null) {
            visibility = "SHOWN";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
