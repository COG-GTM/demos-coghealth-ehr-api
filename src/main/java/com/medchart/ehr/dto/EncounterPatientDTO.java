package com.medchart.ehr.dto;

import com.medchart.ehr.domain.patient.Gender;
import lombok.*;

import java.time.LocalDate;

/**
 * Minimal patient identity embedded in encounter responses. Must never carry SSN or other
 * demographics; the full record is only available through the patient endpoints.
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
    private LocalDate dateOfBirth;
    private Gender gender;
}
