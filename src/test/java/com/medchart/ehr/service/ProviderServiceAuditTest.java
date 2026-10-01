package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.ProviderAuditLogger;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceAuditTest {

    private ProviderRepository providerRepository;
    private ProviderAuditLogger providerAuditLogger;
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        providerRepository = mock(ProviderRepository.class);
        providerAuditLogger = mock(ProviderAuditLogger.class);
        providerService = new ProviderService(providerRepository, providerAuditLogger);
    }

    @Test
    void savingNewProviderAuditsCreate() {
        Provider provider = new Provider();
        provider.setNpi("1234567890");
        Provider saved = new Provider();
        saved.setId(7L);
        saved.setNpi("1234567890");
        when(providerRepository.save(provider)).thenReturn(saved);

        providerService.save(provider);

        verify(providerAuditLogger).logChange(eq(AuditAction.CREATE), eq(7L), eq("1234567890"), any());
    }

    @Test
    void savingExistingProviderAuditsUpdate() {
        Provider provider = new Provider();
        provider.setId(7L);
        provider.setNpi("1234567890");
        when(providerRepository.save(provider)).thenReturn(provider);

        providerService.save(provider);

        verify(providerAuditLogger).logChange(eq(AuditAction.UPDATE), eq(7L), eq("1234567890"), any());
    }

    @Test
    void failedSaveAuditsFailure() {
        Provider provider = new Provider();
        provider.setNpi("1234567890");
        when(providerRepository.save(provider)).thenThrow(new IllegalStateException("constraint violation"));

        assertThrows(IllegalStateException.class, () -> providerService.save(provider));

        verify(providerAuditLogger).logFailedChange(eq(AuditAction.CREATE), isNull(), eq("1234567890"),
                any(), eq("constraint violation"));
        verify(providerAuditLogger, never()).logChange(any(), any(), any(), any());
    }

    @Test
    void deactivationAuditsTheActiveTransition() {
        Provider provider = new Provider();
        provider.setId(7L);
        provider.setNpi("1234567890");
        provider.setActive(true);
        when(providerRepository.findById(7L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(provider)).thenReturn(provider);

        providerService.deactivate(7L);

        assertFalse(provider.getActive());
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(providerAuditLogger).logChange(eq(AuditAction.DELETE), eq(7L), eq("1234567890"),
                description.capture());
        assertTrue(description.getValue().contains("true -> false"));
    }

    @Test
    void deactivatingUnknownProviderAuditsFailure() {
        when(providerRepository.findById(7L)).thenReturn(Optional.empty());

        providerService.deactivate(7L);

        verify(providerAuditLogger).logFailedChange(eq(AuditAction.DELETE), eq(7L), isNull(), any(), any());
        verify(providerRepository, never()).save(any());
    }
}
