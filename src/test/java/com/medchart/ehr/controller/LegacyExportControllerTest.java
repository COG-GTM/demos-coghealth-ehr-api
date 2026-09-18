package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class LegacyExportControllerTest {

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
    void exportEncountersBindsIsoDatesAndStreamsCsv() throws Exception {
        when(encounterExportService.exportEncountersForDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn("EncounterId\n1\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=encounters_export.csv"))
                .andExpect(content().string("EncounterId\n1\n"));
    }

    @Test
    void exportEncountersReturnsEmptyBodyForRangeWithNoEncounters() throws Exception {
        when(encounterExportService.exportEncountersForDateRange(any(), any()))
                .thenReturn(new byte[0]);

        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void exportEncountersRejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "01/01/2024")
                        .param("endDate", "2024-01-31"))
                .andExpect(status().isBadRequest());

        verify(encounterExportService, never()).exportEncountersForDateRange(any(), any());
    }

    @Test
    void exportEncountersRejectsMissingDateRange() throws Exception {
        mockMvc.perform(get("/v1/export/encounters"))
                .andExpect(status().isBadRequest());

        verify(encounterExportService, never()).exportEncountersForDateRange(any(), any());
    }

    @Test
    void exportPatientEncountersStreamsPlainTextForPathVariable() throws Exception {
        when(encounterExportService.exportPatientEncounterHistory(42L))
                .thenReturn("history".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/patient/{patientId}/encounters", 42L))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=patient_encounters.txt"))
                .andExpect(content().string("history"));
    }

    @Test
    void exportPatientEncountersRejectsNonNumericPatientId() throws Exception {
        mockMvc.perform(get("/v1/export/patient/{patientId}/encounters", "abc"))
                .andExpect(status().isBadRequest());

        verify(encounterExportService, never()).exportPatientEncounterHistory(any());
    }

    @Test
    void patientRosterReturnsGeneratedFilePath() throws Exception {
        when(reportGenerator.generatePatientRoster()).thenReturn("/tmp/patient_roster_20240101_000000.csv");

        mockMvc.perform(get("/v1/export/reports/patient-roster"))
                .andExpect(status().isOk())
                .andExpect(content().string("Report generated at: /tmp/patient_roster_20240101_000000.csv"));
    }

    @Test
    void encounterSummaryBindsIsoDateTimes() throws Exception {
        when(reportGenerator.generateEncounterSummary(
                LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2024, 1, 31, 23, 59)))
                .thenReturn("/tmp/encounter_summary.txt");

        mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2024-01-01T00:00:00")
                        .param("endDate", "2024-01-31T23:59:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("Report generated at: /tmp/encounter_summary.txt"));
    }

    @Test
    void encounterSummaryRejectsMalformedDateTime() throws Exception {
        mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-31T23:59:00"))
                .andExpect(status().isBadRequest());

        verify(reportGenerator, never()).generateEncounterSummary(any(), eq(LocalDateTime.of(2024, 1, 31, 23, 59)));
    }

    @Test
    void dailyReportStreamsCsv() throws Exception {
        when(reportGenerator.generateDailyReport()).thenReturn("ID,MRN\n1,MRN001\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=daily_report.csv"))
                .andExpect(content().string("ID,MRN\n1,MRN001\n"));
    }
}
