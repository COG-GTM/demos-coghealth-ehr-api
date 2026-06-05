package com.medchart.ehr.export;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeIdentificationServiceTest {

    private DeIdentificationService service;

    @BeforeEach
    void setUp() {
        service = new DeIdentificationService();
    }

    @Test
    void deIdentifyPatient_stripsNames() {
        Patient patient = buildPatient("John", "Doe", LocalDate.of(1980, 1, 15), "10001");

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals("[REDACTED]", result.get("firstName"));
        assertEquals("[REDACTED]", result.get("lastName"));
    }

    @Test
    void deIdentifyPatient_convertsDobToAge() {
        LocalDate dob = LocalDate.now().minusYears(40);
        Patient patient = buildPatient("Jane", "Smith", dob, "20001");

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals(40, result.get("age"));
        assertFalse(result.containsKey("dateOfBirth"));
    }

    @Test
    void deIdentifyPatient_capsAgeAt90() {
        LocalDate dob = LocalDate.now().minusYears(95);
        Patient patient = buildPatient("Elder", "Person", dob, "30001");

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals(90, result.get("age"));
    }

    @Test
    void deIdentifyPatient_truncatesZipTo3Digits() {
        Patient patient = buildPatient("Test", "User", LocalDate.of(1990, 6, 1), "90210");

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals("90200", result.get("zipCode3"));
    }

    @Test
    void deIdentifyPatient_handlesNullZip() {
        Patient patient = buildPatient("Test", "User", LocalDate.of(1990, 6, 1), null);

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals("00000", result.get("zipCode3"));
    }

    @Test
    void deIdentifyPatient_preservesMrnAndGender() {
        Patient patient = buildPatient("Test", "User", LocalDate.of(1990, 6, 1), "10001");
        patient.setGender(Gender.MALE);

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertEquals("MRN001", result.get("mrn"));
        assertEquals("MALE", result.get("gender"));
    }

    @Test
    void deIdentifyPatient_stripsPhiFields() {
        Patient patient = buildPatient("Test", "User", LocalDate.of(1990, 6, 1), "10001");
        patient.setEmail("test@example.com");
        patient.setPhoneHome("555-0100");
        patient.setSsn("123-45-6789");

        Map<String, Object> result = service.deIdentifyPatient(patient);

        assertFalse(result.containsKey("email"));
        assertFalse(result.containsKey("phoneHome"));
        assertFalse(result.containsKey("ssn"));
    }

    @Test
    void deIdentify_processesBatch() {
        Patient p1 = buildPatient("Alice", "A", LocalDate.of(1985, 3, 10), "11111");
        Patient p2 = buildPatient("Bob", "B", LocalDate.of(1990, 7, 20), "22222");

        List<Map<String, Object>> results = service.deIdentify(List.of(p1, p2));

        assertEquals(2, results.size());
        assertEquals("[REDACTED]", results.get(0).get("firstName"));
        assertEquals("[REDACTED]", results.get(1).get("firstName"));
    }

    @Test
    void calculateAge_handlesNullDob() {
        assertEquals(0, service.calculateAge(null));
    }

    @Test
    void truncateZip_handlesShortZip() {
        assertEquals("00000", service.truncateZip("12"));
        assertEquals("00000", service.truncateZip(""));
    }

    private Patient buildPatient(String firstName, String lastName, LocalDate dob, String zip) {
        Address address = Address.builder()
                .street1("123 Main St")
                .city("Anytown")
                .state("NY")
                .zipCode(zip)
                .build();

        return Patient.builder()
                .mrn("MRN001")
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dob)
                .address(address)
                .active(true)
                .build();
    }
}
