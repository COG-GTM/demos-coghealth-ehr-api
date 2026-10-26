package com.medchart.ehr.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterProviderDTO {

    private Long id;
    private String npi;
    private String firstName;
    private String lastName;
    private String credentials;
    private String specialty;
    private String department;
}
