package com.medchart.ehr.config;

import com.medchart.ehr.controller.LegacyExportController;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LegacyExportController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String TOKEN = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        given(reportGenerator.generateDailyReport()).willReturn("report".getBytes());
        given(encounterExportService.exportPatientEncounterHistory(any())).willReturn("history".getBytes());
    }

    @Test
    void rejectsUnauthenticatedPhiExport() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPhiExportWithUnparseableToken() throws Exception {
        given(tokenProvider.getUsernameFromToken(anyString())).willThrow(new IllegalArgumentException("bad token"));

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsPhiExportForAuthenticatedAdmin() throws Exception {
        authenticateAs("admin.user", "ROLE_ADMIN");

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBulkPhiExportForNonAdminRole() throws Exception {
        authenticateAs("dr.house", "ROLE_PROVIDER");

        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsSinglePatientExportForProvider() throws Exception {
        authenticateAs("dr.house", "ROLE_PROVIDER");

        mockMvc.perform(get("/v1/export/patient/1/encounters").header("Authorization", TOKEN))
                .andExpect(status().isOk());
    }

    private void authenticateAs(String username, String authority) {
        UserDetails userDetails = new User(username, "password", List.of(new SimpleGrantedAuthority(authority)));
        given(tokenProvider.getUsernameFromToken(anyString())).willReturn(username);
        given(tokenProvider.validateToken(anyString(), any())).willReturn(true);
        given(userDetailsService.loadUserByUsername(username)).willReturn(userDetails);
    }
}
