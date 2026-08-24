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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
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
        name = "company_user_walkthrough_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uniq_company_user_walkthrough_progress",
                columnNames = {"company_id", "profile_id", "version"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyWalkthroughProgress {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "version", nullable = false, length = 80)
    private String version;

    @Column(name = "intro_dismissed", nullable = false)
    private boolean introDismissed = false;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "completed_task_ids", nullable = false, columnDefinition = "text[]")
    private String[] completedTaskIds = new String[0];

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "completed_tour_ids", nullable = false, columnDefinition = "text[]")
    private String[] completedTourIds = new String[0];

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public List<String> getCompletedTaskIds() {
        return TextArrayMapping.toListOrEmpty(completedTaskIds);
    }

    public void setCompletedTaskIds(List<String> completedTaskIds) {
        this.completedTaskIds = TextArrayMapping.fromListOrEmpty(completedTaskIds);
    }

    public void setCompletedTaskIds(String[] completedTaskIds) {
        this.completedTaskIds = completedTaskIds == null ? new String[0] : completedTaskIds;
    }

    public List<String> getCompletedTourIds() {
        return TextArrayMapping.toListOrEmpty(completedTourIds);
    }

    public void setCompletedTourIds(List<String> completedTourIds) {
        this.completedTourIds = TextArrayMapping.fromListOrEmpty(completedTourIds);
    }

    public void setCompletedTourIds(String[] completedTourIds) {
        this.completedTourIds = completedTourIds == null ? new String[0] : completedTourIds;
    }
}
