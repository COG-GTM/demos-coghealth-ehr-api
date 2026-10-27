package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.interop.fhir.InterfaceContractCheck;
import com.medchart.ehr.interop.fhir.InterfaceContractReport;
import com.medchart.ehr.legacy.HrExportReader;
import com.medchart.ehr.legacy.HrPtoSyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.Collections;
import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InterfaceController.class)
@Import({SecurityConfig.class, HrExportReader.class})
class InterfaceControllerSecurityTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String STAFF_TOKEN = "staff-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HrExportReader hrExportReader;

    @MockBean
    private HrPtoSyncJob hrPtoSyncJob;

    @MockBean
    private InterfaceContractCheck interfaceContractCheck;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        stubToken(ADMIN_TOKEN, user("ops-admin", User.Role.ADMIN));
        stubToken(STAFF_TOKEN, user("front-desk", User.Role.STAFF, User.Role.PROVIDER));
        when(hrPtoSyncJob.runForDate(anyString())).thenReturn(Collections.<String, Object>singletonMap("status", "OK"));
        when(hrPtoSyncJob.readErrors()).thenReturn(Collections.emptyList());
        when(hrPtoSyncJob.readRuns()).thenReturn(Collections.emptyList());
        when(interfaceContractCheck.run(anyString())).thenReturn(new InterfaceContractReport());
    }

    @Test
    void anonymousCallersGet401OnEveryInterfaceRoute() throws Exception {
        for (RequestBuilder request : allRoutes(null)) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verify(hrPtoSyncJob, never()).runForDate(anyString());
        verify(interfaceContractCheck, never()).run(anyString());
    }

    @Test
    void disabledOrLockedAdminIsTreatedAsAnonymous() throws Exception {
        User disabled = user("disabled-admin", User.Role.ADMIN);
        disabled.setEnabled(false);
        stubToken("disabled-token", disabled);
        User locked = user("locked-admin", User.Role.ADMIN);
        locked.setAccountNonLocked(false);
        stubToken("locked-token", locked);
        for (String token : new String[] {"disabled-token", "locked-token"}) {
            for (RequestBuilder request : allRoutes(token)) {
                mockMvc.perform(request).andExpect(status().isUnauthorized());
            }
        }
        verify(hrPtoSyncJob, never()).runForDate(anyString());
        verify(interfaceContractCheck, never()).run(anyString());
    }

    @Test
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        mockMvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verify(hrPtoSyncJob, never()).runForDate(anyString());
    }

    @Test
    void nonAdminUsersGet403OnEveryInterfaceRoute() throws Exception {
        for (RequestBuilder request : allRoutes(STAFF_TOKEN)) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
        verify(hrPtoSyncJob, never()).runForDate(anyString());
        verify(interfaceContractCheck, never()).run(anyString());
    }

    @Test
    void adminCanReachEveryInterfaceRoute() throws Exception {
        for (RequestBuilder request : allRoutes(ADMIN_TOKEN)) {
            mockMvc.perform(request).andExpect(status().isOk());
        }
        verify(hrPtoSyncJob).runForDate("20260930");
        verify(interfaceContractCheck).run("20260930");
    }

    @Test
    void adminRunWithoutDateUsesLatestExport() throws Exception {
        mockMvc.perform(post("/interfaces/hr-pto-sync/run").header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk());
        verify(hrPtoSyncJob).runForDate(eq(hrExportReader.latestExportDate()));
    }

    @Test
    void malformedExportDateIsRejectedWith400() throws Exception {
        String[] bad = {"../x", "2026093", "202609300", "2026-09-30", "20260930/../../etc", "abcdefgh", "${bad}"};
        for (String value : bad) {
            mockMvc.perform(post("/interfaces/hr-pto-sync/run").param("exportDate", value)
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("exportDate must be yyyyMMdd"));
            mockMvc.perform(get("/interfaces/fhir/contract-report").param("exportDate", value)
                            .header("Authorization", "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isBadRequest());
        }
        verify(hrPtoSyncJob, never()).runForDate(anyString());
        verify(interfaceContractCheck, never()).run(anyString());
    }

    private RequestBuilder[] allRoutes(String token) {
        return new RequestBuilder[] {
                auth(get("/interfaces/hr-pto-sync/errors"), token),
                auth(get("/interfaces/hr-pto-sync/runs"), token),
                auth(get("/interfaces/hr-pto-sync/exports"), token),
                auth(post("/interfaces/hr-pto-sync/run").param("exportDate", "20260930"), token),
                auth(get("/interfaces/fhir/contract-report").param("exportDate", "20260930"), token),
        };
    }

    private static RequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private void stubToken(String token, User user) {
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(user.getUsername());
        when(jwtTokenProvider.validateToken(eq(token), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
    }

    private static User user(String username, User.Role first, User.Role... rest) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setRoles(EnumSet.of(first, rest));
        u.setEnabled(true);
        u.setAccountNonExpired(true);
        u.setAccountNonLocked(true);
        u.setCredentialsNonExpired(true);
        return u;
    }
}
