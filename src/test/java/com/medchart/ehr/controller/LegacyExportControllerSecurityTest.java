package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditAspect;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.legacy.EncounterExportService;
import com.medchart.ehr.legacy.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LegacyExportController.class)
@Import({SecurityConfig.class, AuditAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
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

    @MockBean
    private AuditService auditService;

    @Test
    void anonymousDailyReportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportGenerator, auditService);
    }

    @Test
    void anonymousPatientEncounterExportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/patient/1/encounters")).andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterExportService);
    }

    @Test
    void anonymousEncounterRangeExportIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/encounters")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterExportService);
    }

    @Test
    void anonymousRosterAndSummaryReportsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/export/reports/patient-roster")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/export/reports/encounter-summary")
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-01-31T00:00:00"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportGenerator);
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken(any())).thenThrow(new IllegalArgumentException("bad token"));
        mockMvc.perform(get("/v1/export/reports/daily").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportGenerator);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily")).andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/export/patient/1/encounters")).andExpect(status().isForbidden());
        verifyNoInteractions(reportGenerator, encounterExportService);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffIsForbidden() throws Exception {
        mockMvc.perform(get("/v1/export/reports/daily")).andExpect(status().isForbidden());
        verifyNoInteractions(reportGenerator);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReceivesDailyReport() throws Exception {
        when(reportGenerator.generateDailyReport()).thenReturn("ID,MRN\n1,MRN1\n".getBytes());
        mockMvc.perform(get("/v1/export/reports/daily"))
                .andExpect(status().isOk())
                .andExpect(content().string("ID,MRN\n1,MRN1\n"));
        AuditEvent event = capturedAuditEvent();
        assertEquals(AuditAction.EXPORT, event.getAction());
        assertEquals(Boolean.TRUE, event.getSuccess());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReceivesPatientEncounterExport() throws Exception {
        when(encounterExportService.exportPatientEncounterHistory(7L)).thenReturn("history".getBytes());
        mockMvc.perform(get("/v1/export/patient/7/encounters"))
                .andExpect(status().isOk())
                .andExpect(content().string("history"));
        AuditEvent event = capturedAuditEvent();
        assertEquals(AuditAction.EXPORT, event.getAction());
        assertEquals(7L, event.getPatientId());
    }

    private AuditEvent capturedAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    @Test
    void everyExportEndpointIsAudited() {
        int endpoints = 0;
        for (Method method : LegacyExportController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)) continue;
            endpoints++;
            AuditAccess audit = method.getAnnotation(AuditAccess.class);
            assertNotNull(audit, method.getName());
            assertEquals(AuditAction.EXPORT, audit.action(), method.getName());
        }
        assertEquals(5, endpoints);
    }
}
