package com.medchart.ehr.dto;

import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
}
