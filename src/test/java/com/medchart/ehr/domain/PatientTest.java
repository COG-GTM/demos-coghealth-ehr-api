package com.medchart.ehr.domain;

import com.medchart.ehr.domain.patient.EmergencyContact;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.PatientIdentifier;
import com.medchart.ehr.domain.patient.IdentifierType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PatientTest {

    @Test
    void getFullName_shouldReturnFirstAndLast() {
        Patient patient = Patient.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        assertEquals("John Doe", patient.getFullName());
    }

    @Test
    void getFullName_shouldIncludeMiddleName() {
        Patient patient = Patient.builder()
                .firstName("John")
                .middleName("Michael")
                .lastName("Doe")
                .build();

        assertEquals("John Michael Doe", patient.getFullName());
    }

    @Test
    void getFullName_shouldIgnoreBlankMiddleName() {
        Patient patient = Patient.builder()
                .firstName("John")
                .middleName("  ")
                .lastName("Doe")
                .build();

        assertEquals("John Doe", patient.getFullName());
    }

    @Test
    void addIdentifier_shouldLinkToPatient() {
        Patient patient = Patient.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifierType(IdentifierType.MRN);
        identifier.setIdentifierValue("MRN001");

        patient.addIdentifier(identifier);

        assertEquals(1, patient.getIdentifiers().size());
        assertSame(patient, identifier.getPatient());
    }

    @Test
    void removeIdentifier_shouldUnlinkFromPatient() {
        Patient patient = Patient.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifierType(IdentifierType.MRN);
        identifier.setIdentifierValue("MRN001");

        patient.addIdentifier(identifier);
        patient.removeIdentifier(identifier);

        assertEquals(0, patient.getIdentifiers().size());
        assertNull(identifier.getPatient());
    }

    @Test
    void addEmergencyContact_shouldLinkToPatient() {
        Patient patient = Patient.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        EmergencyContact contact = new EmergencyContact();
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setRelationship("Spouse");

        patient.addEmergencyContact(contact);

        assertEquals(1, patient.getEmergencyContacts().size());
        assertSame(patient, contact.getPatient());
    }

    @Test
    void builder_shouldSetDefaults() {
        Patient patient = Patient.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        assertTrue(patient.getActive());
        assertFalse(patient.getDeceased());
        assertNotNull(patient.getIdentifiers());
        assertNotNull(patient.getEmergencyContacts());
    }

    @Test
    void builder_shouldSetAllFields() {
        Patient patient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .middleName("Michael")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(Gender.MALE)
                .email("john@example.com")
                .active(true)
                .build();

        assertEquals(1L, patient.getId());
        assertEquals("MRN001", patient.getMrn());
        assertEquals("John", patient.getFirstName());
        assertEquals("Michael", patient.getMiddleName());
        assertEquals("Doe", patient.getLastName());
        assertEquals(LocalDate.of(1990, 1, 15), patient.getDateOfBirth());
        assertEquals(Gender.MALE, patient.getGender());
        assertEquals("john@example.com", patient.getEmail());
    }
}
