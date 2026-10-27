package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "medchart.security.jwt.secret=test-secret-key-test-secret-key-test-secret-key-test-secret-key-0123456789",
        "medchart.security.jwt.expiration=60000"
})
class PatientControllerSecurityTest {

    private static final String BODY = "{\"firstName\":\"Bob\",\"lastName\":\"Smith\",\"dateOfBirth\":\"1980-01-01\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void anonymousReadsAreRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/mrn/MRN001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/search").param("q", "a")).andExpect(status().isUnauthorized());
        verifyNoInteractions(patientService);
    }

    @Test
    void anonymousWritesAreRejected() throws Exception {
        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(patientService);
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(patientService);
    }

    @Test
    void validJwtAuthenticatesThroughFilterChain() throws Exception {
        User provider = User.builder().username("dr.jones").password("x").email("j@example.com")
                .firstName("Ann").lastName("Jones").roles(Set.of(User.Role.PROVIDER))
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
        when(userDetailsService.loadUserByUsername("dr.jones")).thenReturn(provider);
        when(patientService.getPatientById(1L)).thenReturn(PatientDTO.builder().id(1L).build());
        String token = tokenProvider.generateTokenFromUsername("dr.jones");

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/patients/1").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserTokenIsRejected() throws Exception {
        User disabled = User.builder().username("former.staff").password("x").email("f@example.com")
                .firstName("Former").lastName("Staff").roles(Set.of(User.Role.STAFF))
                .enabled(false).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
        when(userDetailsService.loadUserByUsername("former.staff")).thenReturn(disabled);
        String token = tokenProvider.generateTokenFromUsername("former.staff");

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(patientService);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanReadButNotWrite() throws Exception {
        when(patientService.getPatientById(1L)).thenReturn(PatientDTO.builder().id(1L).build());
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isOk());

        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanUpdate() throws Exception {
        when(patientService.updatePatient(anyLong(), any())).thenReturn(PatientDTO.builder().id(17L).build());
        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void userWithoutClinicalRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isForbidden());
        verifyNoInteractions(patientService);
    }
}
