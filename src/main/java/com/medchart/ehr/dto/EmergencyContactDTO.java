package com.medchart.ehr.dto;

import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContactDTO implements Serializable {

    private static final long serialVersionUID = 1L;
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
