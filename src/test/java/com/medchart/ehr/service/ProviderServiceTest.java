package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderService providerService;

    private Provider provider;

    @BeforeEach
    void setUp() {
        provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Sarah")
                .lastName("Johnson")
                .credentials("MD")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .build();
    }

    @Test
    void findAll_shouldReturnAllProviders() {
        when(providerRepository.findAll()).thenReturn(List.of(provider));

        List<Provider> result = providerService.findAll();

        assertEquals(1, result.size());
        assertEquals("Sarah", result.get(0).getFirstName());
    }

    @Test
    void findActive_shouldReturnActiveOnly() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(provider));

        List<Provider> result = providerService.findActive();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getActive());
    }

    @Test
    void findById_shouldReturnProvider_whenExists() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        Optional<Provider> result = providerService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("1234567890", result.get().getNpi());
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Provider> result = providerService.findById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByNpi_shouldReturnProvider() {
        when(providerRepository.findByNpi("1234567890")).thenReturn(Optional.of(provider));

        Optional<Provider> result = providerService.findByNpi("1234567890");

        assertTrue(result.isPresent());
        assertEquals("Johnson", result.get().getLastName());
    }

    @Test
    void findByDepartment_shouldReturnProviders() {
        when(providerRepository.findByDepartment("Medicine")).thenReturn(List.of(provider));

        List<Provider> result = providerService.findByDepartment("Medicine");

        assertEquals(1, result.size());
    }

    @Test
    void findBySpecialty_shouldReturnProviders() {
        when(providerRepository.findBySpecialty("Internal Medicine")).thenReturn(List.of(provider));

        List<Provider> result = providerService.findBySpecialty("Internal Medicine");

        assertEquals(1, result.size());
    }

    @Test
    void getAllDepartments_shouldReturnDistinctDepartments() {
        when(providerRepository.findAllDepartments()).thenReturn(List.of("Medicine", "Surgery"));

        List<String> result = providerService.getAllDepartments();

        assertEquals(2, result.size());
        assertTrue(result.contains("Medicine"));
        assertTrue(result.contains("Surgery"));
    }

    @Test
    void getAllSpecialties_shouldReturnDistinctSpecialties() {
        when(providerRepository.findAllSpecialties())
                .thenReturn(List.of("Internal Medicine", "Cardiology"));

        List<String> result = providerService.getAllSpecialties();

        assertEquals(2, result.size());
    }

    @Test
    void save_shouldSaveAndReturnProvider() {
        when(providerRepository.save(provider)).thenReturn(provider);

        Provider result = providerService.save(provider);

        assertNotNull(result);
        assertEquals("Sarah", result.getFirstName());
        verify(providerRepository).save(provider);
    }

    @Test
    void deactivate_shouldSetActiveToFalse() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(provider)).thenReturn(provider);

        providerService.deactivate(1L);

        assertFalse(provider.getActive());
        verify(providerRepository).save(provider);
    }

    @Test
    void deactivate_shouldDoNothing_whenNotFound() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        providerService.deactivate(99L);

        verify(providerRepository, never()).save(any());
    }

    @Test
    void search_shouldReturnMatchingProviders() {
        when(providerRepository.findByLastNameContainingIgnoreCase("John"))
                .thenReturn(List.of(provider));

        List<Provider> result = providerService.search("John");

        assertEquals(1, result.size());
        assertEquals("Johnson", result.get(0).getLastName());
    }

    @Test
    void search_shouldReturnEmpty_whenNoMatch() {
        when(providerRepository.findByLastNameContainingIgnoreCase("XYZ"))
                .thenReturn(List.of());

        List<Provider> result = providerService.search("XYZ");

        assertTrue(result.isEmpty());
    }
}
