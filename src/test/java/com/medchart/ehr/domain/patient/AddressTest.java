package com.medchart.ehr.domain.patient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddressTest {

    @Test
    void formatsFullyPopulatedDomesticAddress() {
        Address address = Address.builder()
                .street1("123 Main St")
                .street2("Apt 4B")
                .city("Springfield")
                .state("IL")
                .zipCode("62704")
                .country("USA")
                .build();

        assertEquals("123 Main St, Apt 4B, Springfield, IL 62704", address.getFormattedAddress());
    }

    @Test
    void includesNonUsaCountry() {
        Address address = Address.builder()
                .street1("10 King St W")
                .city("Toronto")
                .state("ON")
                .zipCode("M5X 1A9")
                .country("Canada")
                .build();

        assertEquals("10 King St W, Toronto, ON M5X 1A9, Canada", address.getFormattedAddress());
    }

    @Test
    void omitsLeadingSeparatorWhenStreet1Missing() {
        Address address = Address.builder()
                .street2("Suite 200")
                .city("Springfield")
                .state("IL")
                .zipCode("62704")
                .build();

        assertEquals("Suite 200, Springfield, IL 62704", address.getFormattedAddress());
    }

    @Test
    void formatsZipCodeWithoutState() {
        Address address = Address.builder()
                .street1("123 Main St")
                .zipCode("62704")
                .build();

        assertEquals("123 Main St, 62704", address.getFormattedAddress());
    }

    @Test
    void formatsStateWithoutZipCode() {
        Address address = Address.builder()
                .street1("123 Main St")
                .state("IL")
                .build();

        assertEquals("123 Main St, IL", address.getFormattedAddress());
    }

    @Test
    void returnsEmptyStringWhenAllFieldsMissing() {
        assertEquals("", new Address().getFormattedAddress());
    }

    @Test
    void returnsEmptyStringWhenOnlyUsaCountryPresent() {
        Address address = Address.builder().country("USA").build();

        assertEquals("", address.getFormattedAddress());
    }
}
