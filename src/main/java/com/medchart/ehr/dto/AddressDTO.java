package com.medchart.ehr.dto;

import lombok.*;

/**
 * Data transfer object for a postal address.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDTO {
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
}
