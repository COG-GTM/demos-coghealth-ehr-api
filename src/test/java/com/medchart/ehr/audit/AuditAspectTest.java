package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private final List<AuditEvent> saved = new ArrayList<>();
    private AuditAspect aspect;

    static class Sample {
        @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", patientIdArg = 0)
        void byPatient(Long patientId) {
        }

        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter",
                patientIdArg = AuditAccess.NONE, resourceIdArg = 0)
        void byEncounter(Long encounterId) {
        }

        @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter",
                patientIdArg = AuditAccess.NONE)
        void byProvider(Long providerId) {
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient")
        void legacy(Long patientId) {
        }
    }

    @BeforeEach
    void setUp() {
        saved.clear();
        AuditService auditService = new AuditService(null) {
            @Override
            public void saveAuditEventAsync(AuditEvent event) {
                saved.add(event);
            }
        };
        aspect = new AuditAspect(auditService);
    }

    private AuditEvent invoke(String methodName, Object... args) throws Throwable {
        Method method = Sample.class.getDeclaredMethod(methodName, Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.auditAccess(joinPoint);
        assertThat(saved).hasSize(1);
        return saved.get(0);
    }

    @Test
    void recordsPatientIdFromDeclaredArgument() throws Throwable {
        AuditEvent event = invoke("byPatient", 42L);

        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getResourceId()).isNull();
        assertThat(event.getResourceType()).isEqualTo("Encounter");
        assertThat(event.getSuccess()).isTrue();
    }

    @Test
    void recordsEncounterIdAsResourceIdWithoutMislabellingItAsPatientId() throws Throwable {
        AuditEvent event = invoke("byEncounter", 7L);

        assertThat(event.getPatientId()).isNull();
        assertThat(event.getResourceId()).isEqualTo(7L);
        assertThat(event.getAction()).isEqualTo(AuditAction.UPDATE);
    }

    @Test
    void ignoresNonPatientIdentifiers() throws Throwable {
        AuditEvent event = invoke("byProvider", 9L);

        assertThat(event.getPatientId()).isNull();
        assertThat(event.getResourceId()).isNull();
    }

    @Test
    void keepsAutoDetectionForUnannotatedArguments() throws Throwable {
        AuditEvent event = invoke("legacy", 3L);

        assertThat(event.getPatientId()).isEqualTo(3L);
    }

    @Test
    void recordsFailures() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("byEncounter", Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        try {
            aspect.auditAccess(joinPoint);
        } catch (IllegalStateException expected) {
            // expected
        }

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSuccess()).isFalse();
        assertThat(saved.get(0).getErrorMessage()).isEqualTo("boom");
        assertThat(saved.get(0).getResourceId()).isEqualTo(5L);
    }

    @Test
    void encounterServiceMethodsAreAudited() {
        List<String> unaudited = new ArrayList<>();
        for (Method method : com.medchart.ehr.service.EncounterService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    && method.getAnnotation(AuditAccess.class) == null) {
                unaudited.add(method.getName());
            }
        }
        assertThat(unaudited).isEmpty();
    }
}
