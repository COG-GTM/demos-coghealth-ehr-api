package com.medchart.ehr.domain.clinical;

import com.medchart.ehr.domain.patient.Patient;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "allergies", indexes = {
    @Index(name = "idx_allergy_patient", columnList = "patient_id"),
    @Index(name = "idx_allergy_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AllergyType allergyType;

    @Column(nullable = false, length = 200)
    private String allergen;

    @Column(length = 50)
    private String allergenCode;

    @Column(length = 20)
    private String allergenCodeSystem;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllergyStatus status;

    @Column(length = 500)
    private String reaction;

    private LocalDate onsetDate;

    private LocalDate recordedDate;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;
}
