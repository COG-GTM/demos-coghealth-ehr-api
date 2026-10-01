package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.MaritalStatus;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FhirPatientMapperTest {

    private FhirPatientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FhirPatientMapper();
    }

    private Patient fullyPopulatedPatient() {
        Address address = Address.builder()
            .street1("123 Main St")
            .street2("Apt 4B")
            .city("Boston")
            .state("MA")
            .zipCode("02115")
            .country("USA")
            .build();

        return Patient.builder()
            .id(42L)
            .mrn("MRN-001")
            .ssn("123-45-6789")
            .firstName("Jane")
            .middleName("Q")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1980, 3, 5))
            .gender(Gender.FEMALE)
            .maritalStatus(MaritalStatus.MARRIED)
            .email("jane.doe@example.com")
            .phoneHome("617-555-0100")
            .phoneMobile("617-555-0101")
            .address(address)
            .active(true)
            .deceased(false)
            .build();
    }

    private Patient minimalPatient() {
        return Patient.builder()
            .id(7L)
            .mrn("MRN-002")
            .firstName("John")
            .lastName("Smith")
            .active(false)
            .deceased(false)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Nested
    class ToFhirResource {

        @Test
        void mapsFullyPopulatedPatient() {
            Map<String, Object> fhir = mapper.toFhirResource(fullyPopulatedPatient());

            assertThat(fhir).containsEntry("resourceType", "Patient");
            assertThat(fhir).containsEntry("id", "42");
            assertThat(fhir).containsEntry("gender", "female");
            assertThat(fhir).containsEntry("birthDate", "1980-03-05");
            assertThat(fhir).containsEntry("active", true);
            assertThat(fhir).doesNotContainKeys("deceasedBoolean", "deceasedDateTime");

            assertThat(mapList(fhir.get("identifier"))).containsExactly(
                Map.of("system", "http://hospital.example.org/mrn", "value", "MRN-001", "use", "official"),
                Map.of("system", "http://hl7.org/fhir/sid/us-ssn", "value", "123-45-6789", "use", "secondary")
            );

            Map<String, Object> name = mapList(fhir.get("name")).get(0);
            assertThat(name).containsEntry("use", "official");
            assertThat(name).containsEntry("family", "Doe");
            assertThat(name).containsEntry("given", List.of("Jane", "Q"));

            assertThat(mapList(fhir.get("telecom"))).containsExactly(
                Map.of("system", "phone", "value", "617-555-0100", "use", "home"),
                Map.of("system", "phone", "value", "617-555-0101", "use", "mobile"),
                Map.of("system", "email", "value", "jane.doe@example.com")
            );

            Map<String, Object> address = mapList(fhir.get("address")).get(0);
            assertThat(address).containsEntry("use", "home");
            assertThat(address).containsEntry("line", List.of("123 Main St", "Apt 4B"));
            assertThat(address).containsEntry("city", "Boston");
            assertThat(address).containsEntry("state", "MA");
            assertThat(address).containsEntry("postalCode", "02115");
            assertThat(address).containsEntry("country", "US");

            Map<String, Object> coding = mapList(((Map<String, Object>) fhir.get("maritalStatus")).get("coding")).get(0);
            assertThat(coding).containsEntry("system", "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus");
            assertThat(coding).containsEntry("code", "M");
        }

        @Test
        void omitsOptionalFieldsWhenNull() {
            Map<String, Object> fhir = mapper.toFhirResource(minimalPatient());

            assertThat(fhir).doesNotContainKeys("birthDate", "address", "maritalStatus",
                "deceasedBoolean", "deceasedDateTime");
            assertThat(fhir).containsEntry("active", false);
            assertThat(fhir).containsEntry("gender", "unknown");
            assertThat(mapList(fhir.get("telecom"))).isEmpty();

            Map<String, Object> name = mapList(fhir.get("name")).get(0);
            assertThat(name).containsEntry("given", List.of("John"));
        }

        @Test
        void usesBlankValueWhenSsnMissing() {
            Map<String, Object> ssnIdentifier = mapList(mapper.toFhirResource(minimalPatient()).get("identifier")).get(1);

            assertThat(ssnIdentifier).containsEntry("system", "http://hl7.org/fhir/sid/us-ssn");
            assertThat(ssnIdentifier).containsEntry("value", "");
        }

        @Test
        void includesDeceasedDateTimeWhenDeceased() {
            Patient patient = minimalPatient();
            patient.setDeceased(true);
            patient.setDeceasedDate(LocalDateTime.of(2024, 1, 2, 3, 4, 5));

            Map<String, Object> fhir = mapper.toFhirResource(patient);

            assertThat(fhir).containsEntry("deceasedBoolean", true);
            assertThat(fhir).containsEntry("deceasedDateTime", "2024-01-02T03:04:05");
        }

        @Test
        void omitsDeceasedDateTimeWhenDateMissing() {
            Patient patient = minimalPatient();
            patient.setDeceased(true);

            Map<String, Object> fhir = mapper.toFhirResource(patient);

            assertThat(fhir).containsEntry("deceasedBoolean", true);
            assertThat(fhir).doesNotContainKey("deceasedDateTime");
        }

        @Test
        void omitsAddressLinesThatAreNull() {
            Patient patient = minimalPatient();
            patient.setAddress(Address.builder().city("Boston").state("MA").zipCode("02115").build());

            Map<String, Object> address = mapList(mapper.toFhirResource(patient).get("address")).get(0);

            assertThat(address).containsEntry("line", List.of());
        }

        @ParameterizedTest
        @CsvSource({"MALE,male", "FEMALE,female", "OTHER,other", "UNKNOWN,unknown"})
        void mapsEachGender(Gender gender, String expected) {
            Patient patient = minimalPatient();
            patient.setGender(gender);

            assertThat(mapper.toFhirResource(patient)).containsEntry("gender", expected);
        }

        @ParameterizedTest
        @CsvSource({"SINGLE,S", "MARRIED,M", "DIVORCED,D", "WIDOWED,W", "SEPARATED,UNK",
            "DOMESTIC_PARTNER,UNK", "UNKNOWN,UNK"})
        void mapsEachMaritalStatus(MaritalStatus status, String expectedCode) {
            Patient patient = minimalPatient();
            patient.setMaritalStatus(status);

            Map<String, Object> maritalStatus = (Map<String, Object>) mapper.toFhirResource(patient).get("maritalStatus");

            assertThat(mapList(maritalStatus.get("coding")).get(0)).containsEntry("code", expectedCode);
        }
    }

    @Nested
    class FromFhirResource {

        private Map<String, Object> fhirPayload() {
            Map<String, Object> name = new HashMap<>();
            name.put("family", "Doe");
            name.put("given", List.of("Jane", "Q"));

            Map<String, Object> fhir = new HashMap<>();
            fhir.put("identifier", List.of(
                Map.of("system", "http://hospital.example.org/mrn", "value", "MRN-001"),
                Map.of("system", "http://hl7.org/fhir/sid/us-ssn", "value", "123-45-6789")
            ));
            fhir.put("name", List.of(name));
            fhir.put("gender", "female");
            fhir.put("birthDate", "1980-03-05");
            fhir.put("active", false);
            return fhir;
        }

        @Test
        void mapsAllSupportedFields() {
            Patient patient = mapper.fromFhirResource(fhirPayload());

            assertThat(patient.getMrn()).isEqualTo("MRN-001");
            assertThat(patient.getSsn()).isEqualTo("123-45-6789");
            assertThat(patient.getFirstName()).isEqualTo("Jane");
            assertThat(patient.getMiddleName()).isEqualTo("Q");
            assertThat(patient.getLastName()).isEqualTo("Doe");
            assertThat(patient.getGender()).isEqualTo(Gender.FEMALE);
            assertThat(patient.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 3, 5));
            assertThat(patient.getActive()).isFalse();
        }

        @Test
        void defaultsToActiveWhenFlagAbsent() {
            Map<String, Object> fhir = fhirPayload();
            fhir.remove("active");

            assertThat(mapper.fromFhirResource(fhir).getActive()).isTrue();
        }

        @Test
        void leavesFieldsUnsetWhenSectionsAbsent() {
            Patient patient = mapper.fromFhirResource(new HashMap<>());

            assertThat(patient.getMrn()).isNull();
            assertThat(patient.getSsn()).isNull();
            assertThat(patient.getFirstName()).isNull();
            assertThat(patient.getLastName()).isNull();
            assertThat(patient.getGender()).isNull();
            assertThat(patient.getDateOfBirth()).isNull();
            assertThat(patient.getActive()).isTrue();
        }

        @Test
        void ignoresUnknownIdentifierSystems() {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("identifier", List.of(Map.of("system", "http://other.example.org/id", "value", "X-1")));

            Patient patient = mapper.fromFhirResource(fhir);

            assertThat(patient.getMrn()).isNull();
            assertThat(patient.getSsn()).isNull();
        }

        @Test
        void mapsSingleGivenNameWithoutMiddleName() {
            Map<String, Object> name = new HashMap<>();
            name.put("family", "Smith");
            name.put("given", List.of("John"));
            Map<String, Object> fhir = fhirPayload();
            fhir.put("name", List.of(name));

            Patient patient = mapper.fromFhirResource(fhir);

            assertThat(patient.getFirstName()).isEqualTo("John");
            assertThat(patient.getMiddleName()).isNull();
        }

        @Test
        void toleratesNameWithoutGivenNames() {
            Map<String, Object> name = new HashMap<>();
            name.put("family", "Smith");
            Map<String, Object> fhir = fhirPayload();
            fhir.put("name", List.of(name));

            Patient patient = mapper.fromFhirResource(fhir);

            assertThat(patient.getLastName()).isEqualTo("Smith");
            assertThat(patient.getFirstName()).isNull();
        }

        @Test
        void toleratesEmptyNameList() {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("name", new ArrayList<Map<String, Object>>());

            Patient patient = mapper.fromFhirResource(fhir);

            assertThat(patient.getLastName()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"male,MALE", "MALE,MALE", "female,FEMALE", "other,OTHER", "unknown,OTHER", "zzz,OTHER"})
        void mapsFhirGender(String fhirGender, Gender expected) {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("gender", fhirGender);

            assertThat(mapper.fromFhirResource(fhir).getGender()).isEqualTo(expected);
        }

        @Test
        void throwsOnMalformedBirthDate() {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("birthDate", "03/05/1980");

            assertThatThrownBy(() -> mapper.fromFhirResource(fhir))
                .isInstanceOf(DateTimeParseException.class);
        }

        @Test
        void throwsOnIdentifierThatIsNotAList() {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("identifier", "MRN-001");

            assertThatThrownBy(() -> mapper.fromFhirResource(fhir))
                .isInstanceOf(ClassCastException.class);
        }

        @Test
        void throwsOnIdentifierEntriesThatAreNotObjects() {
            Map<String, Object> fhir = fhirPayload();
            fhir.put("identifier", List.of("MRN-001"));

            assertThatThrownBy(() -> mapper.fromFhirResource(fhir))
                .isInstanceOf(ClassCastException.class);
        }

        @Test
        void throwsOnGivenNameThatIsNotAList() {
            Map<String, Object> name = new HashMap<>();
            name.put("family", "Doe");
            name.put("given", "Jane");
            Map<String, Object> fhir = fhirPayload();
            fhir.put("name", List.of(name));

            assertThatThrownBy(() -> mapper.fromFhirResource(fhir))
                .isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    void roundTripPreservesCoreFields() {
        Patient original = fullyPopulatedPatient();

        Patient roundTripped = mapper.fromFhirResource(mapper.toFhirResource(original));

        assertThat(roundTripped.getMrn()).isEqualTo(original.getMrn());
        assertThat(roundTripped.getSsn()).isEqualTo(original.getSsn());
        assertThat(roundTripped.getFirstName()).isEqualTo(original.getFirstName());
        assertThat(roundTripped.getMiddleName()).isEqualTo(original.getMiddleName());
        assertThat(roundTripped.getLastName()).isEqualTo(original.getLastName());
        assertThat(roundTripped.getGender()).isEqualTo(original.getGender());
        assertThat(roundTripped.getDateOfBirth()).isEqualTo(original.getDateOfBirth());
        assertThat(roundTripped.getActive()).isEqualTo(original.getActive());
    }
}
