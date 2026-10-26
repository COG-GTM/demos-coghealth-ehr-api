package com.medchart.ehr.config;

import com.medchart.ehr.controller.AuthController;
import com.medchart.ehr.controller.EncounterController;
import com.medchart.ehr.controller.InterfaceController;
import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.controller.PatientController;
import com.medchart.ehr.controller.ProviderController;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import com.medchart.ehr.legacy.ReportGenerator;
import com.medchart.ehr.repository.UserRepository;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.PatientService;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
    PatientController.class,
    EncounterController.class,
    ProviderController.class,
    LegacyExportController.class,
    InterfaceController.class,
    AuthController.class
})
@Import(SecurityConfig.class)
class ApiAuthorizationTest {

    private static final String TOKEN = "header.payload.signature";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PatientService patientService;
    @MockBean
    private EncounterService encounterService;
    @MockBean
    private ProviderService providerService;
    @MockBean
    private EncounterExportService encounterExportService;
    @MockBean
    private ReportGenerator reportGenerator;
    @MockBean
    private HrPtoSyncJob hrPtoSyncJob;
    @MockBean
    private HrExportReader hrExportReader;
    @MockBean
    private InterfaceContractCheck interfaceContractCheck;

    @Test
    @WithAnonymousUser
    void anonymousCallerGets401OnEncounter() throws Exception {
        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterService);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerGets401OnPatientRoster() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportGenerator);
    }

    @Test
    void requestsWithoutTokenGet401AcrossTheApi() throws Exception {
        RequestBuilder[] requests = {
            get("/v1/patients/1"),
            get("/v1/patients/search").param("q", "a"),
            put("/v1/patients/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
            get("/v1/export/patient/1/encounters"),
            get("/v1/export/encounters").param("startDate", "2026-01-01").param("endDate", "2026-01-31"),
            put("/v1/encounters/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
            post("/v1/encounters/1/cancel"),
            delete("/v1/providers/1"),
            post("/interfaces/hr-pto-sync/run"),
            get("/interfaces/hr-pto-sync/errors")
        };
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(patientService, encounterService, providerService,
            encounterExportService, reportGenerator, hrPtoSyncJob);
    }

    @Test
    void invalidTokenGets401() throws Exception {
        given(tokenProvider.getUsernameFromToken(anyString())).willThrow(new IllegalArgumentException("bad signature"));

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(patientService);
    }

    @Test
    void expiredOrMismatchedTokenGets401() throws Exception {
        signIn("nurse.johnson", User.Role.STAFF);
        given(tokenProvider.validateToken(anyString(), any())).willReturn(false);

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clinicianWithValidTokenCanReadCharts() throws Exception {
        signIn("dr.anderson", User.Role.PROVIDER);
        given(patientService.getPatientById(1L)).willReturn(new PatientDTO());
        given(encounterService.findByIdWithDetails(1L)).willReturn(Optional.of(new Encounter()));

        mockMvc.perform(authed(get("/v1/patients/1"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/v1/encounters/1"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/v1/encounters/1/complete"))).andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotRunExportsHrSyncOrProviderAdmin() throws Exception {
        signIn("dr.anderson", User.Role.PROVIDER, User.Role.STAFF);

        mockMvc.perform(authed(get("/v1/export/reports/patient-roster"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/v1/export/patient/1/encounters"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(post("/interfaces/hr-pto-sync/run"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(delete("/v1/providers/1"))).andExpect(status().isForbidden());
        verifyNoInteractions(reportGenerator, encounterExportService, hrPtoSyncJob);
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    void staffCannotCompleteEncounterWithClinicalNotes() throws Exception {
        signIn("frontdesk.wilson", User.Role.STAFF);

        mockMvc.perform(authed(post("/v1/encounters/1/complete").content("note")))
            .andExpect(status().isForbidden());
        verify(encounterService, never()).completeEncounter(anyLong(), any());
    }

    @Test
    void adminCanRunExportsAndHrSync() throws Exception {
        signIn("admin", User.Role.ADMIN);
        given(reportGenerator.generatePatientRoster()).willReturn("/tmp/roster.csv");
        given(hrExportReader.resolveExportDate(any())).willReturn("20261008");
        given(hrPtoSyncJob.runForDate("20261008")).willReturn(Map.of("status", "ok"));

        mockMvc.perform(authed(get("/v1/export/reports/patient-roster"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/interfaces/hr-pto-sync/run"))).andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithoutRoleIsForbidden() throws Exception {
        signIn("new.signup");

        mockMvc.perform(authed(get("/v1/patients/1"))).andExpect(status().isForbidden());
        verifyNoInteractions(patientService);
    }

    @Test
    void registrationIsPublicButGrantsNoRole() throws Exception {
        given(userRepository.existsByUsername("mallory")).willReturn(false);
        given(userRepository.existsByEmail("mallory@example.com")).willReturn(false);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mallory\",\"email\":\"mallory@example.com\",\"password\":\"pw\","
                    + "\"firstName\":\"M\",\"lastName\":\"X\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().getRoles().isEmpty());
    }

    private void signIn(String username, User.Role... roles) {
        User user = User.builder()
            .username(username)
            .password("x")
            .email(username + "@medchart.test")
            .firstName("Test")
            .lastName("User")
            .roles(new HashSet<>(Arrays.asList(roles)))
            .enabled(true)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();
        given(tokenProvider.getUsernameFromToken(TOKEN)).willReturn(username);
        given(tokenProvider.validateToken(TOKEN, user)).willReturn(true);
        given(userDetailsService.loadUserByUsername(username)).willReturn(user);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TOKEN);
    }
}
