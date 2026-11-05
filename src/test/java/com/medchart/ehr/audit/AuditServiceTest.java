package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository repository;
    @InjectMocks
    private AuditService service;

    @Test
    @DisplayName("BR-AUD-06: audit events are persisted")
    void brAud06_eventPersisted() {
        AuditEvent e = AuditEvent.builder().userId("u").action(AuditAction.READ).resourceType("Patient").build();
        service.saveAuditEventAsync(e);
        verify(repository).save(e);
    }

    @Test
    @DisplayName("BR-AUD-06: audit persistence failures never break the clinical workflow")
    void brAud06_persistenceFailureSwallowed() {
        AuditEvent e = new AuditEvent();
        when(repository.save(e)).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.saveAuditEventAsync(e));
    }

    @Test
    @DisplayName("BR-AUD-06: audit writes are async and in their own transaction (survive caller rollback)")
    void brAud06_asyncRequiresNew() throws Exception {
        Method m = AuditService.class.getMethod("saveAuditEventAsync", AuditEvent.class);
        assertNotNull(m.getAnnotation(Async.class));
        assertEquals(Propagation.REQUIRES_NEW, m.getAnnotation(Transactional.class).propagation());
    }

    @Test
    @DisplayName("BR-AUD-15: audit events default to success=true and the action vocabulary covers emergency access")
    void brAud15_defaultsAndVocabulary() {
        assertTrue(AuditEvent.builder().build().getSuccess());
        assertTrue(EnumSet.allOf(AuditAction.class).containsAll(EnumSet.of(AuditAction.ACCESS_DENIED,
                AuditAction.EMERGENCY_ACCESS, AuditAction.BREAK_THE_GLASS, AuditAction.EXPORT, AuditAction.PRINT)));
        assertEquals(12, AuditAction.values().length);
    }
}
