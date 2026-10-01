package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FhirPatientMapperTest {

    private FhirPatientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FhirPatientMapper();
    }

    @Test
    void toFhirResource_shouldMapBasicFields() {
        Patient patient = buildPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        assertEquals("Patient", fhir.get("resourceType"));
        assertEquals("1", fhir.get("id"));
        assertEquals("male", fhir.get("gender"));
        assertEquals("1990-01-15", fhir.get("birthDate"));
        assertTrue((Boolean) fhir.get("active"));
    }

    @Test
    void toFhirResource_shouldMapIdentifiers() {
        Patient patient = buildPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) fhir.get("identifier");
        assertNotNull(identifiers);
        assertEquals(2, identifiers.size());

        Map<String, Object> mrnId = identifiers.get(0);
        assertEquals("http://hospital.example.org/mrn", mrnId.get("system"));
        assertEquals("MRN001", mrnId.get("value"));
        assertEquals("official", mrnId.get("use"));
    }

    @Test
    void toFhirResource_shouldMapName() {
        Patient patient = buildPatient();
        patient.setMiddleName("Michael");

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        List<Map<String, Object>> names = (List<Map<String, Object>>) fhir.get("name");
        assertNotNull(names);
        assertEquals(1, names.size());

        Map<String, Object> name = names.get(0);
        assertEquals("official", name.get("use"));
        assertEquals("Doe", name.get("family"));
        List<String> given = (List<String>) name.get("given");
        assertEquals(2, given.size());
        assertEquals("John", given.get(0));
        assertEquals("Michael", given.get(1));
    }

    @Test
    void toFhirResource_shouldMapNameWithoutMiddleName() {
        Patient patient = buildPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        List<Map<String, Object>> names = (List<Map<String, Object>>) fhir.get("name");
        Map<String, Object> name = names.get(0);
        List<String> given = (List<String>) name.get("given");
        assertEquals(1, given.size());
        assertEquals("John", given.get(0));
    }

    @Test
    void toFhirResource_shouldMapTelecom() {
        Patient patient = buildPatient();
        patient.setPhoneHome("555-1234");
        patient.setPhoneMobile("555-5678");
        patient.setEmail("john@example.com");

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        List<Map<String, Object>> telecom = (List<Map<String, Object>>) fhir.get("telecom");
        assertEquals(3, telecom.size());
    }

    @Test
    void toFhirResource_shouldMapAddress() {
        Patient patient = buildPatient();
        Address address = new Address();
        address.setStreet1("123 Main St");
        address.setStreet2("Apt 4B");
        address.setCity("Springfield");
        address.setState("IL");
        address.setZipCode("62704");
        patient.setAddress(address);

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        List<Map<String, Object>> addresses = (List<Map<String, Object>>) fhir.get("address");
        assertNotNull(addresses);
        assertEquals(1, addresses.size());

        Map<String, Object> addr = addresses.get(0);
        assertEquals("home", addr.get("use"));
        assertEquals("Springfield", addr.get("city"));
        assertEquals("IL", addr.get("state"));
        assertEquals("62704", addr.get("postalCode"));
        assertEquals("US", addr.get("country"));
    }

    @Test
    void toFhirResource_shouldMapGenderCorrectly() {
        Patient patient = buildPatient();

        patient.setGender(Gender.FEMALE);
        assertEquals("female", mapper.toFhirResource(patient).get("gender"));

        patient.setGender(Gender.OTHER);
        assertEquals("other", mapper.toFhirResource(patient).get("gender"));

        patient.setGender(null);
        assertEquals("unknown", mapper.toFhirResource(patient).get("gender"));
    }

    @Test
    void toFhirResource_shouldMapDeceasedPatient() {
        Patient patient = buildPatient();
        patient.setDeceased(true);

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        assertTrue((Boolean) fhir.get("deceasedBoolean"));
    }

    @Test
    void toFhirResource_shouldMapMaritalStatus() {
        Patient patient = buildPatient();
        patient.setMaritalStatus(MaritalStatus.MARRIED);

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        Map<String, Object> maritalStatus = (Map<String, Object>) fhir.get("maritalStatus");
        assertNotNull(maritalStatus);
        List<Map<String, Object>> codings = (List<Map<String, Object>>) maritalStatus.get("coding");
        assertEquals("M", codings.get(0).get("code"));
    }

    @Test
    void fromFhirResource_shouldMapBasicFields() {
        Map<String, Object> fhir = Map.of(
                "resourceType", "Patient",
                "gender", "female",
                "birthDate", "1985-06-20",
                "active", true,
                "identifier", List.of(
                        Map.of("system", "http://hospital.example.org/mrn", "value", "MRN002")
                ),
                "name", List.of(
                        Map.of("family", "Smith", "given", List.of("Jane"))
                )
        );

        Patient patient = mapper.fromFhirResource(fhir);

        assertEquals("MRN002", patient.getMrn());
        assertEquals("Jane", patient.getFirstName());
        assertEquals("Smith", patient.getLastName());
        assertEquals(Gender.FEMALE, patient.getGender());
        assertEquals(LocalDate.of(1985, 6, 20), patient.getDateOfBirth());
        assertTrue(patient.getActive());
    }

    @Test
    void fromFhirResource_shouldMapMultipleGivenNames() {
        Map<String, Object> fhir = Map.of(
                "resourceType", "Patient",
                "name", List.of(
                        Map.of("family", "Smith", "given", List.of("Jane", "Marie"))
                )
        );

        Patient patient = mapper.fromFhirResource(fhir);

        assertEquals("Jane", patient.getFirstName());
        assertEquals("Marie", patient.getMiddleName());
    }

    @Test
    void fromFhirResource_shouldHandleGenderMapping() {
        assertEquals(Gender.MALE, mapper.fromFhirResource(
                Map.of("resourceType", "Patient", "gender", "male")).getGender());
        assertEquals(Gender.FEMALE, mapper.fromFhirResource(
                Map.of("resourceType", "Patient", "gender", "female")).getGender());
        assertEquals(Gender.OTHER, mapper.fromFhirResource(
                Map.of("resourceType", "Patient", "gender", "unknown")).getGender());
    }

    @Test
    void fromFhirResource_shouldDefaultActiveToTrue() {
        Patient patient = mapper.fromFhirResource(Map.of("resourceType", "Patient"));

        assertTrue(patient.getActive());
    }

    @Test
    void roundTrip_shouldPreserveData() {
        Patient original = buildPatient();
        original.setMiddleName("Michael");
        original.setPhoneHome("555-1234");
        original.setEmail("john@example.com");
        original.setMaritalStatus(MaritalStatus.SINGLE);

        Map<String, Object> fhir = mapper.toFhirResource(original);
        Patient restored = mapper.fromFhirResource(fhir);

        assertEquals(original.getMrn(), restored.getMrn());
        assertEquals(original.getFirstName(), restored.getFirstName());
        assertEquals(original.getMiddleName(), restored.getMiddleName());
        assertEquals(original.getLastName(), restored.getLastName());
        assertEquals(original.getGender(), restored.getGender());
        assertEquals(original.getDateOfBirth(), restored.getDateOfBirth());
    }

    private Patient buildPatient() {
        return Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .deceased(false)
                .build();
    }
}
