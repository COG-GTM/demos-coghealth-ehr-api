package com.medchart.ehr.dto;

import lombok.*;

/**
 * Patient reference embedded in encounter responses. Identifies the patient only;
 * SSN, date of birth, contact details and demographics stay behind the patient API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterPatientSummaryDTO {
    private Long id;
    private String mrn;
    private String firstName;
    private String middleName;
    private String lastName;
}
