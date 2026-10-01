package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void saveAuditEventAsync_delegatesToRepository() {
        AuditEvent event = AuditEvent.builder()
                .userId("user1")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .build();

        when(auditEventRepository.save(event)).thenReturn(event);

        auditService.saveAuditEventAsync(event);

        verify(auditEventRepository).save(event);
    }

    @Test
    void saveAuditEventAsync_swallowsExceptions_whenRepositoryThrows() {
        AuditEvent event = AuditEvent.builder()
                .userId("user1")
                .action(AuditAction.READ)
                .resourceType("Patient")
                .build();

        when(auditEventRepository.save(event)).thenThrow(new RuntimeException("DB connection failed"));

        // Should not throw — exception is swallowed
        assertThatCode(() -> auditService.saveAuditEventAsync(event))
                .doesNotThrowAnyException();

        verify(auditEventRepository).save(event);
    }
}
