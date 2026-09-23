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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
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

    @Test
    void getPatientRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPatientByMrnRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/patients/mrn/MRN001234"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchPatientsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "smith"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPatientRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mrn\":\"MRN001234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void unprivilegedRoleCannotReadPatients() throws Exception {
        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void authenticatedProviderCanReadPatients() throws Exception {
        given(patientService.getPatientById(anyLong())).willReturn(new PatientDTO());
        Page<PatientDTO> page = new PageImpl<>(List.of(new PatientDTO()));
        given(patientService.searchPatients(anyString(), any())).willReturn(page);

        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/patients/search").param("q", "smith")).andExpect(status().isOk());
    }
}
