package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.MaritalStatus;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class FhirPatientMapperTest {

    private static final String MRN_SYSTEM = "http://hospital.example.org/mrn";
    private static final String SSN_SYSTEM = "http://hl7.org/fhir/sid/us-ssn";

    private final FhirPatientMapper mapper = new FhirPatientMapper();

    private static Patient minimal() {
        Patient p = new Patient();
        p.setId(42L);
        p.setMrn("MRN-TEST-1");
        p.setFirstName("Test");
        p.setLastName("Patient");
        return p;
    }

    private static Map<String, Object> identifier(List<Map<String, Object>> ids, String system) {
        return ids.stream().filter(i -> system.equals(i.get("system"))).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("BR-MAP-01: outbound resources are FHIR R4 Patient resources with a string id")
    void brMap01_resourceTypeAndId() {
        Map<String, Object> r = mapper.toFhirResource(minimal());
        assertEquals("Patient", r.get("resourceType"));
        assertEquals("42", r.get("id"));
    }

    @Test
    @DisplayName("BR-MAP-02: MRN uses the hospital MRN system (official); SSN uses the US-SSN system (secondary)")
    void brMap02_identifierSystems() {
        Patient p = minimal();
        p.setSsn("000-00-0000");
        List<Map<String, Object>> ids = (List<Map<String, Object>>) mapper.toFhirResource(p).get("identifier");

        assertEquals(2, ids.size());
        Map<String, Object> mrn = identifier(ids, MRN_SYSTEM);
        assertEquals("MRN-TEST-1", mrn.get("value"));
        assertEquals("official", mrn.get("use"));
        Map<String, Object> ssn = identifier(ids, SSN_SYSTEM);
        assertEquals("000-00-0000", ssn.get("value"));
        assertEquals("secondary", ssn.get("use"));
    }

    @Test
    @DisplayName("BR-MAP-02: a patient without SSN gets an empty SSN identifier value (current contract)")
    void brMap02_missingSsnIsEmpty() {
        List<Map<String, Object>> ids = (List<Map<String, Object>>) mapper.toFhirResource(minimal()).get("identifier");
        assertEquals("", identifier(ids, SSN_SYSTEM).get("value"));
    }

    @Test
    @DisplayName("BR-MAP-03: the official name has family and given [first, middle]; middle is optional")
    void brMap03_name() {
        Patient p = minimal();
        Map<String, Object> name = ((List<Map<String, Object>>) mapper.toFhirResource(p).get("name")).get(0);
        assertEquals("official", name.get("use"));
        assertEquals("Patient", name.get("family"));
        assertEquals(List.of("Test"), name.get("given"));

        p.setMiddleName("Middle");
        name = ((List<Map<String, Object>>) mapper.toFhirResource(p).get("name")).get(0);
        assertEquals(List.of("Test", "Middle"), name.get("given"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"MALE,male", "FEMALE,female", "OTHER,other", "UNKNOWN,unknown"})
    @DisplayName("BR-MAP-04: administrative gender maps to the FHIR AdministrativeGender codes")
    void brMap04_genderOutbound(Gender gender, String code) {
        Patient p = minimal();
        p.setGender(gender);
        assertEquals(code, mapper.toFhirResource(p).get("gender"));
    }

    @Test
    @DisplayName("BR-MAP-04: a missing gender is exported as 'unknown'")
    void brMap04_nullGender() {
        assertEquals("unknown", mapper.toFhirResource(minimal()).get("gender"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"male,MALE", "FEMALE,FEMALE", "Other,OTHER", "unknown,UNKNOWN", "not-a-code,UNKNOWN"})
    @DisplayName("BR-MAP-04: inbound gender is case-insensitive and 'unknown'/unrecognised codes map to UNKNOWN")
    void brMap04_genderInbound(String code, Gender expected) {
        assertEquals(expected, mapper.fromFhirResource(Map.of("gender", code)).getGender());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"MALE", "FEMALE", "OTHER", "UNKNOWN"})
    @DisplayName("BR-MAP-04: gender survives a FHIR round trip unchanged")
    void brMap04_genderRoundTrip(Gender gender) {
        Patient p = minimal();
        p.setGender(gender);
        assertEquals(gender, mapper.fromFhirResource(mapper.toFhirResource(p)).getGender());
    }

    @Test
    @DisplayName("BR-MAP-05: birthDate is ISO yyyy-MM-dd and omitted when unknown")
    void brMap05_birthDate() {
        Patient p = minimal();
        assertFalse(mapper.toFhirResource(p).containsKey("birthDate"));
        p.setDateOfBirth(LocalDate.of(2000, 2, 29));
        assertEquals("2000-02-29", mapper.toFhirResource(p).get("birthDate"));
    }

    @Test
    @DisplayName("BR-MAP-06: telecom lists home phone, mobile phone and email, only when present")
    void brMap06_telecom() {
        Patient p = minimal();
        assertEquals(List.of(), mapper.toFhirResource(p).get("telecom"));

        p.setPhoneHome("555-0100");
        p.setPhoneMobile("555-0101");
        p.setEmail("test@example.test");
        List<Map<String, Object>> t = (List<Map<String, Object>>) mapper.toFhirResource(p).get("telecom");
        assertEquals(Map.of("system", "phone", "value", "555-0100", "use", "home"), t.get(0));
        assertEquals(Map.of("system", "phone", "value", "555-0101", "use", "mobile"), t.get(1));
        assertEquals(Map.of("system", "email", "value", "test@example.test"), t.get(2));
    }

    @Test
    @DisplayName("BR-MAP-07: the home address maps street lines, city, state, postal code and country US")
    void brMap07_address() {
        Patient p = minimal();
        assertFalse(mapper.toFhirResource(p).containsKey("address"));

        p.setAddress(Address.builder().street1("1 Test St").street2("Apt 2").city("Testville").state("NY")
                .zipCode("00000").country("USA").build());
        Map<String, Object> a = ((List<Map<String, Object>>) mapper.toFhirResource(p).get("address")).get(0);
        assertEquals("home", a.get("use"));
        assertEquals(List.of("1 Test St", "Apt 2"), a.get("line"));
        assertEquals("Testville", a.get("city"));
        assertEquals("NY", a.get("state"));
        assertEquals("00000", a.get("postalCode"));
        assertEquals("US", a.get("country"));

        p.setAddress(new Address());
        a = ((List<Map<String, Object>>) mapper.toFhirResource(p).get("address")).get(0);
        assertEquals(List.of(), a.get("line"));
    }

    @Test
    @DisplayName("BR-MAP-08: active is exported true only when explicitly active")
    void brMap08_activeOutbound() {
        Patient p = minimal();
        assertEquals(true, mapper.toFhirResource(p).get("active"));
        p.setActive(null);
        assertEquals(false, mapper.toFhirResource(p).get("active"));
        p.setActive(false);
        assertEquals(false, mapper.toFhirResource(p).get("active"));
    }

    @Test
    @DisplayName("BR-MAP-08: inbound patients default to active when the flag is absent")
    void brMap08_activeInbound() {
        assertTrue(mapper.fromFhirResource(Map.of()).getActive());
        assertFalse(mapper.fromFhirResource(Map.of("active", false)).getActive());
    }

    @Test
    @DisplayName("BR-MAP-09: deceased patients export deceasedBoolean and, when known, deceasedDateTime")
    void brMap09_deceased() {
        Patient p = minimal();
        assertFalse(mapper.toFhirResource(p).containsKey("deceasedBoolean"));

        p.setDeceased(true);
        Map<String, Object> r = mapper.toFhirResource(p);
        assertEquals(true, r.get("deceasedBoolean"));
        assertFalse(r.containsKey("deceasedDateTime"));

        p.setDeceasedDate(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertEquals("2026-01-02T03:04", mapper.toFhirResource(p).get("deceasedDateTime"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"SINGLE,S", "MARRIED,M", "DIVORCED,D", "WIDOWED,W", "SEPARATED,UNK", "DOMESTIC_PARTNER,UNK", "UNKNOWN,UNK"})
    @DisplayName("BR-MAP-10: marital status uses HL7 v3 MaritalStatus codes; unmapped values become UNK")
    void brMap10_maritalStatus(MaritalStatus status, String code) {
        Patient p = minimal();
        p.setMaritalStatus(status);
        Map<String, Object> ms = (Map<String, Object>) mapper.toFhirResource(p).get("maritalStatus");
        Map<String, Object> coding = ((List<Map<String, Object>>) ms.get("coding")).get(0);
        assertEquals("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus", coding.get("system"));
        assertEquals(code, coding.get("code"));
    }

    @Test
    @DisplayName("BR-MAP-10: marital status is omitted when unknown to the EHR")
    void brMap10_noMaritalStatus() {
        assertFalse(mapper.toFhirResource(minimal()).containsKey("maritalStatus"));
    }

    @Test
    @DisplayName("BR-MAP-11: inbound identifiers are matched by system; other systems are ignored")
    void brMap11_inboundIdentifiers() {
        Map<String, Object> r = new HashMap<>();
        r.put("identifier", List.of(
                Map.of("system", "urn:other", "value", "IGNORED"),
                Map.of("system", MRN_SYSTEM, "value", "MRN-IN-1"),
                Map.of("system", SSN_SYSTEM, "value", "000-00-0001")));
        r.put("name", List.of(Map.of("family", "Patient", "given", List.of("Test", "Middle"))));
        r.put("birthDate", "1990-12-31");

        Patient p = mapper.fromFhirResource(r);
        assertEquals("MRN-IN-1", p.getMrn());
        assertEquals("000-00-0001", p.getSsn());
        assertEquals("Patient", p.getLastName());
        assertEquals("Test", p.getFirstName());
        assertEquals("Middle", p.getMiddleName());
        assertEquals(LocalDate.of(1990, 12, 31), p.getDateOfBirth());
    }

    @Test
    @DisplayName("BR-MAP-11: inbound resources with missing/empty name, given or identifiers map to nulls")
    void brMap11_inboundSparse() {
        Patient empty = mapper.fromFhirResource(Map.of("name", List.of()));
        assertNull(empty.getMrn());
        assertNull(empty.getLastName());
        assertNull(empty.getGender());
        assertNull(empty.getDateOfBirth());

        Patient noGiven = mapper.fromFhirResource(Map.of("name", List.of(Map.of("family", "Only"))));
        assertEquals("Only", noGiven.getLastName());
        assertNull(noGiven.getFirstName());

        Patient emptyGiven = mapper.fromFhirResource(Map.of("name", List.of(Map.of("family", "F", "given", List.of()))));
        assertNull(emptyGiven.getFirstName());

        Patient oneGiven = mapper.fromFhirResource(Map.of("name", List.of(Map.of("given", List.of("One")))));
        assertEquals("One", oneGiven.getFirstName());
        assertNull(oneGiven.getMiddleName());
    }
}
