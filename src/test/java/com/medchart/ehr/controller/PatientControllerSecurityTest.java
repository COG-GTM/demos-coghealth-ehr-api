package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.MethodSecurityConfig;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class PatientControllerSecurityTest {

    private static final String PATIENT_JSON = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private static RequestBuilder[] allRoutes() {
        return new RequestBuilder[] {
            get("/v1/patients/17"),
            get("/v1/patients/mrn/MRN123"),
            get("/v1/patients/search").param("q", "").param("size", "2000"),
            post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON),
            put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON),
        };
    }

    @Test
    void anonymousCallerIsDeniedOnEveryRoute() throws Exception {
        for (RequestBuilder request : allRoutes()) {
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(status).isIn(401, 403);
        }
        verifyNoInteractions(patientService);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void nonClinicalRoleIsDeniedOnEveryRoute() throws Exception {
        for (RequestBuilder request : allRoutes()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
        verifyNoInteractions(patientService);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanReadButNotWrite() throws Exception {
        when(patientService.getPatientById(17L)).thenReturn(new PatientDTO());

        mockMvc.perform(get("/v1/patients/17")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanWrite() throws Exception {
        when(patientService.createPatient(any())).thenReturn(new PatientDTO());
        when(patientService.updatePatient(anyLong(), any())).thenReturn(new PatientDTO());

        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void invalidSearchTermReturnsBadRequest() throws Exception {
        when(patientService.searchPatients(eq(""), any()))
                .thenThrow(new IllegalArgumentException("Search term must be between 2 and 100 characters"));

        mockMvc.perform(get("/v1/patients/search").param("q", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void searchPassesRequestToService() throws Exception {
        when(patientService.searchPatients(eq("doe"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/v1/patients/search").param("q", "doe")).andExpect(status().isOk());
        verify(patientService).searchPatients(eq("doe"), any());
    }
}
