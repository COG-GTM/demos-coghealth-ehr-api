package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private AuditAspect aspect;

    static class Target {
        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient", description = "Update patient record")
        public void update(String mrn, Long patientId, Long other) {
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        aspect = new AuditAspect(auditService);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Target.class.getMethod("update", String.class, Long.class, Long.class));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private AuditEvent captured() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("BR-AUD-01: audited calls record the annotation's action, resource type, description and acting user")
    void brAud01_eventFromAnnotation() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"MRN-1", 42L, 7L});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.auditAccess(joinPoint);

        AuditEvent e = captured();
        assertEquals(AuditAction.UPDATE, e.getAction());
        assertEquals("Patient", e.getResourceType());
        assertEquals("Update patient record", e.getDescription());
        assertEquals("system", e.getUserId());
        assertEquals("System User", e.getUserName());
    }

    @Test
    @DisplayName("BR-AUD-02: the first Long argument is recorded as the patient id")
    void brAud02_firstLongIsPatientId() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"MRN-1", 42L, 7L});
        aspect.auditAccess(joinPoint);
        assertEquals(42L, captured().getPatientId());
    }

    @Test
    @DisplayName("BR-AUD-02: calls without a Long argument are audited with no patient id")
    void brAud02_noLongArgument() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {"MRN-1", null});
        aspect.auditAccess(joinPoint);
        assertNull(captured().getPatientId());
    }

    @Test
    @DisplayName("BR-AUD-03: successful access is recorded as success and the result is returned unchanged")
    void brAud03_successRecorded() throws Throwable {
        Object result = new Object();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.auditAccess(joinPoint));
        AuditEvent e = captured();
        assertTrue(e.getSuccess());
        assertNull(e.getErrorMessage());
    }

    @Test
    @DisplayName("BR-AUD-04: failed access is still audited (success=false, error message) and the error propagates")
    void brAud04_failureRecordedAndRethrown() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
        IllegalStateException boom = new IllegalStateException("Patient not found with id: 1");
        when(joinPoint.proceed()).thenThrow(boom);

        assertSame(boom, assertThrows(IllegalStateException.class, () -> aspect.auditAccess(joinPoint)));
        AuditEvent e = captured();
        assertFalse(e.getSuccess());
        assertEquals("Patient not found with id: 1", e.getErrorMessage());
    }

    @Test
    @DisplayName("BR-AUD-05: client IP is the first X-Forwarded-For hop; user agent is captured")
    void brAud05_forwardedForAndUserAgent() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", " 203.0.113.9 , 10.0.0.1");
        req.addHeader("User-Agent", "JUnit");
        req.setRemoteAddr("10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        aspect.auditAccess(joinPoint);

        AuditEvent e = captured();
        assertEquals("203.0.113.9", e.getIpAddress());
        assertEquals("JUnit", e.getUserAgent());
    }

    @Test
    @DisplayName("BR-AUD-05: without X-Forwarded-For (absent or empty) the remote address is recorded")
    void brAud05_remoteAddrFallback() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "");
        req.setRemoteAddr("198.51.100.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        aspect.auditAccess(joinPoint);
        assertEquals("198.51.100.4", captured().getIpAddress());
    }

    @Test
    @DisplayName("BR-AUD-05: outside an HTTP request (batch/async) IP and user agent are null")
    void brAud05_noRequestContext() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        aspect.auditAccess(joinPoint);
        AuditEvent e = captured();
        assertNull(e.getIpAddress());
        assertNull(e.getUserAgent());
    }

    @Test
    @DisplayName("BR-AUD-05: errors reading request metadata never block the audited call")
    void brAud05_requestErrorsTolerated() throws Throwable {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenThrow(new IllegalStateException("recycled"));
        when(req.getHeader("User-Agent")).thenThrow(new IllegalStateException("recycled"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.auditAccess(joinPoint));
        AuditEvent e = captured();
        assertNull(e.getIpAddress());
        assertNull(e.getUserAgent());
    }
}
