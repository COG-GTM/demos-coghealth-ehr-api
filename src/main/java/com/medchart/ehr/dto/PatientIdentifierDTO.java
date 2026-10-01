package com.medchart.ehr.dto;

import com.medchart.ehr.domain.patient.IdentifierType;
import lombok.*;

import java.time.LocalDate;

/**
 * Data transfer object for a patient identifier (MRN, SSN, insurance ID, etc.).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientIdentifierDTO {
    private Long id;
    private IdentifierType identifierType;
    private String identifierValue;
    private String issuingAuthority;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private Boolean active;
}
