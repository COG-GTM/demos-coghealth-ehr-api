package com.medchart.ehr.dto;

import com.medchart.ehr.domain.order.ResultFlag;
import com.medchart.ehr.domain.order.ResultStatus;
import com.medchart.ehr.domain.order.ReviewStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UnmatchedLabResultDTO {

    private Long id;
    private Long importId;
    private String patientIdentifier;
    private String patientFirstName;
    private String patientLastName;
    private String orderNumber;
    private String testCode;
    private String testName;
    private String value;
    private BigDecimal numericValue;
    private String unit;
    private String referenceRange;
    private ResultFlag flag;
    private ResultStatus resultStatus;
    private LocalDateTime resultDateTime;
    private String performingLab;
    private ReviewStatus reviewStatus;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private LocalDateTime createdAt;
}
