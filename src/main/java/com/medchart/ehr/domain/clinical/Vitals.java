package com.medchart.ehr.domain.clinical;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vitals", indexes = {
    @Index(name = "idx_vitals_patient", columnList = "patient_id"),
    @Index(name = "idx_vitals_encounter", columnList = "encounter_id"),
    @Index(name = "idx_vitals_recorded", columnList = "recordedDateTime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @Column(nullable = false)
    private LocalDateTime recordedDateTime;

    @Column(precision = 5, scale = 1)
    private BigDecimal temperatureFahrenheit;

    private Integer heartRate;

    private Integer respiratoryRate;

    private Integer systolicBp;

    private Integer diastolicBp;

    @Column(precision = 5, scale = 1)
    private BigDecimal oxygenSaturation;

    @Column(precision = 5, scale = 1)
    private BigDecimal heightInches;

    @Column(precision = 6, scale = 2)
    private BigDecimal weightPounds;

    @Column(precision = 4, scale = 1)
    private BigDecimal bmi;

    private Integer painLevel;

    @Column(length = 50)
    private String bloodPressurePosition;

    @Column(length = 50)
    private String bloodPressureSite;

    @Column(length = 50)
    private String temperatureSite;

    @Column(length = 50)
    private String oxygenDeliveryMethod;

    @Column(precision = 4, scale = 1)
    private BigDecimal oxygenFlowRate;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public String getBloodPressure() {
        if (systolicBp != null && diastolicBp != null) {
            return systolicBp + "/" + diastolicBp;
        }
        return null;
    }
}
