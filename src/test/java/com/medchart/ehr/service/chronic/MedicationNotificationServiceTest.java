package com.medchart.ehr.service.chronic;

import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.service.ProviderNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    private CompletableFuture<ProviderNotificationService.NotificationResult> successResult() {
        ProviderNotificationService.NotificationResult result =
                new ProviderNotificationService.NotificationResult();
        result.setSuccess(true);
        return CompletableFuture.completedFuture(result);
    }

    @Test
    void sendDailyDigestSkipsNotificationWhenNoAlerts() {
        CompletableFuture<Void> future = service.sendDailyDigest(7L, "doc@example.com", 0, 0, 0);

        assertThat(future).isCompleted();
        verifyNoInteractions(providerNotificationService);
    }

    @Test
    void sendDailyDigestSummarizesTotalAndPerCategoryCounts() {
        when(providerNotificationService.notifyProvider(anyLong(), anyString(), any(),
                any(), anyString(), anyString(), any())).thenReturn(successResult());

        service.sendDailyDigest(7L, "doc@example.com", 2, 3, 1).join();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);

        verify(providerNotificationService).notifyProvider(eq(7L), eq("doc@example.com"), eq(null),
                eq(ProviderNotificationService.NotificationType.EMAIL),
                subject.capture(), message.capture(), metadata.capture());

        assertThat(subject.getValue()).isEqualTo("Daily Chronic Care Digest: 6 items need attention");
        assertThat(message.getValue())
                .contains("- 2 patients with medication adherence issues")
                .contains("- 3 care gaps identified")
                .contains("- 1 critical lab results");
        assertThat(metadata.getValue()).containsEntry("alertType", "DAILY_DIGEST");
    }

    @Test
    void sendDailyDigestSendsWhenOnlyOneCategoryHasAlerts() {
        when(providerNotificationService.notifyProvider(anyLong(), anyString(), any(),
                any(), anyString(), anyString(), any())).thenReturn(successResult());

        service.sendDailyDigest(7L, "doc@example.com", 0, 0, 1).join();

        verify(providerNotificationService).notifyProvider(eq(7L), eq("doc@example.com"), eq(null),
                eq(ProviderNotificationService.NotificationType.EMAIL),
                eq("Daily Chronic Care Digest: 1 items need attention"), anyString(), any());
    }

    @Test
    void sendNonAdherenceAlertFormatsPdcAsPercentage() {
        when(providerNotificationService.notifyProvider(anyLong(), any(), any(),
                any(), anyString(), anyString(), any())).thenReturn(successResult());

        service.sendNonAdherenceAlert(7L, 42L, "MRN-001", "Jane Roe", "Metformin", 0.734).join();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);

        verify(providerNotificationService).notifyProvider(eq(7L), eq(null), eq(null),
                eq(ProviderNotificationService.NotificationType.IN_APP),
                subject.capture(), message.capture(), metadata.capture());

        assertThat(subject.getValue()).isEqualTo("Medication Non-Adherence Alert: Jane Roe");
        assertThat(message.getValue()).isEqualTo(
                "Patient Jane Roe (MRN: MRN-001) has a PDC score of 73.4% for Metformin. "
                        + "Consider outreach to discuss barriers to medication adherence.");
        assertThat(metadata.getValue())
                .containsEntry("alertType", "NON_ADHERENCE")
                .containsEntry("patientId", 42L)
                .containsEntry("patientMrn", "MRN-001")
                .containsEntry("medicationName", "Metformin")
                .containsEntry("pdcScore", 0.734);
    }

    @Test
    void sendCareGapAlertBuildsSubjectAndMessageFromGapDetails() {
        when(providerNotificationService.notifyProvider(anyLong(), any(), any(),
                any(), anyString(), anyString(), any())).thenReturn(successResult());

        service.sendCareGapAlert(7L, 42L, "MRN-001", "Jane Roe", "HBA1C_OVERDUE",
                "HbA1c last drawn 9 months ago").join();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);

        verify(providerNotificationService).notifyProvider(eq(7L), eq(null), eq(null),
                eq(ProviderNotificationService.NotificationType.IN_APP),
                subject.capture(), message.capture(), any());

        assertThat(subject.getValue()).isEqualTo("Care Gap Alert: Jane Roe - HBA1C_OVERDUE");
        assertThat(message.getValue())
                .startsWith("Patient Jane Roe (MRN: MRN-001) has an identified care gap:")
                .contains("HbA1c last drawn 9 months ago");
    }

    @Test
    void sendCriticalLabAlertDelegatesToCriticalAlertChannel() {
        when(providerNotificationService.sendCriticalAlert(anyLong(), anyLong(), anyString(),
                anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.sendCriticalLabAlert(7L, 42L, "MRN-001", "Jane Roe", "HbA1c", "10.2%", "9%").join();

        verify(providerNotificationService).sendCriticalAlert(eq(7L), eq(42L), eq("MRN-001"),
                eq("CRITICAL_LAB"), eq("HbA1c result 10.2% exceeds critical threshold 9%"), eq("HIGH"));
        verify(providerNotificationService, never()).notifyProvider(anyLong(), any(), any(),
                any(), anyString(), anyString(), any());
    }
}
