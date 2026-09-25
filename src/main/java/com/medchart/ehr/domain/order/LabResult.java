package com.medchart.ehr.domain.order;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_results", indexes = {
    @Index(name = "idx_lab_result_order", columnList = "lab_order_id"),
    @Index(name = "idx_lab_result_code", columnList = "resultCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder labOrder;

    @Column(nullable = false, length = 20)
    private String resultCode;

    @Column(nullable = false, length = 200)
    private String resultName;

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
    private ResultStatus status;

    private LocalDateTime resultDateTime;

    @Column(length = 500)
    private String interpretation;

    @Column(length = 500)
    private String comments;

    @Column(length = 100)
    private String performingLab;

    @Column(length = 100)
    private String verifiedBy;

    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public boolean isAbnormal() {
        return flag != null && flag != ResultFlag.NORMAL;
    }
}
