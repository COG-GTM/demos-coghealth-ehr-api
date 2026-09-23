package com.medchart.ehr.config;

import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.controller.PatientController;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PatientController.class, LegacyExportController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ApiAuthorizationTest {

    private static final String PATIENT_URL = "/v1/patients/1";
    private static final String EXPORT_URL = "/v1/export/reports/patient-roster";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousPatientReadIsRejected() throws Exception {
        mockMvc.perform(get(PATIENT_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPhiExportIsRejected() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanReadPatient() throws Exception {
        given(patientService.getPatientById(anyLong())).willReturn(new PatientDTO());

        mockMvc.perform(get(PATIENT_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCannotRunPhiExport() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRunPhiExport() throws Exception {
        given(reportGenerator.generatePatientRoster()).willReturn("/tmp/roster.csv");

        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isOk());
    }

    @Test
    void bearerTokenIsAuthenticatedByRegisteredJwtFilter() throws Exception {
        UserDetails provider = new User("dr.house", "n/a",
                AuthorityUtils.createAuthorityList("ROLE_PROVIDER"));
        given(tokenProvider.getUsernameFromToken(anyString())).willReturn("dr.house");
        given(userDetailsService.loadUserByUsername("dr.house")).willReturn(provider);
        given(tokenProvider.validateToken(anyString(), any(UserDetails.class))).willReturn(true);
        given(patientService.getPatientById(eq(1L))).willReturn(new PatientDTO());

        mockMvc.perform(get(PATIENT_URL).header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBearerTokenStaysAnonymous() throws Exception {
        UserDetails provider = new User("dr.house", "n/a",
                AuthorityUtils.createAuthorityList("ROLE_PROVIDER"));
        given(tokenProvider.getUsernameFromToken(anyString())).willReturn("dr.house");
        given(userDetailsService.loadUserByUsername("dr.house")).willReturn(provider);
        given(tokenProvider.validateToken(anyString(), any(UserDetails.class))).willReturn(false);

        mockMvc.perform(get(PATIENT_URL).header("Authorization", "Bearer tampered.jwt.token"))
                .andExpect(status().isUnauthorized());
    }
}
