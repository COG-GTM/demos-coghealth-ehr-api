package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.MethodSecurityConfig;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The application's filter chain is permit-all, so these tests assert that the
 * export endpoints deny access on their own.
 */
@WebMvcTest(LegacyExportController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class LegacyExportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterExportService encounterExportService;

    @MockBean
    private ReportGenerator reportGenerator;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @WithAnonymousUser
    void anonymousDailyReportIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily").param("page", "0").param("size", "50"))
                .andExpect(status().isForbidden());

        verify(reportGenerator, never()).generateDailyReport(anyInt(), anyInt());
    }

    @Test
    @WithAnonymousUser
    void anonymousPatientEncounterExportIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/patient/1/encounters").param("page", "0").param("size", "50"))
                .andExpect(status().isForbidden());

        verify(encounterExportService, never()).exportPatientEncounterHistory(any(), anyInt(), anyInt());
    }

    @Test
    @WithAnonymousUser
    void anonymousEncounterRangeExportIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-10")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousPatientRosterIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster").param("page", "0").param("size", "50"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousEncounterSummaryIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2024-01-01T00:00:00")
                        .param("endDate", "2024-01-10T00:00:00")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"PROVIDER", "ADMIN"})
    void nonPrivilegedRoleIsDenied() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily").param("page", "0").param("size", "50"))
                .andExpect(status().isForbidden());

        verify(reportGenerator, never()).generateDailyReport(anyInt(), anyInt());
    }

    @Test
    @WithMockUser(roles = "PHI_EXPORT")
    void privilegedRoleCanExport() throws Exception {
        when(reportGenerator.generateDailyReport(0, 50)).thenReturn("ID,MRN\n1,MRN001\n".getBytes());

        mockMvc.perform(get("/v1/export/reports/daily").param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().string("ID,MRN\n1,MRN001\n"));
    }

    @Test
    @WithMockUser(roles = "PHI_EXPORT")
    void paginationIsMandatory() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PHI_EXPORT")
    void oversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily").param("page", "0").param("size", "100000"))
                .andExpect(status().isBadRequest());

        verify(reportGenerator, never()).generateDailyReport(anyInt(), anyInt());
    }

    @Test
    @WithMockUser(roles = "PHI_EXPORT")
    void unboundedDateRangeIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "0001-01-01")
                        .param("endDate", "9999-12-31")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isBadRequest());

        verify(encounterExportService, never())
                .exportEncountersForDateRange(any(), any(), anyInt(), eq(50));
    }
}
