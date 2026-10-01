package com.medchart.ehr.domain.patient;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        List<String> segments = new ArrayList<>();
        if (street1 != null) segments.add(street1);
        if (street2 != null) segments.add(street2);
        if (city != null) segments.add(city);

        String stateAndZip = Stream.of(state, zipCode)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        if (!stateAndZip.isEmpty()) segments.add(stateAndZip);

        if (country != null && !country.equals("USA")) segments.add(country);
        return String.join(", ", segments);
    }
}
