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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PatientEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousMrnLookupIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/mrn/MRN1700000000000"))
                .andExpect(status().isUnauthorized());

        verify(patientService, never()).getPatientByMrn(anyString());
    }

    @Test
    void anonymousIdLookupIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isUnauthorized());

        verify(patientService, never()).getPatientById(anyLong());
    }

    @Test
    void anonymousSearchIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "smith"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void clinicalUserCanLookUpByMrn() throws Exception {
        when(patientService.getPatientByMrn("MRN0123456789ABC")).thenReturn(new PatientDTO());

        mockMvc.perform(get("/v1/patients/mrn/MRN0123456789ABC"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BILLING")
    void nonClinicalRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/patients/mrn/MRN0123456789ABC"))
                .andExpect(status().isForbidden());

        verify(patientService, never()).getPatientByMrn(anyString());
    }
}
