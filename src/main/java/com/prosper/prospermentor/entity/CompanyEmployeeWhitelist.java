package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing whitelisted employee emails for a company
 * This allows companies to pre-approve employees who can register
 */
@Entity
@Table(name = "company_employee_whitelist",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEmployeeWhitelist {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "is_used")
    private Boolean isUsed = false;

    @Column(name = "notes")
    private String notes;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "invitation_token")
    private String invitationToken;

    @Column(name = "invitation_token_expiry")
    private LocalDateTime invitationTokenExpiry;

    @Column(name = "invitation_sent")
    private Boolean invitationSent = false;

    @Column(name = "invitation_accepted")
    private Boolean invitationAccepted = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isUsed == null) {
            isUsed = false;
        }
        if (invitationSent == null) {
            invitationSent = false;
        }
        if (invitationAccepted == null) {
            invitationAccepted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
