package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FhirPatientMapperTest {

    private FhirPatientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FhirPatientMapper();
    }

    private Patient buildTestPatient() {
        Patient patient = new Patient();
        patient.setId(1L);
        patient.setMrn("MRN001");
        patient.setSsn("123-45-6789");
        patient.setFirstName("John");
        patient.setMiddleName("Michael");
        patient.setLastName("Doe");
        patient.setGender(Gender.MALE);
        patient.setDateOfBirth(LocalDate.of(1985, 3, 15));
        patient.setActive(true);
        return patient;
    }

    @Test
    void toFhirResource_mapsResourceTypeAndId() {
        Patient patient = buildTestPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        assertThat(fhir.get("resourceType")).isEqualTo("Patient");
        assertThat(fhir.get("id")).isEqualTo("1");
    }

    @Test
    void toFhirResource_mapsIdentifiers() {
        Patient patient = buildTestPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) fhir.get("identifier");
        assertThat(identifiers).hasSize(2);

        Map<String, Object> mrnId = identifiers.get(0);
        assertThat(mrnId.get("system")).isEqualTo("http://hospital.example.org/mrn");
        assertThat(mrnId.get("value")).isEqualTo("MRN001");

        Map<String, Object> ssnId = identifiers.get(1);
        assertThat(ssnId.get("system")).isEqualTo("http://hl7.org/fhir/sid/us-ssn");
        assertThat(ssnId.get("value")).isEqualTo("123-45-6789");
    }

    @Test
    void toFhirResource_mapsName() {
        Patient patient = buildTestPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> names = (List<Map<String, Object>>) fhir.get("name");
        assertThat(names).hasSize(1);

        Map<String, Object> name = names.get(0);
        assertThat(name.get("family")).isEqualTo("Doe");

        @SuppressWarnings("unchecked")
        List<String> given = (List<String>) name.get("given");
        assertThat(given).containsExactly("John", "Michael");
    }

    @Test
    void toFhirResource_mapsGenderAndBirthDate() {
        Patient patient = buildTestPatient();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        assertThat(fhir.get("gender")).isEqualTo("male");
        assertThat(fhir.get("birthDate")).isEqualTo("1985-03-15");
    }

    @Test
    void fromFhirResource_mapsBackToPatient() {
        Map<String, Object> fhir = Map.of(
                "resourceType", "Patient",
                "identifier", List.of(
                        Map.of("system", "http://hospital.example.org/mrn", "value", "MRN002"),
                        Map.of("system", "http://hl7.org/fhir/sid/us-ssn", "value", "999-88-7777")
                ),
                "name", List.of(Map.of(
                        "family", "Smith",
                        "given", List.of("Jane")
                )),
                "gender", "female",
                "birthDate", "1990-07-20",
                "active", true
        );

        Patient patient = mapper.fromFhirResource(fhir);

        assertThat(patient.getMrn()).isEqualTo("MRN002");
        assertThat(patient.getSsn()).isEqualTo("999-88-7777");
        assertThat(patient.getFirstName()).isEqualTo("Jane");
        assertThat(patient.getLastName()).isEqualTo("Smith");
        assertThat(patient.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(patient.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 7, 20));
        assertThat(patient.getActive()).isTrue();
    }

    @Test
    void roundTrip_preservesKeyFields() {
        Patient original = buildTestPatient();

        Map<String, Object> fhir = mapper.toFhirResource(original);
        Patient roundTripped = mapper.fromFhirResource(fhir);

        assertThat(roundTripped.getMrn()).isEqualTo(original.getMrn());
        assertThat(roundTripped.getFirstName()).isEqualTo(original.getFirstName());
        assertThat(roundTripped.getLastName()).isEqualTo(original.getLastName());
        assertThat(roundTripped.getGender()).isEqualTo(original.getGender());
        assertThat(roundTripped.getDateOfBirth()).isEqualTo(original.getDateOfBirth());
        assertThat(roundTripped.getActive()).isEqualTo(original.getActive());
    }
}
