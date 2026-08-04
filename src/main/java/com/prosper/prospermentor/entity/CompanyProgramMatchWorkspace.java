package com.prosper.prospermentor.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "company_program_match_workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramMatchWorkspace {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false, unique = true)
    private CompanyProgramParticipant participant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status = MatchStatus.ADMIN_REVIEW;

    @Column(name = "selection_deadline_at")
    private LocalDateTime selectionDeadlineAt;

    @Column(name = "shortlist_generated_at")
    private LocalDateTime shortlistGeneratedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_by")
    private ResolverType resolvedBy;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rankOrder ASC")
    private List<CompanyProgramMatchOption> options = new ArrayList<>();

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
        if (version == null) {
            version = 0L;
        }
        if (status == null) {
            status = MatchStatus.ADMIN_REVIEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MatchStatus {
        ADMIN_REVIEW,
        PENDING_EMPLOYEE_SELECTION,
        ASSIGNED,
        EXPIRED_NO_CANDIDATE,
        INACTIVE
    }

    public enum ResolverType {
        EMPLOYEE,
        ADMIN,
        SYSTEM
    }
}
