package com.medchart.ehr.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderNotificationService")
class ProviderNotificationServiceTest {

    @InjectMocks
    private ProviderNotificationService notificationService;

    @Nested
    @DisplayName("notifyProvider")
    class NotifyProvider {

        @Test
        @DisplayName("should send EMAIL notification successfully")
        void shouldSendEmailNotification() throws ExecutionException, InterruptedException {
            CompletableFuture<ProviderNotificationService.NotificationResult> future =
                    notificationService.notifyProvider(
                            1L, "doctor@medchart.com", null,
                            ProviderNotificationService.NotificationType.EMAIL,
                            "Lab Results Ready", "Critical lab results for patient",
                            Map.of("priority", "HIGH"));

            ProviderNotificationService.NotificationResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProviderId()).isEqualTo(1L);
            assertThat(result.getType()).isEqualTo(ProviderNotificationService.NotificationType.EMAIL);
            assertThat(result.getSentAt()).isNotNull();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("should send SMS notification successfully")
        void shouldSendSmsNotification() throws ExecutionException, InterruptedException {
            CompletableFuture<ProviderNotificationService.NotificationResult> future =
                    notificationService.notifyProvider(
                            2L, null, "555-0100",
                            ProviderNotificationService.NotificationType.SMS,
                            "Urgent", "Patient needs attention",
                            Map.of());

            ProviderNotificationService.NotificationResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getType()).isEqualTo(ProviderNotificationService.NotificationType.SMS);
        }

        @Test
        @DisplayName("should send IN_APP notification successfully")
        void shouldSendInAppNotification() throws ExecutionException, InterruptedException {
            CompletableFuture<ProviderNotificationService.NotificationResult> future =
                    notificationService.notifyProvider(
                            3L, null, null,
                            ProviderNotificationService.NotificationType.IN_APP,
                            "New Message", "You have a new message",
                            Map.of("category", "MESSAGE"));

            ProviderNotificationService.NotificationResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getType()).isEqualTo(ProviderNotificationService.NotificationType.IN_APP);
        }

        @Test
        @DisplayName("should send ALL notification types successfully")
        void shouldSendAllNotificationTypes() throws ExecutionException, InterruptedException {
            CompletableFuture<ProviderNotificationService.NotificationResult> future =
                    notificationService.notifyProvider(
                            4L, "doctor@medchart.com", "555-0200",
                            ProviderNotificationService.NotificationType.ALL,
                            "Critical Alert", "Immediate attention required",
                            Map.of("severity", "CRITICAL"));

            ProviderNotificationService.NotificationResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getType()).isEqualTo(ProviderNotificationService.NotificationType.ALL);
        }
    }

    @Nested
    @DisplayName("sendCriticalAlert")
    class SendCriticalAlert {

        @Test
        @DisplayName("should send critical alert without error")
        void shouldSendCriticalAlert() throws ExecutionException, InterruptedException {
            CompletableFuture<Void> future = notificationService.sendCriticalAlert(
                    1L, 100L, "MRN001", "CRITICAL_LAB",
                    "Potassium level critically high", "HIGH");

            future.get();
            // Should complete without exception
        }
    }

    @Nested
    @DisplayName("notifyCareTeam")
    class NotifyCareTeam {

        @Test
        @DisplayName("should notify care team and return empty list")
        void shouldNotifyCareTeam() throws ExecutionException, InterruptedException {
            CompletableFuture<List<ProviderNotificationService.NotificationResult>> future =
                    notificationService.notifyCareTeam(
                            100L, List.of(1L, 2L, 3L),
                            "Patient Update", "Patient status changed");

            List<ProviderNotificationService.NotificationResult> results = future.get();

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("NotificationResult")
    class NotificationResultTest {

        @Test
        @DisplayName("should correctly store error message on failure")
        void shouldStoreErrorOnFailure() {
            ProviderNotificationService.NotificationResult result =
                    new ProviderNotificationService.NotificationResult();
            result.setProviderId(1L);
            result.setSuccess(false);
            result.setErrorMessage("Connection timeout");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Connection timeout");
        }
    }
}
