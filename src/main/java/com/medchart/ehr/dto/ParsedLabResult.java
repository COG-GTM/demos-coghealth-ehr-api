package com.medchart.ehr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedLabResult {

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
    private String flag;
    private String resultStatus;
    private LocalDateTime resultDateTime;
    private String performingLab;
}
