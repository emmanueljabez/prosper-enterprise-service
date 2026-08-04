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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Table(name = "company_mentor_pool_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMentorPoolMembership {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mentor_profile_id", nullable = false)
    private Profile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_invitation_id")
    private CompanyMentorInvitation sourceInvitation;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_mode", nullable = false)
    private VisibilityMode visibilityMode = VisibilityMode.COMPANY_PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false)
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    @Column(name = "profile_complete", nullable = false)
    private Boolean profileComplete = false;

    @Column(name = "availability_complete", nullable = false)
    private Boolean availabilityComplete = false;

    @Column(name = "company_bookable", nullable = false)
    private Boolean companyBookable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_approval_status", nullable = false)
    private PublicApprovalStatus publicApprovalStatus = PublicApprovalStatus.NOT_REQUESTED;

    @Column(name = "public_requested_at")
    private LocalDateTime publicRequestedAt;

    @Column(name = "public_approved_at")
    private LocalDateTime publicApprovedAt;

    @Column(name = "public_approved_by_user_id")
    private UUID publicApprovedByUserId;

    @Column(name = "public_listing_preexisting", nullable = false)
    private Boolean publicListingPreexisting = false;

    @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompanyMentorProgramScope> programScopes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (visibilityMode == null) {
            visibilityMode = VisibilityMode.COMPANY_PRIVATE;
        }
        if (membershipStatus == null) {
            membershipStatus = MembershipStatus.ACTIVE;
        }
        if (profileComplete == null) {
            profileComplete = false;
        }
        if (availabilityComplete == null) {
            availabilityComplete = false;
        }
        if (companyBookable == null) {
            companyBookable = false;
        }
        if (publicApprovalStatus == null) {
            publicApprovalStatus = PublicApprovalStatus.NOT_REQUESTED;
        }
        if (publicListingPreexisting == null) {
            publicListingPreexisting = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum VisibilityMode {
        COMPANY_PRIVATE,
        PROGRAM_RESTRICTED,
        PUBLIC_REQUESTED,
        PUBLIC_APPROVED
    }

    public enum MembershipStatus {
        PENDING_INVITE,
        ACTIVE,
        REMOVED,
        SUSPENDED
    }

    public enum PublicApprovalStatus {
        NOT_REQUESTED,
        REQUESTED,
        APPROVED,
        REJECTED
    }
}
