package com.medchart.ehr.dto;

import lombok.*;

/**
 * Minimal patient identity embedded in encounter responses. Only id, MRN and display name:
 * never SSN, DOB or other demographics; the full record is only available through the patient endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterPatientDTO {

    private Long id;
    private String mrn;
    private String firstName;
    private String middleName;
    private String lastName;
}
