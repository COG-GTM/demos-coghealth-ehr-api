package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PatientControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private static final String PATIENT_JSON = "{\"mrn\":\"MRN001234\",\"firstName\":\"John\",\"lastName\":\"Doe\"}";

    @Test
    void unauthenticatedReadByIdIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedReadByMrnIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/mrn/MRN001234")).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "smith"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCreateIsRejected() throws Exception {
        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUpdateIsRejected() throws Exception {
        mockMvc.perform(put("/v1/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanReadPatient() throws Exception {
        when(patientService.getPatientById(eq(1L))).thenReturn(new PatientDTO());

        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanSearchPatients() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(Collections.emptyList());
        when(patientService.searchPatients(anyString(), any())).thenReturn(page);

        mockMvc.perform(get("/v1/patients/search").param("q", "smith"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotCreatePatients() throws Exception {
        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isForbidden());
    }
}
