package com.medchart.ehr.controller;

import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyExportControllerTest {

    private ReportGenerator reportGenerator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportGenerator = mock(ReportGenerator.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LegacyExportController(mock(EncounterExportService.class), reportGenerator))
                .build();
    }

    @Test
    void patientRosterIsReturnedAsAttachmentWithoutServerPath() throws Exception {
        byte[] csv = "ID,MRN\n1,MRN0001\n".getBytes(StandardCharsets.UTF_8);
        when(reportGenerator.generatePatientRoster()).thenReturn(csv);

        MvcResult result = mockMvc.perform(get("/v1/export/reports/patient-roster"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=patient_roster.csv"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(csv);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Report generated at");
    }

    @Test
    void encounterSummaryIsReturnedAsAttachmentWithoutServerPath() throws Exception {
        byte[] text = "ENCOUNTER SUMMARY REPORT\n".getBytes(StandardCharsets.UTF_8);
        when(reportGenerator.generateEncounterSummary(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 2, 0, 0))).thenReturn(text);

        MvcResult result = mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2026-10-01T00:00:00")
                        .param("endDate", "2026-10-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=encounter_summary.txt"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(text);
    }

    @Test
    void dailyReportIsNotCached() throws Exception {
        when(reportGenerator.generateDailyReport()).thenReturn(new byte[0]);

        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
