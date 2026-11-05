package com.medchart.ehr.service.chronic;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.service.ProviderNotificationService;
import com.medchart.ehr.service.ProviderNotificationService.NotificationResult;
import com.medchart.ehr.service.ProviderNotificationService.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationNotificationServiceTest {

    @Mock
    private ProviderNotificationService providerNotificationService;
    @Mock
    private PatientAccessLogger accessLogger;
    @InjectMocks
    private MedicationNotificationService service;

    private static CompletableFuture<NotificationResult> ok() {
        NotificationResult r = new NotificationResult();
        r.setSuccess(true);
        return CompletableFuture.completedFuture(r);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    @Test
    @DisplayName("BR-CHR-05: non-adherence alerts go IN_APP to the provider with PDC as a one-decimal percentage")
    void brChr05_nonAdherenceAlert() throws Exception {
        when(providerNotificationService.notifyProvider(eq(3L), isNull(), isNull(), eq(NotificationType.IN_APP),
                anyString(), anyString(), anyMap())).thenReturn(ok());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> meta = mapCaptor();

        service.sendNonAdherenceAlert(3L, 7L, "MRN-1", "Test Patient", "Metformin", 0.7949).get();

        verify(providerNotificationService).notifyProvider(eq(3L), isNull(), isNull(), eq(NotificationType.IN_APP),
                subject.capture(), message.capture(), meta.capture());
        assertEquals("Medication Non-Adherence Alert: Test Patient", subject.getValue());
        assertTrue(message.getValue().contains("PDC score of 79.5% for Metformin"), message.getValue());
        assertEquals("NON_ADHERENCE", meta.getValue().get("alertType"));
        assertEquals(0.7949, meta.getValue().get("pdcScore"));
        assertEquals(7L, meta.getValue().get("patientId"));
    }

    @Test
    @DisplayName("BR-CHR-05: non-adherence alerts are audited as Alert CREATE")
    void brChr05_alertsAudited() throws Exception {
        AuditAccess a = MedicationNotificationService.class.getMethod("sendNonAdherenceAlert", Long.class, Long.class,
                String.class, String.class, String.class, double.class).getAnnotation(AuditAccess.class);
        AuditAccess g = MedicationNotificationService.class.getMethod("sendCareGapAlert", Long.class, Long.class,
                String.class, String.class, String.class, String.class).getAnnotation(AuditAccess.class);
        assertEquals(AuditAction.CREATE, a.action());
        assertEquals("Alert", a.resourceType());
        assertEquals(AuditAction.CREATE, g.action());
    }

    @Test
    @DisplayName("BR-CHR-06: care-gap alerts go IN_APP with the gap type in subject and metadata")
    void brChr06_careGapAlert() throws Exception {
        when(providerNotificationService.notifyProvider(eq(3L), isNull(), isNull(), eq(NotificationType.IN_APP),
                anyString(), anyString(), anyMap())).thenReturn(ok());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> meta = mapCaptor();

        service.sendCareGapAlert(3L, 7L, "MRN-1", "Test Patient", "HBA1C_OVERDUE", "No HbA1c in 6 months").get();

        verify(providerNotificationService).notifyProvider(eq(3L), isNull(), isNull(), eq(NotificationType.IN_APP),
                subject.capture(), anyString(), meta.capture());
        assertEquals("Care Gap Alert: Test Patient - HBA1C_OVERDUE", subject.getValue());
        assertEquals("CARE_GAP", meta.getValue().get("alertType"));
        assertEquals("HBA1C_OVERDUE", meta.getValue().get("gapType"));
    }

    @Test
    @DisplayName("BR-CHR-07: critical lab results escalate as a HIGH-severity CRITICAL_LAB critical alert")
    void brChr07_criticalLabEscalates() {
        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        when(providerNotificationService.sendCriticalAlert(3L, 7L, "MRN-1", "CRITICAL_LAB",
                "Potassium result 6.5 exceeds critical threshold 6.0", "HIGH")).thenReturn(done);

        assertEquals(done, service.sendCriticalLabAlert(3L, 7L, "MRN-1", "Test Patient", "Potassium", "6.5", "6.0"));
    }

    @Test
    @DisplayName("BR-CHR-08: the daily digest is skipped when there is nothing to report")
    void brChr08_emptyDigestSkipped() throws Exception {
        assertNull(service.sendDailyDigest(3L, "dr@example.test", 0, 0, 0).get());
        verifyNoInteractions(providerNotificationService);
    }

    @Test
    @DisplayName("BR-CHR-08: a non-empty digest is emailed with the total item count")
    void brChr08_digestEmailedWithTotal() throws Exception {
        when(providerNotificationService.notifyProvider(eq(3L), eq("dr@example.test"), isNull(),
                eq(NotificationType.EMAIL), anyString(), anyString(), anyMap())).thenReturn(ok());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);

        service.sendDailyDigest(3L, "dr@example.test", 0, 0, 1).get();

        verify(providerNotificationService).notifyProvider(eq(3L), eq("dr@example.test"), isNull(),
                eq(NotificationType.EMAIL), subject.capture(), message.capture(), any());
        assertEquals("Daily Chronic Care Digest: 1 items need attention", subject.getValue());
        assertTrue(message.getValue().contains("- 1 critical lab results"));
    }

    @Test
    @DisplayName("BR-CHR-08: any single non-zero category triggers the digest (boundary)")
    void brChr08_eachCategoryTriggers() throws Exception {
        when(providerNotificationService.notifyProvider(any(), any(), any(), any(), anyString(), anyString(), anyMap()))
                .thenReturn(ok());
        service.sendDailyDigest(3L, "e", 1, 0, 0).get();
        service.sendDailyDigest(3L, "e", 0, 2, 0).get();
        verify(providerNotificationService).notifyProvider(eq(3L), eq("e"), isNull(), eq(NotificationType.EMAIL),
                eq("Daily Chronic Care Digest: 2 items need attention"), anyString(), anyMap());
    }

    @Test
    @DisplayName("BR-CHR-09: refill reminders are a stub that completes without contacting anyone")
    void brChr09_refillReminderStub() throws Exception {
        assertNull(service.sendRefillReminder(7L, "MRN-1", "555-0100", "Metformin", LocalDate.of(2026, 2, 1)).get());
        verifyNoInteractions(providerNotificationService, accessLogger);
    }
}
