package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import com.medchart.ehr.legacy.OnDemandRunThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InterfaceController.class)
@Import(SecurityConfig.class)
class InterfaceControllerSecurityTest {

    private static final String DATE = "20261008";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HrPtoSyncJob hrPtoSyncJob;
    @MockBean
    private HrExportReader hrExportReader;
    @MockBean
    private InterfaceContractCheck interfaceContractCheck;
    @MockBean
    private OnDemandRunThrottle throttle;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(hrExportReader.resolveExportDate(any())).thenReturn(DATE);
        when(throttle.tryAcquire(anyString())).thenReturn(0L);
        when(hrPtoSyncJob.runForDate(anyString(), anyBoolean())).thenReturn(result("OK"));
        when(interfaceContractCheck.run(anyString())).thenReturn(new InterfaceContractReport());
    }

    @Test
    void anonymousRunIsUnauthorizedAndDoesNotRunJob() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", DATE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(hrPtoSyncJob);
    }

    @Test
    void anonymousLogAndReportReadsAreUnauthorized() throws Exception {
        for (String path : new String[] {"/interfaces/hr-pto-sync/errors", "/interfaces/hr-pto-sync/runs",
                "/interfaces/hr-pto-sync/exports", "/interfaces/fhir/contract-report"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(hrPtoSyncJob, interfaceContractCheck);
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad token"));
        mvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(hrPtoSyncJob);
    }

    @Test
    void nonOperatorIsForbidden() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run").with(user("nurse").roles("STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/interfaces/hr-pto-sync/errors").with(user("dr").roles("PROVIDER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(hrPtoSyncJob);
    }

    @Test
    void interfaceAdminCanRun() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", DATE)
                        .with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
        verify(hrPtoSyncJob).runForDate(DATE, false);
    }

    @Test
    void adminCanReadRuns() throws Exception {
        when(hrPtoSyncJob.readRuns()).thenReturn(Collections.singletonList(result("OK")));
        mvc.perform(get("/interfaces/hr-pto-sync/runs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exportDate").value(DATE));
    }

    @Test
    void jwtBearerForOperatorIsAccepted() throws Exception {
        User ops = User.builder().username("ops").password("x").enabled(true)
                .roles(Set.of(User.Role.INTERFACE_ADMIN)).build();
        when(jwtTokenProvider.getUsernameFromToken("ops-token")).thenReturn("ops");
        when(jwtTokenProvider.validateToken(eq("ops-token"), any(UserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername("ops")).thenReturn(ops);

        mvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer ops-token"))
                .andExpect(status().isOk());
        verify(hrPtoSyncJob).runForDate(DATE, false);
    }

    @Test
    void repeatRunForSameDateIsConflict() throws Exception {
        when(hrPtoSyncJob.runForDate(DATE, false)).thenReturn(result(HrPtoSyncJob.STATUS_ALREADY_RUN));
        mvc.perform(post("/interfaces/hr-pto-sync/run").with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HrPtoSyncJob.STATUS_ALREADY_RUN));
    }

    @Test
    void forceIsPassedThrough() throws Exception {
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("force", "true")
                        .with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isOk());
        verify(hrPtoSyncJob).runForDate(DATE, true);
    }

    @Test
    void throttledRunReturns429WithoutRunning() throws Exception {
        when(throttle.tryAcquire(InterfaceController.RUN_OPERATION)).thenReturn(42L);
        mvc.perform(post("/interfaces/hr-pto-sync/run").with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
        verify(hrPtoSyncJob, never()).runForDate(anyString(), anyBoolean());
    }

    @Test
    void throttledContractReportReturns429WithoutValidating() throws Exception {
        when(throttle.tryAcquire(InterfaceController.CONTRACT_REPORT_OPERATION)).thenReturn(5L);
        mvc.perform(get("/interfaces/fhir/contract-report").with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(interfaceContractCheck);
    }

    @Test
    void malformedExportDateIsBadRequest() throws Exception {
        when(hrExportReader.resolveExportDate("../x")).thenThrow(new IllegalArgumentException("exportDate must be yyyyMMdd"));
        mvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", "../x")
                        .with(user("ops").roles("INTERFACE_ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(hrPtoSyncJob);
    }

    private static Map<String, Object> result(String status) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("exportDate", DATE);
        r.put("status", status);
        return r;
    }
}
