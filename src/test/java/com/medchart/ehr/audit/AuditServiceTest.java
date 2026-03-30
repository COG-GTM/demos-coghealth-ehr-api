package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService")
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("should save audit event successfully")
    void shouldSaveAuditEventSuccessfully() {
        AuditEvent event = AuditEvent.builder()
                .userId("system")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .description("View patient record")
                .patientId(1L)
                .success(true)
                .build();

        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }

    @Test
    @DisplayName("should handle save failure gracefully without throwing")
    void shouldHandleSaveFailureGracefully() {
        AuditEvent event = AuditEvent.builder()
                .userId("system")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .build();

        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenThrow(new RuntimeException("DB connection failed"));

        // Should not throw
        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }

    @Test
    @DisplayName("should save audit event with all fields populated")
    void shouldSaveAuditEventWithAllFields() {
        AuditEvent event = AuditEvent.builder()
                .userId("dr.smith")
                .userName("Dr. Smith")
                .patientId(100L)
                .action(AuditAction.UPDATE)
                .resourceType("Patient")
                .description("Updated patient demographics")
                .ipAddress("192.168.1.100")
                .userAgent("MedChart-EHR/3.2.1")
                .success(true)
                .build();

        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }

    @Test
    @DisplayName("should save audit event with failure details")
    void shouldSaveAuditEventWithFailureDetails() {
        AuditEvent event = AuditEvent.builder()
                .userId("system")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .success(false)
                .errorMessage("Access denied")
                .build();

        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }
}
