package com.medchart.ehr.controller;

import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LegacyExportControllerTest {

    @Mock
    private EncounterExportService encounterExportService;

    @Mock
    private ReportGenerator reportGenerator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LegacyExportController(encounterExportService, reportGenerator))
                .build();
    }

    @Test
    void patientRosterReturnsReportContentNotServerPath() throws Exception {
        when(reportGenerator.generatePatientRoster())
                .thenReturn("ID,MRN,SSN\n1,MRN001,XXX-XX-6789\n".getBytes(StandardCharsets.UTF_8));

        String body = mockMvc.perform(get("/v1/export/reports/patient-roster"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=patient_roster.csv"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).startsWith("ID,MRN,SSN");
        assertThat(body).doesNotContain(System.getProperty("java.io.tmpdir"));
        assertThat(body).doesNotContain("Report generated at");
    }

    @Test
    void encounterSummaryReturnsReportContentNotServerPath() throws Exception {
        when(reportGenerator.generateEncounterSummary(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn("ENCOUNTER SUMMARY REPORT\n".getBytes(StandardCharsets.UTF_8));

        String body = mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-01-03T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=encounter_summary.txt"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).startsWith("ENCOUNTER SUMMARY REPORT");
        assertThat(body).doesNotContain(System.getProperty("java.io.tmpdir"));
        assertThat(body).doesNotContain("Report generated at");
    }
}
