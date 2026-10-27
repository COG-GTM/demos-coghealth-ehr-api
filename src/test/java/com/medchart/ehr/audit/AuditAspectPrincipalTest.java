package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditAspectPrincipalTest {

    private AuditService auditService;
    private Exporter exporter;

    static class Exporter {
        @AuditAccess(action = AuditAction.EXPORT, resourceType = "Patient", description = "test export")
        public String export() {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new Exporter());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditService));
        exporter = factory.getProxy();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedUser() {
        User admin = User.builder().id(42L).username("admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, Collections.emptyList()));

        exporter.export();

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("42");
        assertThat(event.getUserName()).isEqualTo("admin");
        assertThat(event.getAction()).isEqualTo(AuditAction.EXPORT);
    }

    @Test
    void fallsBackToSystemWithoutAuthentication() {
        exporter.export();

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("system");
        assertThat(event.getUserName()).isEqualTo("System User");
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
