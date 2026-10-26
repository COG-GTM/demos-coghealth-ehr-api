package com.medchart.ehr.domain.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void ssnIsNeverSerialized() throws Exception {
        Patient patient = Patient.builder()
                .id(1L)
                .mrn("MRN0000000001")
                .ssn("123-45-6789")
                .firstName("Jane")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 4, 2))
                .build();

        String json = objectMapper.writeValueAsString(patient);

        assertTrue(json.contains("\"mrn\":\"MRN0000000001\""));
        assertFalse(json.contains("ssn"));
        assertFalse(json.contains("123-45-6789"));
    }

    @Test
    void ssnIsNotBoundFromRequestBodies() throws Exception {
        Patient patient = objectMapper.readValue(
                "{\"mrn\":\"MRN0000000001\",\"ssn\":\"123-45-6789\",\"firstName\":\"Jane\",\"lastName\":\"Doe\"}",
                Patient.class);

        assertNull(patient.getSsn());
    }
}
