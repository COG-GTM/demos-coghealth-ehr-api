package com.medchart.ehr.domain.medication;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_administrations", indexes = {
    @Index(name = "idx_med_admin_patient", columnList = "patient_id"),
    @Index(name = "idx_med_admin_order", columnList = "medication_order_id"),
    @Index(name = "idx_med_admin_time", columnList = "administeredDateTime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationAdministration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_order_id", nullable = false)
    private MedicationOrder medicationOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administered_by", nullable = false)
    private Provider administeredBy;

    @Column(nullable = false)
    private LocalDateTime scheduledDateTime;

    private LocalDateTime administeredDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdministrationStatus status;

    @Column(length = 100)
    private String doseGiven;

    @Column(length = 50)
    private String route;

    @Column(length = 50)
    private String site;

    @Column(length = 500)
    private String notes;

    @Column(length = 200)
    private String reasonNotGiven;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;
}
