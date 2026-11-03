package com.medchart.ehr.controller;

import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterfaceControllerTest {

    private HrPtoSyncJob job;
    private InterfaceContractCheck contractCheck;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        job = mock(HrPtoSyncJob.class);
        contractCheck = mock(InterfaceContractCheck.class);
        mvc = MockMvcBuilders.standaloneSetup(new InterfaceController(job, new HrExportReader(), contractCheck)).build();
    }

    @Test
    void runRejectsCraftedExportDateWith400() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run")
                        .param("exportDate", "2026,x:y/../workday_pto_export_20261008"))
                .andExpect(status().isBadRequest());
        verify(job, never()).runForDate(anyString());
    }

    @Test
    void contractReportRejectsCraftedExportDateWith400() throws Exception {
        mvc.perform(get("/interfaces/fhir/contract-report")
                        .param("exportDate", "20261008/../../secret"))
                .andExpect(status().isBadRequest());
        verify(contractCheck, never()).run(anyString());
    }

    @Test
    void runAcceptsValidExportDate() throws Exception {
        when(job.runForDate("20261008")).thenReturn(Collections.<String, Object>singletonMap("exportDate", "20261008"));
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", "20261008"))
                .andExpect(status().isOk());
        verify(job).runForDate("20261008");
    }
}
