package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void saveAuditEventAsync_savesEvent() {
        AuditEvent event = AuditEvent.builder()
                .userId("USR001")
                .userName("Dr. Anderson")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .description("View patient record")
                .success(true)
                .build();

        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }

    @Test
    void saveAuditEventAsync_repositoryThrowsException_handlesGracefully() {
        AuditEvent event = AuditEvent.builder()
                .userId("USR001")
                .action(AuditAction.READ)
                .build();

        doThrow(new RuntimeException("DB connection failed"))
                .when(auditEventRepository).save(event);

        // Should not throw
        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }
}
