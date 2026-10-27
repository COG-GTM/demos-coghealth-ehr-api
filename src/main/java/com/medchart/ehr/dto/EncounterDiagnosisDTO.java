package com.medchart.ehr.dto;

import com.medchart.ehr.domain.encounter.DiagnosisRole;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDiagnosisDTO {
    private Long id;
    private Long diagnosisId;
    private String icd10Code;
    private String description;
    private DiagnosisRole role;
    private Integer rank;
    private Boolean primaryDiagnosis;
}
