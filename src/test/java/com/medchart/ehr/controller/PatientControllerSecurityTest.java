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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "medchart.security.jwt.secret=test-only-jwt-signing-key-0123456789abcdef0123456789abcdef01234567",
        "medchart.security.jwt.expiration=60000"
})
class PatientControllerSecurityTest {

    private static final String PATIENT_JSON =
            "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dateOfBirth\":\"1980-01-01\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private PatientService patientService;

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
                        .content(PATIENT_JSON))
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

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotWritePatients() throws Exception {
        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanWritePatients() throws Exception {
        given(patientService.createPatient(any())).willReturn(new PatientDTO());
        given(patientService.updatePatient(anyLong(), any())).willReturn(new PatientDTO());

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/v1/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void signedBearerTokenGrantsRoleBasedAccess() throws Exception {
        given(userDetailsService.loadUserByUsername("provider1")).willReturn(activeUser("PROVIDER"));
        given(patientService.getPatientById(anyLong())).willReturn(new PatientDTO());

        String token = jwtTokenProvider.generateTokenFromUsername("provider1");

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedBearerTokenIsRejected() throws Exception {
        given(userDetailsService.loadUserByUsername(anyString())).willReturn(activeUser("PROVIDER"));

        String token = jwtTokenProvider.generateTokenFromUsername("provider1");

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotUseValidToken() throws Exception {
        given(userDetailsService.loadUserByUsername("revoked")).willReturn(
                User.withUsername("revoked")
                        .password("x")
                        .authorities(new SimpleGrantedAuthority("ROLE_PROVIDER"))
                        .disabled(true)
                        .build());

        String token = jwtTokenProvider.generateTokenFromUsername("revoked");

        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private UserDetails activeUser(String role) {
        return User.withUsername("provider1")
                .password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .build();
    }
}
