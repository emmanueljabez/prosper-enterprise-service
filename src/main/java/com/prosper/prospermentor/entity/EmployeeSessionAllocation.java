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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "employee_session_allocations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "profile_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSessionAllocation {

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

    @Column(name = "allocated_total", nullable = false)
    private Integer allocatedTotal = 0;

    @Column(name = "consumed_total", nullable = false)
    private Integer consumedTotal = 0;

    @Column(name = "available_balance", nullable = false)
    private Integer availableBalance = 0;

    @Column(name = "last_allocated_at")
    private LocalDateTime lastAllocatedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

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
        if (allocatedTotal == null) {
            allocatedTotal = 0;
        }
        if (consumedTotal == null) {
            consumedTotal = 0;
        }
        if (availableBalance == null) {
            availableBalance = 0;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
