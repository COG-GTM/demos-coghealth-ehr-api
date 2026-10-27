package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import com.medchart.ehr.legacy.JobAlreadyRunningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterfaceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class InterfaceControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HrPtoSyncJob hrPtoSyncJob;

    @MockBean
    private HrExportReader hrExportReader;

    @MockBean
    private InterfaceContractCheck interfaceContractCheck;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void exports() {
        when(hrExportReader.resolveExportDate(null)).thenReturn("20261008");
        when(hrExportReader.resolveExportDate("20261008")).thenReturn("20261008");
        when(hrExportReader.resolveExportDate("20990101")).thenReturn("20990101");
        when(hrExportReader.listExportDates()).thenReturn(Arrays.asList("20261007", "20261008"));
        when(hrPtoSyncJob.runForDate("20261008")).thenReturn(Collections.<String, Object>singletonMap("status", "OK"));
    }

    @Test
    void anonymousRunIsUnauthorized() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run")).andExpect(status().isUnauthorized());
        verify(hrPtoSyncJob, never()).runForDate(anyString());
    }

    @Test
    void anonymousReadsAndContractReportAreUnauthorized() throws Exception {
        mvc.perform(get("/interfaces/hr-pto-sync/errors")).andExpect(status().isUnauthorized());
        mvc.perform(get("/interfaces/hr-pto-sync/runs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/interfaces/hr-pto-sync/exports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/interfaces/fhir/contract-report")).andExpect(status().isUnauthorized());
        verifyNoInteractions(interfaceContractCheck);
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("forged")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad"));
        mvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verify(hrPtoSyncJob, never()).runForDate(anyString());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void nonAdminIsForbidden() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run")).andExpect(status().isForbidden());
        mvc.perform(get("/interfaces/hr-pto-sync/errors")).andExpect(status().isForbidden());
        verify(hrPtoSyncJob, never()).runForDate(anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRun() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run")).andExpect(status().isOk());
        verify(hrPtoSyncJob).runForDate("20261008");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownExportDateIsBadRequest() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", "20990101"))
                .andExpect(status().isBadRequest());
        verify(hrPtoSyncJob, never()).runForDate(anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void concurrentRunIsConflict() throws Exception {
        when(hrPtoSyncJob.runForDate("20261008")).thenThrow(new JobAlreadyRunningException("busy"));
        mvc.perform(post("/interfaces/hr-pto-sync/run")).andExpect(status().isConflict());
    }
}
