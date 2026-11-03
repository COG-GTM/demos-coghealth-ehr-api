package com.medchart.ehr.audit;

import com.medchart.ehr.service.PatientService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private AuditService auditService;
    private AuditAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        auditService = mock(AuditService.class);
        aspect = new AuditAspect(auditService);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(PatientService.class.getMethod("getPatientById", Long.class));
        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("ok");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsSocketAddressAndIgnoresClientSuppliedForwardedFor() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "10.20.30.40, 198.51.100.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.auditAccess(joinPoint);

        assertEquals("203.0.113.7", capturedEvent().getIpAddress());
    }

    @Test
    void recordsSocketAddressWithoutForwardedFor() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.23");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.auditAccess(joinPoint);

        assertEquals("198.51.100.23", capturedEvent().getIpAddress());
    }

    @Test
    void recordsNoAddressOutsideRequest() throws Throwable {
        aspect.auditAccess(joinPoint);

        assertNull(capturedEvent().getIpAddress());
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
