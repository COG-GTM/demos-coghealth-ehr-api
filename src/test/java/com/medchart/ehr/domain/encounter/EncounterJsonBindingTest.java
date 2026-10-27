package com.medchart.ehr.domain.encounter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncounterJsonBindingTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serverOwnedFieldsAreNotDeserialized() throws Exception {
        String json = "{\"id\":12,\"encounterNumber\":\"ENC-X\",\"patient\":{\"id\":3,\"version\":0},"
                + "\"attendingProvider\":{\"id\":9},\"status\":\"COMPLETED\","
                + "\"diagnoses\":[{\"encounter\":{\"id\":7,\"version\":0}}],"
                + "\"createdBy\":\"x\",\"version\":4,\"chiefComplaint\":\"cough\"}";

        Encounter encounter = mapper.readValue(json, Encounter.class);

        assertNull(encounter.getId());
        assertNull(encounter.getEncounterNumber());
        assertNull(encounter.getPatient());
        assertNull(encounter.getAttendingProvider());
        assertNull(encounter.getStatus());
        assertTrue(encounter.getDiagnoses().isEmpty());
        assertNull(encounter.getCreatedBy());
        assertNull(encounter.getVersion());
        assertEquals("cough", encounter.getChiefComplaint());
    }

    @Test
    void serverOwnedFieldsAreStillSerialized() throws Exception {
        Encounter encounter = Encounter.builder()
                .id(12L).encounterNumber("ENC-2026-000012").status(EncounterStatus.SCHEDULED)
                .createdBy("dr.smith").version(3L).build();

        String json = mapper.writeValueAsString(encounter);

        assertTrue(json.contains("\"id\":12"));
        assertTrue(json.contains("\"encounterNumber\":\"ENC-2026-000012\""));
        assertTrue(json.contains("\"status\":\"SCHEDULED\""));
        assertTrue(json.contains("\"createdBy\":\"dr.smith\""));
        assertTrue(json.contains("\"version\":3"));
    }
}
