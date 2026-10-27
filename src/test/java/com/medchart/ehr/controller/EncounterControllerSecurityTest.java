package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.DiagnosisRole;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousReadIsRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", "2000-01-01").param("endDate", "2099-12-31"))
                .andExpect(status().isUnauthorized());
        verify(encounterService, never()).findByIdForProvider(anyLong(), any());
        verify(encounterService, never()).findByDateRange(any(), any());
    }

    @Test
    void anonymousStatusChangesAreRejected() throws Exception {
        for (String action : List.of("check-in", "start", "complete", "cancel", "no-show")) {
            mockMvc.perform(post("/v1/encounters/5/" + action)).andExpect(status().isUnauthorized());
        }
        verify(encounterService, never()).checkIn(anyLong());
        verify(encounterService, never()).startEncounter(anyLong());
        verify(encounterService, never()).completeEncounter(anyLong(), any());
        verify(encounterService, never()).cancelEncounter(anyLong());
        verify(encounterService, never()).markNoShow(anyLong());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("bogus")).thenThrow(new IllegalArgumentException("bad token"));
        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer bogus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void authenticatedReadReturnsDtoWithoutPatientPhi() throws Exception {
        when(encounterService.findByIdForProvider(1L, null)).thenReturn(Optional.of(sampleEncounter()));

        String body = mockMvc.perform(get("/v1/encounters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000101"))
                .andExpect(jsonPath("$.patient.id").value(10))
                .andExpect(jsonPath("$.patient.mrn").value("MRN0001"))
                .andExpect(jsonPath("$.diagnoses[0].icd10Code").value("E11.9"))
                .andExpect(jsonPath("$.diagnoses[0].primaryDiagnosis").value(true))
                .andExpect(jsonPath("$.attendingProvider.lastName").value("House"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("ssn")
                .doesNotContain("123-45-6789")
                .doesNotContain("dateOfBirth")
                .doesNotContain("phoneMobile")
                .doesNotContain("email");
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void clinicalRoleCanChangeStatus() throws Exception {
        mockMvc.perform(post("/v1/encounters/5/cancel")).andExpect(status().isOk());
        verify(encounterService).cancelEncounter(5L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonClinicalRoleCannotChangeStatus() throws Exception {
        mockMvc.perform(post("/v1/encounters/5/cancel")).andExpect(status().isForbidden());
        verify(encounterService, never()).cancelEncounter(anyLong());
    }

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientEntityNeverSerializesSsn() throws Exception {
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(samplePatient());
        assertThat(json).doesNotContain("ssn").doesNotContain("123-45-6789");
    }

    private static Patient samplePatient() {
        return Patient.builder()
                .id(10L)
                .mrn("MRN0001")
                .ssn("123-45-6789")
                .firstName("Jane")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .email("jane@example.com")
                .phoneMobile("555-0100")
                .build();
    }

    private static Encounter sampleEncounter() {
        Provider provider = Provider.builder().id(20L).npi("1234567890").firstName("Greg").lastName("House").build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(samplePatient())
                .attendingProvider(provider)
                .encounterType(EncounterType.values()[0])
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 10, 27, 9, 0))
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id(30L)
                .patient(samplePatient())
                .icd10Code("E11.9")
                .description("Type 2 diabetes mellitus without complications")
                .build();
        encounter.addDiagnosis(EncounterDiagnosis.builder()
                .id(40L)
                .diagnosis(diagnosis)
                .role(DiagnosisRole.ADMISSION)
                .primaryDiagnosis(true)
                .build());
        return encounter;
    }
}
