package com.medchart.ehr.dto;

import com.medchart.ehr.domain.patient.IdentifierType;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientIdentifierDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long id;
    private IdentifierType identifierType;
    private String identifierValue;
    private String issuingAuthority;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private Boolean active;
}
