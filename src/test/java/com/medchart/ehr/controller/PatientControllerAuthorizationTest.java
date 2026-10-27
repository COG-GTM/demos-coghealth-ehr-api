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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerAuthorizationTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String PATIENT_JSON = "{\"firstName\":\"Bob\",\"lastName\":\"Smith\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private PatientService patientService;

    @Test
    void anonymousCallerGets401OnEveryPatientRoute() throws Exception {
        MockHttpServletRequestBuilder[] requests = {
            get("/v1/patients/1"),
            get("/v1/patients/mrn/MRN001"),
            get("/v1/patients/search").param("q", "a").param("size", "2000"),
            post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON),
            put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON)
        };
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(patientService);
    }

    @Test
    void providerCanReadButNotCreateOrUpdatePatients() throws Exception {
        signIn("dr.anderson", User.Role.PROVIDER);
        given(patientService.getPatientById(1L)).willReturn(new PatientDTO());
        given(patientService.getPatientByMrn("MRN001")).willReturn(new PatientDTO());
        given(patientService.searchPatients(anyString(), any())).willReturn(Page.empty());

        mockMvc.perform(authed(get("/v1/patients/1"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/v1/patients/mrn/MRN001"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/v1/patients/search").param("q", "smi"))).andExpect(status().isOk());

        mockMvc.perform(authed(post("/v1/patients")).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
            .andExpect(status().isForbidden());
        mockMvc.perform(authed(put("/v1/patients/17")).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
            .andExpect(status().isForbidden());
        verify(patientService, never()).createPatient(any());
        verify(patientService, never()).updatePatient(anyLong(), any());
    }

    @Test
    void registrationStaffAndAdminCanCreateAndUpdatePatients() throws Exception {
        given(patientService.createPatient(any())).willReturn(new PatientDTO());
        given(patientService.updatePatient(eq(17L), any())).willReturn(new PatientDTO());

        signIn("frontdesk.wilson", User.Role.STAFF);
        mockMvc.perform(authed(post("/v1/patients")).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
            .andExpect(status().isCreated());
        mockMvc.perform(authed(put("/v1/patients/17")).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
            .andExpect(status().isOk());

        signIn("admin", User.Role.ADMIN);
        mockMvc.perform(authed(put("/v1/patients/17")).contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void userWithoutRoleCannotReadPatients() throws Exception {
        signIn("new.signup");

        mockMvc.perform(authed(get("/v1/patients/search").param("q", "a"))).andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/v1/patients/1"))).andExpect(status().isForbidden());
        verifyNoInteractions(patientService);
    }

    @Test
    void searchPageSizeIsCappedAt100() throws Exception {
        signIn("dr.anderson", User.Role.PROVIDER);
        given(patientService.searchPatients(anyString(), any())).willReturn(Page.empty());

        mockMvc.perform(authed(get("/v1/patients/search").param("q", "a").param("size", "2000")))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).searchPatients(eq("a"), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
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

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TOKEN);
    }
}
