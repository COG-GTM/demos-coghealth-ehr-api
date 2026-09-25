package com.medchart.ehr.domain.medication;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_orders", indexes = {
    @Index(name = "idx_med_order_patient", columnList = "patient_id"),
    @Index(name = "idx_med_order_provider", columnList = "prescriber_id"),
    @Index(name = "idx_med_order_status", columnList = "status"),
    @Index(name = "idx_med_order_date", columnList = "orderDateTime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Provider prescriber;

    @Column(nullable = false)
    private LocalDateTime orderDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MedicationOrderStatus status;

    @Column(nullable = false, length = 100)
    private String dose;

    @Column(nullable = false, length = 50)
    private String doseUnit;

    @Column(nullable = false, length = 50)
    private String route;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(length = 200)
    private String sig;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer quantity;

    private Integer refills;

    private Integer refillsRemaining;

    private Integer daysSupply;

    @Column(nullable = false)
    @Builder.Default
    private Boolean dispenseAsWritten = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean prn = false;

    @Column(length = 200)
    private String prnReason;

    @Column(length = 500)
    private String instructions;

    @Column(length = 500)
    private String pharmacyNotes;

    @Column(length = 200)
    private String pharmacy;

    @Column(length = 20)
    private String pharmacyNpi;

    private LocalDateTime sentToPharmacyAt;

    @Column(length = 100)
    private String discontinuedReason;

    private LocalDateTime discontinuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discontinued_by")
    private Provider discontinuedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;

    public boolean isActive() {
        return status == MedicationOrderStatus.ACTIVE && 
               (endDate == null || !endDate.isBefore(LocalDate.now()));
    }
}
