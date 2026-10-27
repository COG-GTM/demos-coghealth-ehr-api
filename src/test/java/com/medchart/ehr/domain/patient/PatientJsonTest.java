package com.medchart.ehr.domain.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PatientJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void patientEntitySerializationOmitsSsnAndBackReferences() throws Exception {
        Patient patient = Patient.builder()
                .id(1L)
                .mrn("MRN0000001")
                .ssn("123-45-6789")
                .firstName("Ned")
                .lastName("Flanders")
                .dateOfBirth(LocalDate.of(1960, 1, 1))
                .build();
        patient.addIdentifier(PatientIdentifier.builder()
                .identifierType(IdentifierType.MRN)
                .identifierValue("X1")
                .build());
        patient.addEmergencyContact(EmergencyContact.builder().firstName("Maude").lastName("Flanders").build());

        String json = mapper.writeValueAsString(patient);

        assertThat(json).doesNotContain("ssn").doesNotContain("123-45-6789");
        assertThat(json).contains("MRN0000001").contains("X1").contains("Maude");
    }

    @Test
    void encounterDiagnosisDoesNotSerializeItsEncounter() throws Exception {
        Encounter encounter = Encounter.builder().id(5L).encounterNumber("ENC-1").build();
        encounter.addDiagnosis(EncounterDiagnosis.builder().id(9L).build());

        String json = mapper.writeValueAsString(encounter.getDiagnoses().get(0));

        assertThat(json).doesNotContain("ENC-1");
    }

    @Test
    void encounterDiagnosisStillAcceptsItsEncounterReferenceOnInput() throws Exception {
        EncounterDiagnosis diagnosis = mapper.readValue("{\"encounter\":{\"id\":5}}", EncounterDiagnosis.class);

        assertThat(diagnosis.getEncounter()).isNotNull();
        assertThat(diagnosis.getEncounter().getId()).isEqualTo(5L);
    }
}
