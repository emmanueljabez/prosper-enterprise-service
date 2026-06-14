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
@Table(
        name = "company_program_catalog_programs",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_program_catalog_programs_order", columnNames = {"company_program_id", "journey_order"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCatalogProgram {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_program_id", nullable = false)
    private CompanyProgram companyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "journey_order", nullable = false)
    private Integer journeyOrder;

    @Column(name = "journey_stage_name")
    private String journeyStageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false)
    private StageType stageType = StageType.CORE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stageType == null) {
            stageType = StageType.CORE;
        }
    }

    public enum StageType {
        CORE,
        OPTIONAL
    }
}
