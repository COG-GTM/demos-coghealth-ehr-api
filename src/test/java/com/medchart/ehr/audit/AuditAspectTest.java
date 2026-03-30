package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditAspect")
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditAspect auditAspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    // Helper annotated class for testing
    static class TestService {
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient")
        public String getPatient(Long id) {
            return "patient";
        }

        @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
        public String createEncounter(String data) {
            return "encounter";
        }
    }

    private void setupJoinPoint(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = TestService.class.getMethod(methodName, paramTypes);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
    }

    @Nested
    @DisplayName("successful method execution")
    class SuccessfulExecution {

        @Test
        @DisplayName("should create audit event on successful execution")
        void shouldCreateAuditEventOnSuccess() throws Throwable {
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("patient data");

            Object result = auditAspect.auditAccess(joinPoint);

            assertThat(result).isEqualTo("patient data");

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());

            AuditEvent event = eventCaptor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.READ);
            assertThat(event.getResourceType()).isEqualTo("Patient");
            assertThat(event.getDescription()).isEqualTo("View patient");
            assertThat(event.getSuccess()).isTrue();
            assertThat(event.getPatientId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should extract patient ID from Long argument")
        void shouldExtractPatientIdFromLongArg() throws Throwable {
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getPatientId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should set null patientId when no Long argument")
        void shouldSetNullPatientIdWhenNoLongArg() throws Throwable {
            setupJoinPoint("createEncounter", String.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"some data"});
            when(joinPoint.proceed()).thenReturn("encounter");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getPatientId()).isNull();
        }
    }

    @Nested
    @DisplayName("failed method execution")
    class FailedExecution {

        @Test
        @DisplayName("should create audit event with failure flag on exception")
        void shouldCreateAuditEventOnFailure() throws Throwable {
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            RuntimeException exception = new RuntimeException("DB error");
            when(joinPoint.proceed()).thenThrow(exception);

            assertThatThrownBy(() -> auditAspect.auditAccess(joinPoint))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());

            AuditEvent event = eventCaptor.getValue();
            assertThat(event.getSuccess()).isFalse();
            assertThat(event.getErrorMessage()).isEqualTo("DB error");
        }

        @Test
        @DisplayName("should re-throw the original exception")
        void shouldRethrowOriginalException() throws Throwable {
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            IllegalStateException exception = new IllegalStateException("Invalid state");
            when(joinPoint.proceed()).thenThrow(exception);

            assertThatThrownBy(() -> auditAspect.auditAccess(joinPoint))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid state");
        }
    }

    @Nested
    @DisplayName("request context")
    class RequestContext {

        @Test
        @DisplayName("should capture IP address from request")
        void shouldCaptureIpAddress() throws Throwable {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should capture X-Forwarded-For header when present")
        void shouldCaptureXForwardedFor() throws Throwable {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should capture User-Agent header")
        void shouldCaptureUserAgent() throws Throwable {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", "MedChart-EHR/3.2.1");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUserAgent()).isEqualTo("MedChart-EHR/3.2.1");
        }

        @Test
        @DisplayName("should handle null request context gracefully")
        void shouldHandleNullRequestContext() throws Throwable {
            // No request context set
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getIpAddress()).isNull();
            assertThat(eventCaptor.getValue().getUserAgent()).isNull();
        }
    }

    @Nested
    @DisplayName("user identification")
    class UserIdentification {

        @Test
        @DisplayName("should set default system user when no auth context")
        void shouldSetDefaultSystemUser() throws Throwable {
            setupJoinPoint("getPatient", Long.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(joinPoint.proceed()).thenReturn("data");

            auditAspect.auditAccess(joinPoint);

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).saveAuditEventAsync(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUserId()).isEqualTo("system");
            assertThat(eventCaptor.getValue().getUserName()).isEqualTo("System User");
        }
    }
}
