package com.medchart.ehr.domain.insurance;

import com.medchart.ehr.domain.patient.Patient;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_coverages", indexes = {
    @Index(name = "idx_coverage_patient", columnList = "patient_id"),
    @Index(name = "idx_coverage_member_id", columnList = "memberId"),
    @Index(name = "idx_coverage_payer", columnList = "payerId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 100)
    private String payerName;

    @Column(nullable = false, length = 50)
    private String payerId;

    @Column(length = 100)
    private String planName;

    @Column(length = 50)
    private String planType;

    @Column(nullable = false, length = 50)
    private String memberId;

    @Column(length = 50)
    private String groupNumber;

    @Column(length = 100)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverageType coverageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoverageOrder coverageOrder;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDate terminationDate;

    @Column(length = 100)
    private String subscriberFirstName;

    @Column(length = 100)
    private String subscriberLastName;

    private LocalDate subscriberDob;

    @Column(length = 11)
    private String subscriberSsn;

    @Column(length = 50)
    private String subscriberRelationship;

    @Column(length = 20)
    private String copayAmount;

    @Column(length = 20)
    private String deductible;

    @Column(length = 20)
    private String deductibleMet;

    @Column(length = 20)
    private String outOfPocketMax;

    @Column(length = 20)
    private String outOfPocketMet;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verified = false;

    private LocalDateTime lastVerifiedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public boolean isCurrentlyActive() {
        LocalDate today = LocalDate.now();
        return active && 
               !effectiveDate.isAfter(today) && 
               (terminationDate == null || !terminationDate.isBefore(today));
    }
}
