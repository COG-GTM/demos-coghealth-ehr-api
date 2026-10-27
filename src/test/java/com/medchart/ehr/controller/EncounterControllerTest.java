package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.InvalidEncounterStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EncounterControllerTest {

    private EncounterService encounterService;
    private MockMvc mockMvc;
    private EncounterDTO dto;

    @BeforeEach
    void setUp() {
        encounterService = mock(EncounterService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mockMvc = MockMvcBuilders.standaloneSetup(new EncounterController(encounterService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        Patient patient = Patient.builder()
                .id(1L).mrn("MRN-1").firstName("Bob").lastName("Smith")
                .dateOfBirth(LocalDate.of(1980, 5, 1)).ssn("123-45-6789").email("bob@example.com")
                .build();
        Encounter encounter = Encounter.builder()
                .id(42L).encounterNumber("ENC-2026-000042").patient(patient)
                .encounterType(EncounterType.OUTPATIENT).status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 1, 1, 9, 0)).createdBy("dr.alice")
                .build();
        dto = new EncounterMapper().toDto(encounter);
    }

    @Test
    void getByIdNeverReturnsSsn() throws Exception {
        when(encounterService.findByIdWithDetails(42L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/v1/encounters/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.mrn").value("MRN-1"))
                .andExpect(jsonPath("$.patient.firstName").value("Bob"))
                .andExpect(jsonPath("$.patient.dateOfBirth").exists())
                .andExpect(jsonPath("$.patient.ssn").doesNotExist())
                .andExpect(jsonPath("$.patient.email").doesNotExist())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(content().string(not(containsString("123-45-6789"))));
    }

    @Test
    void illegalTransitionReturnsConflict() throws Exception {
        when(encounterService.cancelEncounter(42L))
                .thenThrow(new InvalidEncounterStateException("cannot move from COMPLETED to CANCELLED"));

        mockMvc.perform(post("/v1/encounters/42/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cannot move from COMPLETED to CANCELLED"));
    }

    @Test
    void readOnlyFieldsInRequestBodyAreIgnored() throws Exception {
        when(encounterService.update(eq(42L), any(EncounterDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/v1/encounters/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"encounterNumber\":\"FORGED\",\"status\":\"COMPLETED\","
                                + "\"createdBy\":\"mallory\",\"room\":\"12B\"}"))
                .andExpect(status().isOk());

        verify(encounterService).update(eq(42L), argThat(req ->
                req.getId() == null
                        && req.getEncounterNumber() == null
                        && req.getStatus() == null
                        && "12B".equals(req.getRoom())));
    }

    @Test
    void explicitNullInRequestBodyIsDistinguishedFromOmittedField() throws Exception {
        when(encounterService.update(eq(42L), any(EncounterDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/v1/encounters/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\":null}"))
                .andExpect(status().isOk());

        verify(encounterService).update(eq(42L), argThat(req ->
                req.isExplicitlySet("room") && !req.isExplicitlySet("notes")));
    }
}
