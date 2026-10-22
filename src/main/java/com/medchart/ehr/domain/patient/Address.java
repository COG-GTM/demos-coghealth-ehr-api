package com.medchart.ehr.domain.patient;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Column(length = 200)
    private String street1;

    @Column(length = 200)
    private String street2;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(length = 50)
    private String country;

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (street1 != null) sb.append(street1);
        if (street2 != null) sb.append(", ").append(street2);
        if (city != null) sb.append(", ").append(city);
        if (state != null) sb.append(", ").append(state);
        if (zipCode != null) sb.append(" ").append(zipCode);
        if (country != null && !country.equals("USA")) sb.append(", ").append(country);
        return sb.toString();
    }
}
