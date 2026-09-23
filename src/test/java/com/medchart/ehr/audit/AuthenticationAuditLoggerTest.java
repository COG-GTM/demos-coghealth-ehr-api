package com.medchart.ehr.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthenticationAuditLoggerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private SimpleMeterRegistry meterRegistry;
    private AuthenticationAuditLogger logger;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        logger = new AuthenticationAuditLogger(auditEventRepository, meterRegistry);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "JUnit");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void logsLoginSuccessWithRequestContext() {
        logger.logLoginSuccess("dr.house");

        AuditEvent event = savedEvent();
        assertEquals(AuditAction.LOGIN, event.getAction());
        assertEquals("dr.house", event.getUserId());
        assertEquals("203.0.113.7", event.getIpAddress());
        assertEquals("JUnit", event.getUserAgent());
        assertTrue(event.getSuccess());
        assertEquals(1.0, meterRegistry.counter("auth.login", "outcome", "success").count());
    }

    @Test
    void logsLoginFailureAsAccessDeniedWithoutCredentials() {
        logger.logLoginFailure("dr.house", "Bad credentials");

        AuditEvent event = savedEvent();
        assertEquals(AuditAction.ACCESS_DENIED, event.getAction());
        assertEquals("dr.house", event.getUserId());
        assertEquals("Bad credentials", event.getErrorMessage());
        assertFalse(event.getSuccess());
        assertEquals(1.0, meterRegistry.counter("auth.login", "outcome", "failure").count());
    }

    @Test
    void logsRegistrationAsUserCreation() {
        logger.logRegistration("dr.house", 42L);

        AuditEvent event = savedEvent();
        assertEquals(AuditAction.CREATE, event.getAction());
        assertEquals("User", event.getResourceType());
        assertEquals(42L, event.getResourceId());
        assertTrue(event.getSuccess());
        assertEquals(1.0, meterRegistry.counter("auth.registration", "outcome", "success").count());
    }

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
