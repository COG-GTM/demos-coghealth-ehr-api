package com.medchart.ehr.config;

import com.medchart.ehr.controller.EncounterController;
import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {EncounterController.class, LegacyExportController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @WithAnonymousUser
    void anonymousEncounterRequestIsRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void anonymousPatientRosterRequestIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        given(tokenProvider.getUsernameFromToken(anyString())).willThrow(new RuntimeException("bad token"));

        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerTokenIsAccepted() throws Exception {
        User user = new User("nurse", "n/a", java.util.List.of());
        given(tokenProvider.getUsernameFromToken("good")).willReturn("nurse");
        given(tokenProvider.validateToken("good", user)).willReturn(true);
        given(userDetailsService.loadUserByUsername("nurse")).willReturn(user);
        given(encounterService.findByIdWithDetails(anyLong())).willReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer good"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminCannotRunExports() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRunExports() throws Exception {
        given(reportGenerator.generatePatientRoster()).willReturn("/tmp/roster.csv");

        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isOk());
    }
}
