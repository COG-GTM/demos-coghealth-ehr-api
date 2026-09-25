package com.medchart.ehr.domain.order;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_orders", indexes = {
    @Index(name = "idx_lab_order_patient", columnList = "patient_id"),
    @Index(name = "idx_lab_order_provider", columnList = "ordering_provider_id"),
    @Index(name = "idx_lab_order_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrder {

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
    @JoinColumn(name = "ordering_provider_id", nullable = false)
    private Provider orderingProvider;

    @Column(nullable = false)
    private LocalDateTime orderDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderPriority priority;

    @Column(nullable = false, length = 20)
    private String testCode;

    @Column(nullable = false, length = 200)
    private String testName;

    @Column(length = 500)
    private String clinicalIndication;

    @Column(length = 20)
    private String icd10Code;

    @Column(length = 200)
    private String specimenType;

    private LocalDateTime specimenCollectedAt;

    @Column(length = 100)
    private String collectedBy;

    private LocalDateTime specimenReceivedAt;

    @Column(length = 50)
    private String labLocation;

    @Column(length = 500)
    private String specialInstructions;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fasting = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean stat = false;

    @OneToMany(mappedBy = "labOrder", cascade = CascadeType.ALL)
    @Builder.Default
    private List<LabResult> results = new ArrayList<>();

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

    public void addResult(LabResult result) {
        results.add(result);
        result.setLabOrder(this);
    }
}
