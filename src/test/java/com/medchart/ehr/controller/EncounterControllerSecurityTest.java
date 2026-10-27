package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterResponseDTO;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerSecurityTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String SSN = "123-45-6789";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private EncounterService encounterService;

    @Test
    void everyEncounterRouteRejectsAnonymousCallers() throws Exception {
        MockHttpServletRequestBuilder[] requests = {
            get("/v1/encounters/1"),
            get("/v1/encounters/number/ENC-2026-000101"),
            get("/v1/encounters/patient/1"),
            get("/v1/encounters/patient/1/paged"),
            get("/v1/encounters/provider/1"),
            get("/v1/encounters/provider/1/schedule").param("date", "2026-10-27"),
            get("/v1/encounters/date-range").param("startDate", "2000-01-01").param("endDate", "2099-12-31"),
            get("/v1/encounters/status/SCHEDULED"),
            post("/v1/encounters").contentType(MediaType.APPLICATION_JSON).content("{}"),
            put("/v1/encounters/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
            post("/v1/encounters/1/check-in"),
            post("/v1/encounters/1/start"),
            post("/v1/encounters/1/complete").content("erased"),
            post("/v1/encounters/1/cancel"),
            post("/v1/encounters/1/no-show")
        };
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(encounterService);
    }

    @Test
    void encounterResponseNeverContainsPatientSsnOrDemographics() throws Exception {
        signIn("dr.anderson", User.Role.PROVIDER);
        given(encounterService.findByIdForProvider(eq(1L), any()))
            .willReturn(Optional.of(EncounterResponseDTO.from(encounterWithFullPatient())));

        mockMvc.perform(authed(get("/v1/encounters/1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patient.id").value(5))
            .andExpect(jsonPath("$.patient.mrn").value("MRN0000005"))
            .andExpect(jsonPath("$.patient.displayName").value("Jane Doe"))
            .andExpect(jsonPath("$.attendingProvider.displayName").value("Sarah Anderson"))
            .andExpect(content().string(not(containsString("ssn"))))
            .andExpect(content().string(not(containsString(SSN))))
            .andExpect(content().string(not(containsString("dateOfBirth"))))
            .andExpect(content().string(not(containsString("1980-02-03"))))
            .andExpect(content().string(not(containsString("555-0100"))))
            .andExpect(content().string(not(containsString("Elm Street"))))
            .andExpect(content().string(not(containsString("dr.anderson@mercy.example"))))
            .andExpect(content().string(not(containsString("patientId"))));
    }

    @Test
    void encounterListsUseTheSameMinimalView() throws Exception {
        signIn("frontdesk.wilson", User.Role.STAFF);
        given(encounterService.findByDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27)))
            .willReturn(List.of(EncounterResponseDTO.from(encounterWithFullPatient())));

        mockMvc.perform(authed(get("/v1/encounters/date-range")
                .param("startDate", "2026-10-01").param("endDate", "2026-10-27")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].patient.mrn").value("MRN0000005"))
            .andExpect(content().string(not(containsString(SSN))));
    }

    @Test
    void oversizedDateRangeIsRejected() throws Exception {
        signIn("frontdesk.wilson", User.Role.STAFF);
        given(encounterService.findByDateRange(any(), any()))
            .willThrow(new IllegalArgumentException("Date range must not exceed 31 days"));

        mockMvc.perform(authed(get("/v1/encounters/date-range")
                .param("startDate", "2000-01-01").param("endDate", "2099-12-31")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void statusChangeOnUnknownEncounterIs404() throws Exception {
        signIn("frontdesk.wilson", User.Role.STAFF);
        given(encounterService.cancelEncounter(999L)).willReturn(Optional.empty());

        mockMvc.perform(authed(post("/v1/encounters/999/cancel"))).andExpect(status().isNotFound());
    }

    @Test
    void frontDeskCanCheckInCancelAndNoShowButNotCompleteOrStart() throws Exception {
        signIn("frontdesk.wilson", User.Role.STAFF);
        EncounterResponseDTO found = new EncounterResponseDTO();
        given(encounterService.checkIn(1L)).willReturn(Optional.of(found));
        given(encounterService.cancelEncounter(1L)).willReturn(Optional.of(found));
        given(encounterService.markNoShow(1L)).willReturn(Optional.of(found));

        mockMvc.perform(authed(post("/v1/encounters/1/check-in"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/v1/encounters/1/cancel"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/v1/encounters/1/no-show"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/v1/encounters/1/start"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/v1/encounters/1/complete").content("x"))).andExpect(status().isForbidden());
        verify(encounterService, never()).startEncounter(anyLong());
        verify(encounterService, never()).completeEncounter(anyLong(), any());
    }

    @Test
    void userWithoutClinicalRoleCannotTouchEncounters() throws Exception {
        signIn("new.signup");

        mockMvc.perform(authed(get("/v1/encounters/1"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/v1/encounters/1/cancel"))).andExpect(status().isForbidden());
        verifyNoInteractions(encounterService);
    }

    @Test
    void patientEntitySerialisationOmitsSsn() throws Exception {
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(patient());
        assertFalse(json.contains("ssn"), json);
        assertFalse(json.contains(SSN), json);
    }

    private static Encounter encounterWithFullPatient() {
        Provider provider = Provider.builder()
            .id(3L).firstName("Sarah").lastName("Anderson").credentials("MD")
            .email("dr.anderson@mercy.example").phoneOffice("555-0199").npi("1234567890")
            .build();
        return Encounter.builder()
            .id(1L).encounterNumber("ENC-2026-000101")
            .patient(patient()).attendingProvider(provider)
            .encounterType(EncounterType.OUTPATIENT).status(EncounterStatus.SCHEDULED)
            .encounterDateTime(LocalDateTime.of(2026, 10, 27, 9, 0))
            .chiefComplaint("Follow-up")
            .build();
    }

    private static Patient patient() {
        Address address = new Address();
        address.setStreet1("12 Elm Street");
        return Patient.builder()
            .id(5L).mrn("MRN0000005").ssn(SSN).firstName("Jane").lastName("Doe")
            .dateOfBirth(LocalDate.of(1980, 2, 3)).phoneHome("555-0100").email("jane@example.com")
            .address(address)
            .build();
    }

    private void signIn(String username, User.Role... roles) {
        User user = User.builder()
            .username(username).password("x").email(username + "@medchart.test")
            .firstName("Test").lastName("User")
            .roles(new HashSet<>(Arrays.asList(roles)))
            .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
            .build();
        given(tokenProvider.getUsernameFromToken(TOKEN)).willReturn(username);
        given(tokenProvider.validateToken(TOKEN, user)).willReturn(true);
        given(userDetailsService.loadUserByUsername(username)).willReturn(user);
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TOKEN);
    }
}
