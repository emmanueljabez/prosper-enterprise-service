package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_demo_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class B2BDemoRequest {

    public enum DemoRequestStatus {
        NEW,
        CONTACTED,
        QUALIFIED,
        CLOSED
    }

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "work_email", nullable = false, length = 254)
    private String workEmail;

    @Column(name = "organisation", nullable = false, length = 200)
    private String organisation;

    @Column(name = "phone_number", length = 60)
    private String phoneNumber;

    @Column(name = "partnership_type", length = 80)
    private String partnershipType;

    @Column(name = "cohort_size", length = 80)
    private String cohortSize;

    @Column(name = "timeline", length = 120)
    private String timeline;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DemoRequestStatus status;

    @Column(name = "source_page", nullable = false, length = 120)
    private String sourcePage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = DemoRequestStatus.NEW;
        }
        if (sourcePage == null || sourcePage.isBlank()) {
            sourcePage = "enterprise-pricing";
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
