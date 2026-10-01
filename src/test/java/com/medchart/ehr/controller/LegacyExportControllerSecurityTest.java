package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ExportRequestException;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyExportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
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
    void anonymousDailyReportIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily"))
            .andExpect(status().isUnauthorized());

        verify(reportGenerator, never()).generateDailyReport();
    }

    @Test
    void anonymousPatientEncounterExportIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/patient/1/encounters"))
            .andExpect(status().isUnauthorized());

        verify(encounterExportService, never()).exportPatientEncounterHistory(any());
    }

    @Test
    void anonymousEncounterRangeExportIsRejected() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                .param("startDate", "0001-01-01")
                .param("endDate", "9999-12-31"))
            .andExpect(status().isUnauthorized());

        verify(encounterExportService, never()).exportEncountersForDateRange(any(), any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonPrivilegedRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster"))
            .andExpect(status().isForbidden());

        verify(reportGenerator, never()).generatePatientRoster();
    }

    @Test
    @WithMockUser(roles = "DATA_EXPORT")
    void privilegedRoleCanExport() throws Exception {
        when(reportGenerator.generateDailyReport()).thenReturn("csv".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/reports/daily"))
            .andExpect(status().isOk())
            .andExpect(content().string("csv"));
    }

    @Test
    @WithMockUser(roles = "DATA_EXPORT")
    void oversizedDateRangeIsRejectedWithBadRequest() throws Exception {
        when(encounterExportService.exportEncountersForDateRange(any(LocalDate.class), any(LocalDate.class)))
            .thenThrow(new ExportRequestException("Date range must not exceed 31 days"));

        mockMvc.perform(get("/v1/export/encounters")
                .param("startDate", "0001-01-01")
                .param("endDate", "9999-12-31"))
            .andExpect(status().isBadRequest());
    }
}
