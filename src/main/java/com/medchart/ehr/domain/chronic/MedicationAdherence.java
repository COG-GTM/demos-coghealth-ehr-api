package com.medchart.ehr.domain.chronic;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks patient medication adherence for chronic conditions.
 * 
 * Used by the Chronic Medication Module to monitor compliance
 * and trigger provider alerts for non-adherent patients.
 */
@Entity
@Table(name = "medication_adherence")
@Data
@NoArgsConstructor
public class MedicationAdherence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "medication_order_id", nullable = false)
    private Long medicationOrderId;

    @Column(name = "chronic_condition_id")
    private Long chronicConditionId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /**
     * Proportion of Days Covered (PDC) - industry standard measure.
     * PDC >= 0.80 (80%) is considered adherent.
     */
    @Column(name = "pdc_score", precision = 5, scale = 4)
    private BigDecimal pdcScore;

    @Column(name = "days_supply")
    private Integer daysSupply;

    @Column(name = "days_covered")
    private Integer daysCovered;

    @Column(name = "refills_on_time")
    private Integer refillsOnTime;

    @Column(name = "refills_late")
    private Integer refillsLate;

    @Column(name = "refills_missed")
    private Integer refillsMissed;

    @Enumerated(EnumType.STRING)
    @Column(name = "adherence_status")
    private AdherenceStatus adherenceStatus;

    @Column(name = "last_fill_date")
    private LocalDate lastFillDate;

    @Column(name = "next_fill_due")
    private LocalDate nextFillDue;

    @Column(name = "pharmacy_npi")
    private String pharmacyNpi;

    @Column(name = "pharmacy_name")
    private String pharmacyName;

    @Column(name = "alert_sent")
    private boolean alertSent;

    @Column(name = "alert_sent_date")
    private LocalDateTime alertSentDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateAdherenceStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateAdherenceStatus();
    }

    private void calculateAdherenceStatus() {
        if (pdcScore == null) {
            adherenceStatus = AdherenceStatus.UNKNOWN;
        } else if (pdcScore.compareTo(new BigDecimal("0.80")) >= 0) {
            adherenceStatus = AdherenceStatus.ADHERENT;
        } else if (pdcScore.compareTo(new BigDecimal("0.50")) >= 0) {
            adherenceStatus = AdherenceStatus.PARTIALLY_ADHERENT;
        } else {
            adherenceStatus = AdherenceStatus.NON_ADHERENT;
        }
    }

    public enum AdherenceStatus {
        ADHERENT,           // PDC >= 80%
        PARTIALLY_ADHERENT, // PDC 50-79%
        NON_ADHERENT,       // PDC < 50%
        UNKNOWN
    }
}
