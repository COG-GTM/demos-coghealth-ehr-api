package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.service.ProviderNotificationService.NotificationResult;
import com.medchart.ehr.service.ProviderNotificationService.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderNotificationServiceTest {

    private final ProviderNotificationService service = new ProviderNotificationService();

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    @DisplayName("BR-SVC-16: every supported channel delivers and records provider, channel and time")
    void brSvc16_allChannelsSucceed(NotificationType type) throws Exception {
        NotificationResult r = service.notifyProvider(5L, "dr@example.test", "555-0100", type,
                "Lab ready", "A long message body that exceeds fifty characters in total length.", Map.of()).get();
        assertTrue(r.isSuccess());
        assertEquals(5L, r.getProviderId());
        assertEquals(type, r.getType());
        assertNotNull(r.getSentAt());
        assertNull(r.getErrorMessage());
    }

    @Test
    @DisplayName("BR-SVC-16: delivery failures are handled gracefully and reported, not thrown")
    void brSvc16_failureReported() throws Exception {
        NotificationResult r = service.notifyProvider(5L, null, null, NotificationType.SMS, "s", null, null).get();
        assertFalse(r.isSuccess());
        assertNotNull(r.getSentAt());
    }

    @Test
    @DisplayName("BR-SVC-16: notifications are sent asynchronously (non-blocking)")
    void brSvc16_async() throws Exception {
        for (Method m : List.of(
                ProviderNotificationService.class.getMethod("notifyProvider", Long.class, String.class, String.class,
                        NotificationType.class, String.class, String.class, Map.class),
                ProviderNotificationService.class.getMethod("notifyCareTeam", Long.class, List.class, String.class, String.class))) {
            assertNotNull(m.getAnnotation(Async.class), m.getName());
        }
    }

    @Test
    @DisplayName("BR-SVC-17: critical alerts are audited (CREATE CriticalAlert); escalation is currently a stub that completes")
    void brSvc17_criticalAlertAuditedStub() throws Exception {
        Method m = ProviderNotificationService.class.getMethod("sendCriticalAlert", Long.class, Long.class,
                String.class, String.class, String.class, String.class);
        AuditAccess a = m.getAnnotation(AuditAccess.class);
        assertEquals(AuditAction.CREATE, a.action());
        assertEquals("CriticalAlert", a.resourceType());
        assertNull(service.sendCriticalAlert(1L, 2L, "MRN-1", "LAB", "K+ 6.5", "HIGH").get());
    }

    @Test
    @DisplayName("BR-SVC-17: care-team fan-out is a stub that returns no per-provider results")
    void brSvc17_careTeamStub() throws Exception {
        assertTrue(service.notifyCareTeam(1L, List.of(1L, 2L), "s", "m").get().isEmpty());
    }
}
