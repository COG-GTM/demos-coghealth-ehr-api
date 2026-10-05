package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.MaritalStatus;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FhirPatientMapperTest {

    private final FhirPatientMapper mapper = new FhirPatientMapper();

    @Test
    @SuppressWarnings("unchecked")
    void toFhirResourceMapsCoreFields() {
        Patient patient = Patient.builder()
                .id(5L)
                .mrn("MRN005")
                .firstName("Grace")
                .middleName("B")
                .lastName("Hopper")
                .gender(Gender.FEMALE)
                .dateOfBirth(LocalDate.of(1906, 12, 9))
                .maritalStatus(MaritalStatus.MARRIED)
                .active(true)
                .build();

        Map<String, Object> fhir = mapper.toFhirResource(patient);

        assertThat(fhir).containsEntry("resourceType", "Patient")
                .containsEntry("id", "5")
                .containsEntry("gender", "female")
                .containsEntry("birthDate", "1906-12-09")
                .containsEntry("active", true);
        Map<String, Object> name = ((List<Map<String, Object>>) fhir.get("name")).get(0);
        assertThat(name).containsEntry("family", "Hopper")
                .containsEntry("given", List.of("Grace", "B"));
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) fhir.get("identifier");
        assertThat(identifiers).anySatisfy(id -> assertThat(id).containsEntry("value", "MRN005"));
    }

    @Test
    void fromFhirResourceMapsIdentifiersNameAndGender() {
        Map<String, Object> fhir = Map.of(
                "identifier", List.of(Map.of("system", "http://hospital.example.org/mrn", "value", "MRN009")),
                "name", List.of(Map.of("family", "Turing", "given", List.of("Alan", "M"))),
                "gender", "male",
                "birthDate", "1912-06-23");

        Patient patient = mapper.fromFhirResource(fhir);

        assertThat(patient.getMrn()).isEqualTo("MRN009");
        assertThat(patient.getFirstName()).isEqualTo("Alan");
        assertThat(patient.getMiddleName()).isEqualTo("M");
        assertThat(patient.getLastName()).isEqualTo("Turing");
        assertThat(patient.getGender()).isEqualTo(Gender.MALE);
        assertThat(patient.getDateOfBirth()).isEqualTo(LocalDate.of(1912, 6, 23));
        assertThat(patient.getActive()).isTrue();
    }

    @Test
    void roundTripPreservesNameAndGender() {
        Patient original = Patient.builder()
                .id(1L).mrn("MRN100").firstName("Katherine").lastName("Johnson")
                .gender(Gender.FEMALE).dateOfBirth(LocalDate.of(1918, 8, 26)).active(true)
                .build();

        Patient roundTripped = mapper.fromFhirResource(mapper.toFhirResource(original));

        assertThat(roundTripped.getMrn()).isEqualTo("MRN100");
        assertThat(roundTripped.getFirstName()).isEqualTo("Katherine");
        assertThat(roundTripped.getLastName()).isEqualTo("Johnson");
        assertThat(roundTripped.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(roundTripped.getDateOfBirth()).isEqualTo(original.getDateOfBirth());
    }
}
