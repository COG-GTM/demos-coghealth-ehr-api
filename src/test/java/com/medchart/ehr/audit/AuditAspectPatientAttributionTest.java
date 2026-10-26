package com.medchart.ehr.audit;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.service.PatientService;
import com.medchart.ehr.service.ProviderNotificationService;
import com.medchart.ehr.service.chronic.ChronicMedicationService;
import com.medchart.ehr.service.chronic.MedicationNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectPatientAttributionTest {

    private static final Long PATIENT_ID = 42L;
    private static final Long PROVIDER_ID = 7L;
    private static final String MRN = "MRN0000000042";

    private AuditService auditService;
    private PatientRepository patientRepository;
    private PatientMapper patientMapper;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        patientRepository = mock(PatientRepository.class);
        patientMapper = mock(PatientMapper.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T audited(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditService));
        return (T) factory.getProxy();
    }

    private AuditEvent recordedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    private PatientService patientService() {
        return audited(new PatientService(patientRepository, patientMapper));
    }

    @Test
    void getPatientByMrnRecordsResolvedPatientIdAndMrn() {
        Patient patient = new Patient();
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(PatientDTO.builder().id(PATIENT_ID).mrn(MRN).build());

        patientService().getPatientByMrn(MRN);

        AuditEvent event = recordedEvent();
        assertEquals(PATIENT_ID, event.getPatientId());
        assertEquals(MRN, event.getPatientMrn());
        assertTrue(event.getSuccess());
    }

    @Test
    void failedMrnLookupStillRecordsRequestedMrn() {
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> patientService().getPatientByMrn(MRN));

        AuditEvent event = recordedEvent();
        assertNull(event.getPatientId());
        assertEquals(MRN, event.getPatientMrn());
        assertFalse(event.getSuccess());
    }

    @Test
    void oversizedMrnIsTruncatedToColumnLength() {
        String longMrn = "X".repeat(64);
        when(patientRepository.findByMrn(longMrn)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> patientService().getPatientByMrn(longMrn));

        assertEquals(AuditAspect.PATIENT_MRN_MAX_LENGTH, recordedEvent().getPatientMrn().length());
    }

    @Test
    void getPatientByIdRecordsPatientId() {
        Patient patient = new Patient();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(PatientDTO.builder().id(PATIENT_ID).mrn(MRN).build());

        patientService().getPatientById(PATIENT_ID);

        assertEquals(PATIENT_ID, recordedEvent().getPatientId());
    }

    @Test
    void createPatientRecordsNewPatientIdFromResult() {
        PatientDTO input = PatientDTO.builder().firstName("Ada").lastName("Lovelace").build();
        Patient entity = new Patient();
        entity.setMrn(MRN);
        when(patientMapper.toEntity(input)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(entity);
        when(patientMapper.toDto(entity)).thenReturn(PatientDTO.builder().id(PATIENT_ID).mrn(MRN).build());

        patientService().createPatient(input);

        AuditEvent event = recordedEvent();
        assertEquals(PATIENT_ID, event.getPatientId());
        assertEquals(MRN, event.getPatientMrn());
    }

    @Test
    void searchDoesNotGuessAPatient() {
        when(patientRepository.searchPatients(anyString(), any())).thenReturn(Page.empty());

        patientService().searchPatients("smith", PageRequest.of(0, 20));

        AuditEvent event = recordedEvent();
        assertNull(event.getPatientId());
        assertNull(event.getPatientMrn());
    }

    @Test
    void sendCriticalAlertRecordsPatientNotProvider() {
        ProviderNotificationService service = audited(new ProviderNotificationService());

        service.sendCriticalAlert(PROVIDER_ID, PATIENT_ID, MRN, "LAB", "K+ 6.8", "HIGH");

        AuditEvent event = recordedEvent();
        assertEquals(PATIENT_ID, event.getPatientId());
        assertEquals(MRN, event.getPatientMrn());
    }

    @Test
    void medicationAlertsRecordPatientNotProvider() {
        ProviderNotificationService providerNotifications = mock(ProviderNotificationService.class);
        ProviderNotificationService.NotificationResult delivered = new ProviderNotificationService.NotificationResult();
        delivered.setSuccess(true);
        when(providerNotifications.notifyProvider(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(delivered));
        MedicationNotificationService service = audited(
                new MedicationNotificationService(providerNotifications, mock(PatientAccessLogger.class)));

        service.sendNonAdherenceAlert(PROVIDER_ID, PATIENT_ID, MRN, "Test Patient", "Metformin", 0.5).join();
        service.sendCareGapAlert(PROVIDER_ID, PATIENT_ID, MRN, "Test Patient", "HbA1c", "Overdue").join();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).saveAuditEventAsync(captor.capture());
        for (AuditEvent event : captor.getAllValues()) {
            assertEquals(PATIENT_ID, event.getPatientId());
            assertEquals(MRN, event.getPatientMrn());
        }
    }

    @Test
    void failedCallStillRecordsAnnotatedPatient() {
        ChronicMedicationService service = audited(new ChronicMedicationService());

        assertThrows(UnsupportedOperationException.class, () -> service.identifyCareGaps(PATIENT_ID, 99L));

        AuditEvent event = recordedEvent();
        assertEquals(PATIENT_ID, event.getPatientId());
        assertFalse(event.getSuccess());
    }

    @Test
    void everyAuditedMethodWithALongParameterNamesThePatientArgument() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        List<String> offenders = new ArrayList<>();
        int audited = 0;
        for (BeanDefinition bean : scanner.findCandidateComponents("com.medchart.ehr")) {
            for (Method method : Class.forName(bean.getBeanClassName()).getDeclaredMethods()) {
                if (!method.isAnnotationPresent(AuditAccess.class)) {
                    continue;
                }
                audited++;
                boolean hasLong = false;
                boolean marked = false;
                Class<?>[] types = method.getParameterTypes();
                Annotation[][] annotations = method.getParameterAnnotations();
                for (int i = 0; i < types.length; i++) {
                    hasLong |= types[i] == Long.class;
                    for (Annotation annotation : annotations[i]) {
                        marked |= annotation instanceof AuditPatientId;
                    }
                }
                if (hasLong && !marked) {
                    offenders.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(audited > 0, "no @AuditAccess methods found");
        assertTrue(offenders.isEmpty(), "@AuditAccess methods with Long params but no @AuditPatientId: " + offenders);
    }
}
