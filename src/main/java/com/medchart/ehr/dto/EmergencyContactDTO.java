package com.medchart.ehr.dto;

import lombok.*;

/**
 * Data transfer object for a patient's emergency contact.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContactDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String relationship;
    private String phoneHome;
    private String phoneMobile;
    private String phoneWork;
    private String email;
    private AddressDTO address;
    private Integer priority;
    private Boolean active;
}
