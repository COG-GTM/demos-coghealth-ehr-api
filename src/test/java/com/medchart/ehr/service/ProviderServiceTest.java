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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
                .lastName("Anderson")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .build();
    }

    @Test
    void findAll_returnsAllProviders() {
        when(providerRepository.findAll()).thenReturn(Collections.singletonList(provider));

        List<Provider> result = providerService.findAll();

        assertEquals(1, result.size());
        assertEquals("Sarah", result.get(0).getFirstName());
    }

    @Test
    void findActive_returnsOnlyActiveProviders() {
        when(providerRepository.findByActiveTrue()).thenReturn(Collections.singletonList(provider));

        List<Provider> result = providerService.findActive();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getActive());
    }

    @Test
    void findById_existingProvider_returnsProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        Optional<Provider> result = providerService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("1234567890", result.get().getNpi());
    }

    @Test
    void findById_nonExistingProvider_returnsEmpty() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Provider> result = providerService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByNpi_returnsMatchingProvider() {
        when(providerRepository.findByNpi("1234567890")).thenReturn(Optional.of(provider));

        Optional<Provider> result = providerService.findByNpi("1234567890");

        assertTrue(result.isPresent());
        assertEquals("Anderson", result.get().getLastName());
    }

    @Test
    void findByDepartment_returnsMatchingProviders() {
        when(providerRepository.findByDepartment("Medicine"))
                .thenReturn(Collections.singletonList(provider));

        List<Provider> result = providerService.findByDepartment("Medicine");

        assertEquals(1, result.size());
    }

    @Test
    void findBySpecialty_returnsMatchingProviders() {
        when(providerRepository.findBySpecialty("Internal Medicine"))
                .thenReturn(Collections.singletonList(provider));

        List<Provider> result = providerService.findBySpecialty("Internal Medicine");

        assertEquals(1, result.size());
    }

    @Test
    void getAllDepartments_returnsDepartmentList() {
        when(providerRepository.findAllDepartments())
                .thenReturn(Arrays.asList("Medicine", "Surgery", "Pediatrics"));

        List<String> result = providerService.getAllDepartments();

        assertEquals(3, result.size());
        assertTrue(result.contains("Medicine"));
    }

    @Test
    void getAllSpecialties_returnsSpecialtyList() {
        when(providerRepository.findAllSpecialties())
                .thenReturn(Arrays.asList("Internal Medicine", "Cardiology"));

        List<String> result = providerService.getAllSpecialties();

        assertEquals(2, result.size());
    }

    @Test
    void save_savesAndReturnsProvider() {
        when(providerRepository.save(provider)).thenReturn(provider);

        Provider result = providerService.save(provider);

        assertEquals(provider, result);
        verify(providerRepository).save(provider);
    }

    @Test
    void deactivate_existingProvider_setsActiveFalse() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        providerService.deactivate(1L);

        assertFalse(provider.getActive());
        verify(providerRepository).save(provider);
    }

    @Test
    void deactivate_nonExistingProvider_doesNothing() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        providerService.deactivate(999L);

        verify(providerRepository, never()).save(any());
    }

    @Test
    void search_delegatesToRepository() {
        when(providerRepository.findByLastNameContainingIgnoreCase("Anderson"))
                .thenReturn(Collections.singletonList(provider));

        List<Provider> result = providerService.search("Anderson");

        assertEquals(1, result.size());
        assertEquals("Anderson", result.get(0).getLastName());
    }
}
