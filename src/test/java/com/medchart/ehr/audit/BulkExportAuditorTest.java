package com.medchart.ehr.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkExportAuditorTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private PatientAccessLogger patientAccessLogger;

    @Captor
    private ArgumentCaptor<AuditEvent> eventCaptor;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private BulkExportAuditor auditor() {
        return new BulkExportAuditor(patientAccessLogger, new AuditContext());
    }

    private void authenticate(String username, String role) {
        User principal = new User(username, "", Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void bindRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void recordExportPersistsActorIpAndRecordCount() {
        authenticate("dr.house", "ROLE_PHYSICIAN");
        bindRequest("10.0.0.7", "203.0.113.9, 10.0.0.1");

        auditor().recordExport("Encounter", 42L, 17, "Patient encounter history export");

        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo("dr.house");
        assertThat(event.getUserName()).isEqualTo("ROLE_PHYSICIAN");
        assertThat(event.getAction()).isEqualTo(AuditAction.EXPORT);
        assertThat(event.getResourceType()).isEqualTo("Encounter");
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(event.getDescription()).contains("17 records");
        assertThat(event.getSuccess()).isTrue();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void recordExportFallsBackToAnonymousForUnauthenticatedRequests() {
        bindRequest("10.0.0.7", null);

        auditor().recordExport("Patient", null, 3, "Patient roster report");

        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(AuditContext.ANONYMOUS_USER);
        assertThat(event.getUserName()).isEqualTo(AuditContext.ANONYMOUS_USER);
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.7");
    }

    @Test
    void recordFailedExportMarksEventUnsuccessful() {
        authenticate("clerk", "ROLE_STAFF");
        bindRequest("10.0.0.7", null);

        auditor().recordFailedExport("Patient", null, "Patient roster report",
                new IllegalStateException("query failed"));

        verify(auditEventRepository).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getAction()).isEqualTo(AuditAction.EXPORT);
        assertThat(event.getErrorMessage()).contains("query failed");
    }
}
