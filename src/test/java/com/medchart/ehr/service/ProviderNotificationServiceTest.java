package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProviderNotificationServiceTest {

    private MeterRegistry meterRegistry;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditService = mock(AuditService.class);
    }

    @Test
    void criticalAlertRecordsDeliveredOutcome() throws ExecutionException, InterruptedException {
        ProviderNotificationService service = new ProviderNotificationService(meterRegistry, auditService);

        ProviderNotificationService.CriticalAlertResult result = service.sendCriticalAlert(
                7L, 42L, "MRN-1", "CRITICAL_LAB", "HbA1c 12.1", "HIGH").get();

        assertThat(result.isDelivered()).isTrue();
        assertThat(result.getDeliveries()).hasSize(1);
        assertThat(result.getEscalationStep())
                .isEqualTo(ProviderNotificationService.EscalationStep.IMMEDIATE);
        assertThat(counter("channel", "IN_APP", "outcome", "delivered")).isEqualTo(1.0);
        assertThat(counter("channel", "all", "outcome", "delivered")).isEqualTo(1.0);
        assertThat(savedAuditEvent().getSuccess()).isTrue();
    }

    @Test
    void criticalAlertEscalatesToPagerWhenImmediateChannelFails() throws ExecutionException, InterruptedException {
        ProviderNotificationService service = new ProviderNotificationService(meterRegistry, auditService) {
            @Override
            protected void createInAppNotification(Long providerId, String subject, String message,
                                                   Map<String, Object> metadata) {
                throw new IllegalStateException("in-app channel down");
            }
        };

        ProviderNotificationService.CriticalAlertResult result = service.sendCriticalAlert(
                7L, 42L, "MRN-1", "CRITICAL_LAB", "HbA1c 12.1", "HIGH").get();

        assertThat(result.isDelivered()).isTrue();
        assertThat(result.getEscalationStep())
                .isEqualTo(ProviderNotificationService.EscalationStep.ON_CALL_PAGE);
        assertThat(counter("channel", "IN_APP", "outcome", "failed")).isEqualTo(1.0);
        assertThat(counter("channel", "PAGER", "outcome", "delivered")).isEqualTo(1.0);
        assertThat(savedAuditEvent().getSuccess()).isTrue();
    }

    @Test
    void criticalAlertRecordsFailureWhenEveryChannelFails() throws ExecutionException, InterruptedException {
        ProviderNotificationService service = new ProviderNotificationService(meterRegistry, auditService) {
            @Override
            protected void createInAppNotification(Long providerId, String subject, String message,
                                                   Map<String, Object> metadata) {
                throw new IllegalStateException("in-app channel down");
            }

            @Override
            protected void pageOnCallProvider(Long providerId, String alertType, String message, String severity) {
                throw new IllegalStateException("pager gateway timeout");
            }
        };

        ProviderNotificationService.CriticalAlertResult result = service.sendCriticalAlert(
                7L, 42L, "MRN-1", "CRITICAL_LAB", "HbA1c 12.1", "HIGH").get();

        assertThat(result.isDelivered()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("pager gateway timeout");
        assertThat(counter("channel", "all", "outcome", "undelivered")).isEqualTo(1.0);

        AuditEvent event = savedAuditEvent();
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo("pager gateway timeout");
    }

    @Test
    void careTeamNotificationReportsPerProviderOutcome() throws ExecutionException, InterruptedException {
        ProviderNotificationService service = new ProviderNotificationService(meterRegistry, auditService) {
            @Override
            protected void createInAppNotification(Long providerId, String subject, String message,
                                                   Map<String, Object> metadata) {
                if (providerId == 2L) {
                    throw new IllegalStateException("provider inbox unavailable");
                }
            }
        };

        List<ProviderNotificationService.NotificationResult> results =
                service.notifyCareTeam(42L, List.of(1L, 2L), "Update", "Patient chart updated").get();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).getErrorMessage()).isEqualTo("provider inbox unavailable");
        assertThat(meterRegistry.counter(ProviderNotificationService.CARE_TEAM_METRIC,
                "channel", "IN_APP", "outcome", "failed").count()).isEqualTo(1.0);
    }

    private double counter(String... tags) {
        return meterRegistry.counter(ProviderNotificationService.CRITICAL_ALERT_METRIC, tags).count();
    }

    private AuditEvent savedAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
