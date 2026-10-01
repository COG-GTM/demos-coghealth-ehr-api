package com.medchart.ehr.audit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthenticationAuditServiceTest {

    private AuditService auditService;
    private MeterRegistry meterRegistry;
    private AuthenticationAuditService authenticationAuditService;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        meterRegistry = new SimpleMeterRegistry();
        authenticationAuditService = new AuthenticationAuditService(auditService, meterRegistry);
    }

    @Test
    void recordsSuccessfulLogin() {
        authenticationAuditService.recordLoginSuccess("dr.house");

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("dr.house");
        assertThat(event.getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(event.getResourceType()).isEqualTo("Authentication");
        assertThat(event.getSuccess()).isTrue();
        assertThat(meterRegistry.counter("auth.login.success").count()).isEqualTo(1.0);
    }

    @Test
    void recordsFailedLoginWithReasonTag() {
        authenticationAuditService.recordLoginFailure("dr.house", "BadCredentialsException");

        AuditEvent event = capturedEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.ACCESS_DENIED);
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo("BadCredentialsException");
        assertThat(meterRegistry.counter("auth.login.failures", "reason", "BadCredentialsException").count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsFailedLoginWithoutUsername() {
        authenticationAuditService.recordLoginFailure(null, null);

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("anonymous");
        assertThat(meterRegistry.counter("auth.login.failures", "reason", "unknown").count()).isEqualTo(1.0);
    }

    @Test
    void recordsRegistration() {
        authenticationAuditService.recordRegistration("new.provider");

        AuditEvent event = capturedEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(event.getResourceType()).isEqualTo("Account");
        assertThat(meterRegistry.counter("auth.registrations").count()).isEqualTo(1.0);
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
