package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerSecurityTest {

    private static final String PATIENT_JSON = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousCallersGet401OnEveryPatientEndpoint() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "").param("size", "2000"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/17")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/mrn/MRN123")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/patients/1").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(patientService);
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        when(tokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad signature"));

        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(patientService);
    }

    @Test
    void validBearerTokenForClinicalUserIsAccepted() throws Exception {
        User nurse = account("nurse.alice", User.Role.STAFF, true);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("nurse.alice");
        when(userDetailsService.loadUserByUsername("nurse.alice")).thenReturn(nurse);
        when(tokenProvider.validateToken(eq("good"), any())).thenReturn(true);
        when(patientService.getPatientById(17L)).thenReturn(new PatientDTO());

        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenForDisabledAccountIsRejected() throws Exception {
        User pending = account("pending", User.Role.STAFF, false);
        when(tokenProvider.getUsernameFromToken("pending-token")).thenReturn("pending");
        when(userDetailsService.loadUserByUsername("pending")).thenReturn(pending);
        when(tokenProvider.validateToken(eq("pending-token"), any())).thenReturn(true);

        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer pending-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(patientService);
    }

    @Test
    void tokenForExpiredAccountOrCredentialsIsRejected() throws Exception {
        User expiredAccount = account("expired", User.Role.PROVIDER, true);
        expiredAccount.setAccountNonExpired(false);
        User expiredPassword = account("stale", User.Role.PROVIDER, true);
        expiredPassword.setCredentialsNonExpired(false);
        when(tokenProvider.getUsernameFromToken("expired-token")).thenReturn("expired");
        when(tokenProvider.getUsernameFromToken("stale-token")).thenReturn("stale");
        when(userDetailsService.loadUserByUsername("expired")).thenReturn(expiredAccount);
        when(userDetailsService.loadUserByUsername("stale")).thenReturn(expiredPassword);
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);

        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer stale-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(patientService);
    }

    @Test
    void authenticatedUserWithoutClinicalRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/patients/17").with(user("someone").roles("AUDITOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(patientService);
    }

    @Test
    void staffCanReadAndRegisterButNotEditDemographics() throws Exception {
        when(patientService.getPatientById(17L)).thenReturn(new PatientDTO());
        when(patientService.createPatient(any())).thenReturn(new PatientDTO());

        mockMvc.perform(get("/v1/patients/17").with(user("frontdesk").roles("STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/patients").with(user("frontdesk").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/v1/patients/17").with(user("frontdesk").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isForbidden());

        verify(patientService, never()).updatePatient(any(), any());
    }

    @Test
    void providerCanEditDemographics() throws Exception {
        when(patientService.updatePatient(eq(17L), any())).thenReturn(new PatientDTO());

        mockMvc.perform(put("/v1/patients/17").with(user("dr.smith").roles("PROVIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void searchRejectsBlankAndSingleCharacterTerms() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "").with(user("dr.smith").roles("PROVIDER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/patients/search").param("q", "  a ").with(user("dr.smith").roles("PROVIDER")))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).searchPatients(anyString(), any());
    }

    @Test
    void searchCapsPageSize() throws Exception {
        Page<PatientDTO> empty = new PageImpl<>(Collections.emptyList());
        when(patientService.searchPatients(anyString(), any())).thenReturn(empty);

        mockMvc.perform(get("/v1/patients/search").param("q", " doe ").param("size", "2000")
                        .with(user("dr.smith").roles("PROVIDER")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).searchPatients(eq("doe"), pageable.capture());
        assertEquals(PatientController.MAX_SEARCH_PAGE_SIZE, pageable.getValue().getPageSize());
    }

    private static User account(String username, User.Role role, boolean enabled) {
        return User.builder()
                .id(42L)
                .username(username)
                .password("x")
                .email(username + "@example.org")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(role))
                .enabled(enabled)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }
}
