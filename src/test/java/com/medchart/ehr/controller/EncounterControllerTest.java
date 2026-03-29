package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
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
    private com.medchart.ehr.config.JwtTokenProvider jwtTokenProvider;

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
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .build();

        Provider provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .build();

        encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000001")
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 3, 29, 10, 0))
                .department("Medicine")
                .chiefComplaint("Annual checkup")
                .build();
    }

    @Test
    void getById_existingId_returns200() throws Exception {
        when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        mockMvc.perform(get("/v1/encounters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000001"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void getById_nonExistingId_returns404() throws Exception {
        when(encounterService.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNumber_existingNumber_returns200() throws Exception {
        when(encounterService.findByEncounterNumber("ENC-2026-000001")).thenReturn(Optional.of(encounter));

        mockMvc.perform(get("/v1/encounters/number/ENC-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getByNumber_nonExisting_returns404() throws Exception {
        when(encounterService.findByEncounterNumber("INVALID")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/number/INVALID"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByPatient_returns200() throws Exception {
        when(encounterService.findByPatientId(1L)).thenReturn(Collections.singletonList(encounter));

        mockMvc.perform(get("/v1/encounters/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getByPatientPaged_returns200() throws Exception {
        when(encounterService.findByPatientId(eq(1L), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(encounter)));

        mockMvc.perform(get("/v1/encounters/patient/1/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getByProvider_returns200() throws Exception {
        when(encounterService.findByProviderId(1L)).thenReturn(Collections.singletonList(encounter));

        mockMvc.perform(get("/v1/encounters/provider/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getProviderSchedule_returns200() throws Exception {
        when(encounterService.getProviderSchedule(eq(1L), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(encounter));

        mockMvc.perform(get("/v1/encounters/provider/1/schedule")
                        .param("date", "2026-03-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getByDateRange_returns200() throws Exception {
        when(encounterService.findByDateRange(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(encounter));

        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-000001"));
    }

    @Test
    void getByStatus_returns200() throws Exception {
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED))
                .thenReturn(Collections.singletonList(encounter));

        mockMvc.perform(get("/v1/encounters/status/SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void checkIn_returns200() throws Exception {
        doNothing().when(encounterService).checkIn(1L);

        mockMvc.perform(post("/v1/encounters/1/check-in"))
                .andExpect(status().isOk());

        verify(encounterService).checkIn(1L);
    }

    @Test
    void start_returns200() throws Exception {
        doNothing().when(encounterService).startEncounter(1L);

        mockMvc.perform(post("/v1/encounters/1/start"))
                .andExpect(status().isOk());

        verify(encounterService).startEncounter(1L);
    }

    @Test
    void complete_returns200() throws Exception {
        doNothing().when(encounterService).completeEncounter(eq(1L), any());

        mockMvc.perform(post("/v1/encounters/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"Patient recovered\""))
                .andExpect(status().isOk());

        verify(encounterService).completeEncounter(eq(1L), any());
    }

    @Test
    void cancel_returns200() throws Exception {
        doNothing().when(encounterService).cancelEncounter(1L);

        mockMvc.perform(post("/v1/encounters/1/cancel"))
                .andExpect(status().isOk());

        verify(encounterService).cancelEncounter(1L);
    }

    @Test
    void noShow_returns200() throws Exception {
        doNothing().when(encounterService).markNoShow(1L);

        mockMvc.perform(post("/v1/encounters/1/no-show"))
                .andExpect(status().isOk());

        verify(encounterService).markNoShow(1L);
    }

    @Test
    void update_existingId_returns200() throws Exception {
        when(encounterService.findById(1L)).thenReturn(Optional.of(encounter));
        when(encounterService.update(any(Encounter.class))).thenReturn(encounter);

        mockMvc.perform(put("/v1/encounters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounter)))
                .andExpect(status().isOk());
    }

    @Test
    void update_nonExistingId_returns404() throws Exception {
        when(encounterService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/encounters/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounter)))
                .andExpect(status().isNotFound());
    }
}
