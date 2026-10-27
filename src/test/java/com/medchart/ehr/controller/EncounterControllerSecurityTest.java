package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerSecurityTest {

    private static final String DATE_RANGE = "/v1/encounters/date-range?startDate=1900-01-01&endDate=2100-01-01";
    private static final String SSN = "123-45-6789";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousDateRangeIsRejected() throws Exception {
        mockMvc.perform(get(DATE_RANGE)).andExpect(status().isUnauthorized());

        verify(encounterService, never()).findByDateRange(any(), any());
    }

    @Test
    void anonymousStatusChangesAreRejected() throws Exception {
        for (String action : List.of("check-in", "start", "complete", "cancel", "no-show")) {
            mockMvc.perform(post("/v1/encounters/42/" + action)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(encounterService, never()).cancelEncounter(anyLong());
        verify(encounterService, never()).completeEncounter(anyLong(), any());
        verify(encounterService, never()).create(any());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        when(tokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad signature"));

        mockMvc.perform(get(DATE_RANGE).header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountTokenIsRejected() throws Exception {
        UserDetails disabled = User.withUsername("dr.gone").password("x").roles("PROVIDER").disabled(true).build();
        when(tokenProvider.getUsernameFromToken("t")).thenReturn("dr.gone");
        when(userDetailsService.loadUserByUsername("dr.gone")).thenReturn(disabled);
        when(tokenProvider.validateToken(eq("t"), any())).thenReturn(true);

        mockMvc.perform(get(DATE_RANGE).header("Authorization", "Bearer t"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerTokenReturnsEncountersWithoutPatientIdentityData() throws Exception {
        UserDetails provider = User.withUsername("dr.smith").password("x").roles("PROVIDER").build();
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("dr.smith");
        when(userDetailsService.loadUserByUsername("dr.smith")).thenReturn(provider);
        when(tokenProvider.validateToken(eq("good"), any())).thenReturn(true);
        when(encounterService.findByDateRange(any(), any())).thenReturn(List.of(encounter()));

        mockMvc.perform(get(DATE_RANGE).header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].encounterNumber").value("ENC-2026-0042"))
                .andExpect(jsonPath("$[0].patient.id").value(7))
                .andExpect(jsonPath("$[0].patient.mrn").value("MRN0000007"))
                .andExpect(jsonPath("$[0].patient.lastName").value("Patient"))
                .andExpect(jsonPath("$[0].patient.ssn").doesNotExist())
                .andExpect(jsonPath("$[0].patient.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$[0].patient.address").doesNotExist())
                .andExpect(jsonPath("$[0].patient.email").doesNotExist())
                .andExpect(content().string(not(containsString(SSN))))
                .andExpect(content().string(not(containsString("1 Main St"))));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void singleEncounterResponseOmitsSsn() throws Exception {
        when(encounterService.findByIdWithDetails(42L)).thenReturn(Optional.of(encounter()));

        mockMvc.perform(get("/v1/encounters/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.patient.ssn").doesNotExist())
                .andExpect(content().string(not(containsString(SSN))))
                .andExpect(content().string(not(containsString("1980-02-03"))));
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutClinicalRoleIsForbidden() throws Exception {
        mockMvc.perform(get(DATE_RANGE)).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/encounters/42/cancel")).andExpect(status().isForbidden());

        verify(encounterService, never()).findByDateRange(any(), any());
        verify(encounterService, never()).cancelEncounter(anyLong());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanScheduleButNotChangeClinicalContent() throws Exception {
        mockMvc.perform(post("/v1/encounters/42/cancel")).andExpect(status().isOk());
        verify(encounterService).cancelEncounter(42L);

        mockMvc.perform(post("/v1/encounters/42/complete").content("notes")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/encounters/42/start")).andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(encounterService, never()).completeEncounter(anyLong(), any());
        verify(encounterService, never()).startEncounter(anyLong());
        verify(encounterService, never()).update(any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanCompleteEncounter() throws Exception {
        mockMvc.perform(post("/v1/encounters/42/complete").content("seen")).andExpect(status().isOk());

        verify(encounterService).completeEncounter(eq(42L), anyString());
    }

    private static Encounter encounter() {
        Patient patient = Patient.builder()
                .id(7L)
                .mrn("MRN0000007")
                .ssn(SSN)
                .firstName("Alice")
                .lastName("Patient")
                .dateOfBirth(LocalDate.of(1980, 2, 3))
                .email("alice@example.com")
                .address(Address.builder().street1("1 Main St").city("Springfield").build())
                .build();
        return Encounter.builder()
                .id(42L)
                .encounterNumber("ENC-2026-0042")
                .patient(patient)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 10, 28, 9, 0))
                .build();
    }
}
