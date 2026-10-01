package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EncounterController.class)
@AutoConfigureMockMvc(addFilters = false)
class EncounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Patient patient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .build();

        encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(patient)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 5, 25, 10, 0))
                .build();
    }

    @Test
    void getById_shouldReturn200_whenFound() throws Exception {
        when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        mockMvc.perform(get("/v1/encounters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber", is("ENC-2026-000101")))
                .andExpect(jsonPath("$.status", is("SCHEDULED")));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(encounterService.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNumber_shouldReturn200() throws Exception {
        when(encounterService.findByEncounterNumber("ENC-2026-000101"))
                .thenReturn(Optional.of(encounter));

        mockMvc.perform(get("/v1/encounters/number/ENC-2026-000101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber", is("ENC-2026-000101")));
    }

    @Test
    void getByNumber_shouldReturn404_whenNotFound() throws Exception {
        when(encounterService.findByEncounterNumber("INVALID"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/number/INVALID"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByPatient_shouldReturnList() throws Exception {
        when(encounterService.findByPatientId(1L)).thenReturn(List.of(encounter));

        mockMvc.perform(get("/v1/encounters/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getByProvider_shouldReturnList() throws Exception {
        when(encounterService.findByProviderId(1L)).thenReturn(List.of(encounter));

        mockMvc.perform(get("/v1/encounters/provider/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getByStatus_shouldReturnFilteredList() throws Exception {
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(List.of(encounter));

        mockMvc.perform(get("/v1/encounters/status/SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void checkIn_shouldReturn200() throws Exception {
        doNothing().when(encounterService).checkIn(1L);

        mockMvc.perform(post("/v1/encounters/1/check-in"))
                .andExpect(status().isOk());

        verify(encounterService).checkIn(1L);
    }

    @Test
    void start_shouldReturn200() throws Exception {
        doNothing().when(encounterService).startEncounter(1L);

        mockMvc.perform(post("/v1/encounters/1/start"))
                .andExpect(status().isOk());

        verify(encounterService).startEncounter(1L);
    }

    @Test
    void complete_shouldReturn200() throws Exception {
        doNothing().when(encounterService).completeEncounter(eq(1L), any());

        mockMvc.perform(post("/v1/encounters/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"Visit completed\""))
                .andExpect(status().isOk());

        verify(encounterService).completeEncounter(eq(1L), any());
    }

    @Test
    void cancel_shouldReturn200() throws Exception {
        doNothing().when(encounterService).cancelEncounter(1L);

        mockMvc.perform(post("/v1/encounters/1/cancel"))
                .andExpect(status().isOk());

        verify(encounterService).cancelEncounter(1L);
    }

    @Test
    void noShow_shouldReturn200() throws Exception {
        doNothing().when(encounterService).markNoShow(1L);

        mockMvc.perform(post("/v1/encounters/1/no-show"))
                .andExpect(status().isOk());

        verify(encounterService).markNoShow(1L);
    }

    @Test
    void update_shouldReturn200_whenExists() throws Exception {
        when(encounterService.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterService.update(any(Encounter.class))).thenReturn(encounter);

        mockMvc.perform(put("/v1/encounters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounter)))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldReturn404_whenNotFound() throws Exception {
        when(encounterService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/encounters/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounter)))
                .andExpect(status().isNotFound());
    }
}
