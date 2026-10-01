package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
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

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private AuditAspect auditAspect;

    static class AuditedTarget {

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient")
        public String getPatient(Long patientId) {
            return "patient";
        }

        @AuditAccess(action = AuditAction.EXPORT, resourceType = "Encounter", description = "Export encounters")
        public String exportEncounters(String query, Long encounterId, Long patientId) {
            return "encounters";
        }

        @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Search patients")
        public String searchPatients(String query) {
            return "results";
        }
    }

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(auditService);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void stubJoinPoint(String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = AuditedTarget.class.getMethod(methodName, parameterTypes);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void recordsSuccessfulAccessAndReturnsResult() throws Throwable {
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("patient");

        Object result = auditAspect.auditAccess(joinPoint);

        assertThat(result).isEqualTo("patient");
        AuditEvent event = capturedEvent();
        assertThat(event.getSuccess()).isTrue();
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getAction()).isEqualTo(AuditAction.READ);
        assertThat(event.getResourceType()).isEqualTo("Patient");
        assertThat(event.getDescription()).isEqualTo("View patient");
        assertThat(event.getUserId()).isEqualTo("system");
        assertThat(event.getUserName()).isEqualTo("System User");
    }

    @Test
    void recordsFailedAccessAndRethrows() throws Throwable {
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        IllegalStateException failure = new IllegalStateException("access denied");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> auditAspect.auditAccess(joinPoint)).isSameAs(failure);

        AuditEvent event = capturedEvent();
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo("access denied");
        assertThat(event.getPatientId()).isEqualTo(42L);
    }

    @Test
    void extractsFirstLongArgumentAsPatientId() throws Throwable {
        stubJoinPoint("exportEncounters", new Class<?>[]{String.class, Long.class, Long.class},
                new Object[]{"q", 7L, 99L});
        when(joinPoint.proceed()).thenReturn("encounters");

        auditAspect.auditAccess(joinPoint);

        assertThat(capturedEvent().getPatientId()).isEqualTo(7L);
    }

    @Test
    void leavesPatientIdNullWhenNoLongArgument() throws Throwable {
        stubJoinPoint("searchPatients", new Class<?>[]{String.class}, new Object[]{"smith"});
        when(joinPoint.proceed()).thenReturn("results");

        auditAspect.auditAccess(joinPoint);

        assertThat(capturedEvent().getPatientId()).isNull();
    }

    @Test
    void usesFirstForwardedForEntryAsClientIp() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.7 , 70.41.3.18 ");
        request.addHeader("User-Agent", "JUnit/1.0");
        request.setRemoteAddr("10.0.0.1");
        bindRequest(request);
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("patient");

        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(event.getUserAgent()).isEqualTo("JUnit/1.0");
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedForIsEmpty() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "");
        request.setRemoteAddr("10.0.0.1");
        bindRequest(request);
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("patient");

        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isNull();
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedForIsAbsent() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");
        bindRequest(request);
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("patient");

        auditAspect.auditAccess(joinPoint);

        assertThat(capturedEvent().getIpAddress()).isEqualTo("192.0.2.5");
    }

    @Test
    void leavesIpAndUserAgentNullWithoutRequestContext() throws Throwable {
        stubJoinPoint("getPatient", new Class<?>[]{Long.class}, new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("patient");

        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getIpAddress()).isNull();
        assertThat(event.getUserAgent()).isNull();
    }
}
