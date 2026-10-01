package com.medchart.ehr.controller;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.EncounterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EncounterController.class)
@DisplayName("EncounterController Integration Tests")
class EncounterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    private ObjectMapper objectMapper;
    private Encounter sampleEncounter;

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

        Provider provider = Provider.builder()
                .id(10L)
                .npi("1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .build();

        sampleEncounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 3, 18, 10, 0))
                .build();
    }

    @Nested
    @DisplayName("GET /v1/encounters/{id}")
    class GetById {

        @Test
        @WithMockUser
        @DisplayName("should return encounter when found")
        void shouldReturnEncounterWhenFound() throws Exception {
            when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.of(sampleEncounter));

            mockMvc.perform(get("/v1/encounters/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000101"))
                    .andExpect(jsonPath("$.status").value("SCHEDULED"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when encounter not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(encounterService.findByIdWithDetails(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/encounters/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/encounters/number/{encounterNumber}")
    class GetByNumber {

        @Test
        @WithMockUser
        @DisplayName("should return encounter by number")
        void shouldReturnEncounterByNumber() throws Exception {
            when(encounterService.findByEncounterNumber("ENC-2026-000101"))
                    .thenReturn(Optional.of(sampleEncounter));

            mockMvc.perform(get("/v1/encounters/number/ENC-2026-000101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000101"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 for unknown encounter number")
        void shouldReturn404ForUnknownNumber() throws Exception {
            when(encounterService.findByEncounterNumber("INVALID")).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/encounters/number/INVALID"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/encounters/patient/{patientId}")
    class GetByPatient {

        @Test
        @WithMockUser
        @DisplayName("should return encounters for patient")
        void shouldReturnEncountersForPatient() throws Exception {
            when(encounterService.findByPatientId(1L)).thenReturn(List.of(sampleEncounter));

            mockMvc.perform(get("/v1/encounters/patient/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-000101"));
        }
    }

    @Nested
    @DisplayName("GET /v1/encounters/status/{status}")
    class GetByStatus {

        @Test
        @WithMockUser
        @DisplayName("should return encounters by status")
        void shouldReturnEncountersByStatus() throws Exception {
            when(encounterService.findByStatus(EncounterStatus.SCHEDULED))
                    .thenReturn(List.of(sampleEncounter));

            mockMvc.perform(get("/v1/encounters/status/SCHEDULED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
        }
    }

    @Nested
    @DisplayName("POST /v1/encounters/{id}/check-in")
    class CheckIn {

        @Test
        @WithMockUser
        @DisplayName("should check in encounter")
        void shouldCheckInEncounter() throws Exception {
            mockMvc.perform(post("/v1/encounters/1/check-in").with(csrf()))
                    .andExpect(status().isOk());

            verify(encounterService).checkIn(1L);
        }
    }

    @Nested
    @DisplayName("POST /v1/encounters/{id}/start")
    class Start {

        @Test
        @WithMockUser
        @DisplayName("should start encounter")
        void shouldStartEncounter() throws Exception {
            mockMvc.perform(post("/v1/encounters/1/start").with(csrf()))
                    .andExpect(status().isOk());

            verify(encounterService).startEncounter(1L);
        }
    }

    @Nested
    @DisplayName("POST /v1/encounters/{id}/complete")
    class Complete {

        @Test
        @WithMockUser
        @DisplayName("should complete encounter with notes")
        void shouldCompleteEncounterWithNotes() throws Exception {
            mockMvc.perform(post("/v1/encounters/1/complete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("\"Visit completed successfully\""))
                    .andExpect(status().isOk());

            verify(encounterService).completeEncounter(eq(1L), any());
        }
    }

    @Nested
    @DisplayName("POST /v1/encounters/{id}/cancel")
    class Cancel {

        @Test
        @WithMockUser
        @DisplayName("should cancel encounter")
        void shouldCancelEncounter() throws Exception {
            mockMvc.perform(post("/v1/encounters/1/cancel").with(csrf()))
                    .andExpect(status().isOk());

            verify(encounterService).cancelEncounter(1L);
        }
    }

    @Nested
    @DisplayName("POST /v1/encounters/{id}/no-show")
    class NoShow {

        @Test
        @WithMockUser
        @DisplayName("should mark encounter as no-show")
        void shouldMarkEncounterAsNoShow() throws Exception {
            mockMvc.perform(post("/v1/encounters/1/no-show").with(csrf()))
                    .andExpect(status().isOk());

            verify(encounterService).markNoShow(1L);
        }
    }

    @Nested
    @DisplayName("PUT /v1/encounters/{id}")
    class Update {

        @Test
        @WithMockUser
        @DisplayName("should update encounter when found")
        void shouldUpdateEncounterWhenFound() throws Exception {
            when(encounterService.findById(1L)).thenReturn(Optional.of(sampleEncounter));
            when(encounterService.update(any(Encounter.class))).thenReturn(sampleEncounter);

            mockMvc.perform(put("/v1/encounters/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleEncounter)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when updating non-existent encounter")
        void shouldReturn404WhenNotFound() throws Exception {
            when(encounterService.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(put("/v1/encounters/999")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleEncounter)))
                    .andExpect(status().isNotFound());
        }
    }
}
