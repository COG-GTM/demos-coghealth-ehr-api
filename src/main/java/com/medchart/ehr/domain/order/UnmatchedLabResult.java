package com.medchart.ehr.domain.order;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "unmatched_lab_results", indexes = {
    @Index(name = "idx_unmatched_import", columnList = "import_id"),
    @Index(name = "idx_unmatched_review", columnList = "review_status"),
    @Index(name = "idx_unmatched_patient", columnList = "patient_identifier")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnmatchedLabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false)
    private LabResultImport labResultImport;

    @Column(length = 50)
    private String patientIdentifier;

    @Column(length = 100)
    private String patientFirstName;

    @Column(length = 100)
    private String patientLastName;

    @Column(length = 30)
    private String orderNumber;

    @Column(nullable = false, length = 20)
    private String testCode;

    @Column(length = 200)
    private String testName;

    @Column(length = 100)
    private String value;

    @Column(precision = 10, scale = 4)
    private BigDecimal numericValue;

    @Column(length = 50)
    private String unit;

    @Column(length = 100)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResultFlag flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultStatus resultStatus;

    private LocalDateTime resultDateTime;

    @Column(length = 100)
    private String performingLab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(length = 100)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String reviewNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;
}
