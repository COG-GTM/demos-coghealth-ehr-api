package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EncounterController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class EncounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EncounterService encounterService;

    private static Encounter encounter(Long id, String number, EncounterStatus status) {
        return Encounter.builder()
                .id(id)
                .encounterNumber(number)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(status)
                .encounterDateTime(LocalDateTime.of(2024, 3, 1, 9, 30))
                .build();
    }

    @Test
    void getByIdReturnsEncounter() throws Exception {
        when(encounterService.findByIdWithDetails(7L))
                .thenReturn(Optional.of(encounter(7L, "ENC-2024-000007", EncounterStatus.SCHEDULED)));

        mockMvc.perform(get("/v1/encounters/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2024-000007"));
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        when(encounterService.findByIdWithDetails(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/7")).andExpect(status().isNotFound());
    }

    @Test
    void getByNumberReturnsEncounter() throws Exception {
        when(encounterService.findByEncounterNumber("ENC-2024-000007"))
                .thenReturn(Optional.of(encounter(7L, "ENC-2024-000007", EncounterStatus.SCHEDULED)));

        mockMvc.perform(get("/v1/encounters/number/ENC-2024-000007"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void getByNumberReturnsNotFoundWhenMissing() throws Exception {
        when(encounterService.findByEncounterNumber("ENC-2024-999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/number/ENC-2024-999999")).andExpect(status().isNotFound());
    }

    @Test
    void getByPatientReturnsList() throws Exception {
        when(encounterService.findByPatientId(42L))
                .thenReturn(List.of(encounter(1L, "ENC-2024-000001", EncounterStatus.COMPLETED)));

        mockMvc.perform(get("/v1/encounters/patient/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2024-000001"));
    }

    @Test
    void getByPatientPagedPassesPageableThrough() throws Exception {
        Page<Encounter> page = new PageImpl<>(
                List.of(encounter(1L, "ENC-2024-000001", EncounterStatus.COMPLETED)),
                PageRequest.of(2, 5),
                11);
        when(encounterService.findByPatientId(eq(42L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/encounters/patient/42/paged").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(encounterService).findByPatientId(eq(42L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getByProviderReturnsList() throws Exception {
        when(encounterService.findByProviderId(9L))
                .thenReturn(List.of(encounter(1L, "ENC-2024-000001", EncounterStatus.SCHEDULED)));

        mockMvc.perform(get("/v1/encounters/provider/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(encounterService).findByProviderId(9L);
    }

    @Test
    void getProviderScheduleParsesDate() throws Exception {
        when(encounterService.getProviderSchedule(9L, LocalDate.of(2024, 3, 1)))
                .thenReturn(List.of(encounter(1L, "ENC-2024-000001", EncounterStatus.SCHEDULED)));

        mockMvc.perform(get("/v1/encounters/provider/9/schedule").param("date", "2024-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(encounterService).getProviderSchedule(9L, LocalDate.of(2024, 3, 1));
    }

    @Test
    void getByDateRangePassesBothDates() throws Exception {
        when(encounterService.findByDateRange(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", "2024-03-01")
                        .param("endDate", "2024-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(encounterService).findByDateRange(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));
    }

    @Test
    void getByDateRangeRequiresBothDates() throws Exception {
        mockMvc.perform(get("/v1/encounters/date-range").param("startDate", "2024-03-01"))
                .andExpect(status().isBadRequest());

        verify(encounterService, never()).findByDateRange(any(), any());
    }

    @Test
    void getByStatusBindsEnum() throws Exception {
        when(encounterService.findByStatus(EncounterStatus.NO_SHOW))
                .thenReturn(List.of(encounter(1L, "ENC-2024-000001", EncounterStatus.NO_SHOW)));

        mockMvc.perform(get("/v1/encounters/status/NO_SHOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NO_SHOW"));

        verify(encounterService).findByStatus(EncounterStatus.NO_SHOW);
    }

    @Test
    void createReturnsSavedEncounter() throws Exception {
        Encounter request = encounter(null, null, null);
        when(encounterService.create(any(Encounter.class)))
                .thenReturn(encounter(5L, "ENC-2024-000005", EncounterStatus.SCHEDULED));

        mockMvc.perform(post("/v1/encounters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void updateSetsPathIdOnBodyWhenEncounterExists() throws Exception {
        Encounter body = encounter(99L, "ENC-2024-000003", EncounterStatus.CHECKED_IN);
        when(encounterService.findById(3L))
                .thenReturn(Optional.of(encounter(3L, "ENC-2024-000003", EncounterStatus.SCHEDULED)));
        when(encounterService.update(any(Encounter.class)))
                .thenReturn(encounter(3L, "ENC-2024-000003", EncounterStatus.CHECKED_IN));

        mockMvc.perform(put("/v1/encounters/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));

        ArgumentCaptor<Encounter> updated = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterService).update(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(3L);
    }

    @Test
    void updateReturnsNotFoundWhenEncounterMissing() throws Exception {
        when(encounterService.findById(3L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/encounters/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encounter(3L, "ENC-2024-000003", EncounterStatus.SCHEDULED))))
                .andExpect(status().isNotFound());

        verify(encounterService, never()).update(any(Encounter.class));
    }

    @Test
    void checkInInvokesServiceWithPathId() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/check-in")).andExpect(status().isOk());

        verify(encounterService).checkIn(11L);
    }

    @Test
    void startInvokesServiceWithPathId() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/start")).andExpect(status().isOk());

        verify(encounterService).startEncounter(11L);
    }

    @Test
    void completePassesNotesThrough() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/complete")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Patient stable, discharged"))
                .andExpect(status().isOk());

        verify(encounterService).completeEncounter(11L, "Patient stable, discharged");
    }

    @Test
    void completeWithoutNotesPassesNull() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/complete")).andExpect(status().isOk());

        verify(encounterService).completeEncounter(11L, null);
    }

    @Test
    void cancelInvokesServiceWithPathId() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/cancel")).andExpect(status().isOk());

        verify(encounterService).cancelEncounter(11L);
    }

    @Test
    void noShowInvokesServiceWithPathId() throws Exception {
        mockMvc.perform(post("/v1/encounters/11/no-show")).andExpect(status().isOk());

        verify(encounterService).markNoShow(11L);
    }
}
