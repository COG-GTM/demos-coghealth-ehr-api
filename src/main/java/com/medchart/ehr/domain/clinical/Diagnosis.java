package com.medchart.ehr.domain.clinical;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnoses", indexes = {
    @Index(name = "idx_diagnosis_patient", columnList = "patient_id"),
    @Index(name = "idx_diagnosis_icd10", columnList = "icd10Code"),
    @Index(name = "idx_diagnosis_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 20)
    private String icd10Code;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiagnosisSeverity severity;

    @Column(nullable = false)
    private LocalDate onsetDate;

    private LocalDate abatementDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosed_by")
    private Provider diagnosedBy;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean chronic = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean principal = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;
}
