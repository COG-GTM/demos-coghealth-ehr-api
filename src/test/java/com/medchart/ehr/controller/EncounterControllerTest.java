package com.medchart.ehr.controller;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.dto.EncounterCreateRequest;
import com.medchart.ehr.dto.EncounterUpdateRequest;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.InvalidEncounterRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EncounterControllerTest {

    private EncounterService encounterService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        encounterService = mock(EncounterService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EncounterController(encounterService)).build();
    }

    @Test
    void putRejectsPatientReassignmentAndAuditFieldForgery() throws Exception {
        String body = "{\"chiefComplaint\":\"x\",\"patient\":{\"id\":77},\"createdBy\":\"dr.anderson\","
                + "\"version\":0,\"encounterNumber\":\"ENC-1\",\"status\":\"COMPLETED\"}";

        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(encounterService);
    }

    @Test
    void putRejectsEachServerOwnedField() throws Exception {
        String[] fields = {"\"id\":1", "\"patient\":{\"id\":77}", "\"patientId\":77", "\"createdBy\":\"x\"",
                "\"version\":0", "\"encounterNumber\":\"ENC-1\"", "\"status\":\"COMPLETED\"", "\"diagnoses\":[]"};
        for (String field : fields) {
            mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{" + field + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(encounterService);
    }

    @Test
    void postRejectsExistingIdAndVersion() throws Exception {
        String body = "{\"id\":42,\"version\":3,\"patientId\":1,\"encounterType\":\"OUTPATIENT\","
                + "\"encounterDateTime\":\"2026-10-27T09:30:00\"}";

        mockMvc.perform(post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(encounterService);
    }

    @Test
    void postRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content("{\"room\":\"4\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(encounterService);
    }

    @Test
    void postPassesWhitelistedFieldsToService() throws Exception {
        when(encounterService.create(any())).thenReturn(new Encounter());
        String body = "{\"patientId\":1,\"attendingProviderId\":10,\"encounterType\":\"OUTPATIENT\","
                + "\"encounterDateTime\":\"2026-10-27T09:30:00\",\"room\":\"4\"}";

        mockMvc.perform(post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<EncounterCreateRequest> captor = ArgumentCaptor.forClass(EncounterCreateRequest.class);
        verify(encounterService).create(captor.capture());
        assertEquals(1L, captor.getValue().getPatientId());
        assertEquals(10L, captor.getValue().getAttendingProviderId());
        assertEquals("4", captor.getValue().getRoom());
    }

    @Test
    void postMapsUnknownPatientToBadRequest() throws Exception {
        when(encounterService.create(any())).thenThrow(new InvalidEncounterRequestException("Unknown patientId"));
        String body = "{\"patientId\":999,\"encounterType\":\"OUTPATIENT\",\"encounterDateTime\":\"2026-10-27T09:30:00\"}";

        mockMvc.perform(post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putTracksWhichFieldsWereSent() throws Exception {
        when(encounterService.update(eq(42L), any())).thenReturn(Optional.of(new Encounter()));

        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chiefComplaint\":\"Better\",\"bed\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<EncounterUpdateRequest> captor = ArgumentCaptor.forClass(EncounterUpdateRequest.class);
        verify(encounterService).update(eq(42L), captor.capture());
        EncounterUpdateRequest request = captor.getValue();
        assertTrue(request.isProvided(EncounterUpdateRequest.CHIEF_COMPLAINT));
        assertTrue(request.isProvided(EncounterUpdateRequest.BED));
        assertNull(request.getBed());
        assertFalse(request.isProvided(EncounterUpdateRequest.ROOM));
        assertFalse(request.isProvided(EncounterUpdateRequest.NOTES));
    }

    @Test
    void putRejectsClearingRequiredColumns() throws Exception {
        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{\"encounterType\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{\"encounterDateTime\":null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(encounterService);
    }

    @Test
    void putReturnsNotFoundForMissingEncounter() throws Exception {
        when(encounterService.update(eq(7L), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/encounters/7").contentType(MediaType.APPLICATION_JSON).content("{\"room\":\"1\"}"))
                .andExpect(status().isNotFound());
    }
}
