package com.medchart.ehr.domain.chronic;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chronic condition tracking for disease management programs.
 * 
 * Supports: Diabetes, Hypertension, COPD, CHF, CKD, etc.
 */
@Entity
@Table(name = "chronic_conditions")
@Data
@NoArgsConstructor
public class ChronicCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private ChronicConditionType conditionType;

    @Column(name = "icd10_code")
    private String icd10Code;

    @Column(name = "diagnosis_date")
    private LocalDate diagnosisDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private ConditionSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ConditionStatus status;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "managing_provider_id")
    private Long managingProviderId;

    @Column(name = "enrolled_in_program")
    private boolean enrolledInProgram;

    @Column(name = "program_enrollment_date")
    private LocalDate programEnrollmentDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
