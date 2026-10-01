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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderService providerService;

    private Provider provider1;
    private Provider provider2;

    @BeforeEach
    void setUp() {
        provider1 = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .acceptingPatients(true)
                .build();

        provider2 = Provider.builder()
                .id(2L)
                .npi("0987654321")
                .firstName("Bob")
                .lastName("Jones")
                .providerType(ProviderType.NURSE_PRACTITIONER)
                .specialty("Family Medicine")
                .department("Primary Care")
                .active(true)
                .acceptingPatients(true)
                .build();
    }

    @Test
    void findAll_returnsAllProviders() {
        when(providerRepository.findAll()).thenReturn(Arrays.asList(provider1, provider2));

        List<Provider> result = providerService.findAll();

        assertEquals(2, result.size());
        verify(providerRepository).findAll();
    }

    @Test
    void findAll_noProviders_returnsEmptyList() {
        when(providerRepository.findAll()).thenReturn(Collections.emptyList());

        List<Provider> result = providerService.findAll();

        assertTrue(result.isEmpty());
    }

    @Test
    void findActive_returnsOnlyActiveProviders() {
        when(providerRepository.findByActiveTrue()).thenReturn(Arrays.asList(provider1, provider2));

        List<Provider> result = providerService.findActive();

        assertEquals(2, result.size());
        verify(providerRepository).findByActiveTrue();
    }

    @Test
    void findById_existingId_returnsProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider1));

        Optional<Provider> result = providerService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("Alice", result.get().getFirstName());
        assertEquals("1234567890", result.get().getNpi());
    }

    @Test
    void findById_nonExistingId_returnsEmpty() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Provider> result = providerService.findById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findByNpi_existingNpi_returnsProvider() {
        when(providerRepository.findByNpi("1234567890")).thenReturn(Optional.of(provider1));

        Optional<Provider> result = providerService.findByNpi("1234567890");

        assertTrue(result.isPresent());
        assertEquals("Alice", result.get().getFirstName());
    }

    @Test
    void findByNpi_nonExistingNpi_returnsEmpty() {
        when(providerRepository.findByNpi("0000000000")).thenReturn(Optional.empty());

        Optional<Provider> result = providerService.findByNpi("0000000000");

        assertFalse(result.isPresent());
    }

    @Test
    void findByDepartment_returnsProviders() {
        when(providerRepository.findByDepartment("Medicine")).thenReturn(Collections.singletonList(provider1));

        List<Provider> result = providerService.findByDepartment("Medicine");

        assertEquals(1, result.size());
        assertEquals("Medicine", result.get(0).getDepartment());
    }

    @Test
    void findBySpecialty_returnsProviders() {
        when(providerRepository.findBySpecialty("Internal Medicine")).thenReturn(Collections.singletonList(provider1));

        List<Provider> result = providerService.findBySpecialty("Internal Medicine");

        assertEquals(1, result.size());
        assertEquals("Internal Medicine", result.get(0).getSpecialty());
    }

    @Test
    void getAllDepartments_returnsDepartmentList() {
        when(providerRepository.findAllDepartments()).thenReturn(Arrays.asList("Medicine", "Primary Care", "Surgery"));

        List<String> result = providerService.getAllDepartments();

        assertEquals(3, result.size());
        assertTrue(result.contains("Medicine"));
    }

    @Test
    void getAllSpecialties_returnsSpecialtyList() {
        when(providerRepository.findAllSpecialties()).thenReturn(Arrays.asList("Internal Medicine", "Family Medicine"));

        List<String> result = providerService.getAllSpecialties();

        assertEquals(2, result.size());
    }

    @Test
    void save_newProvider_returnsSavedProvider() {
        when(providerRepository.save(provider1)).thenReturn(provider1);

        Provider result = providerService.save(provider1);

        assertNotNull(result);
        assertEquals("Alice", result.getFirstName());
        verify(providerRepository).save(provider1);
    }

    @Test
    void deactivate_existingProvider_setsInactive() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider1));
        when(providerRepository.save(any(Provider.class))).thenReturn(provider1);

        providerService.deactivate(1L);

        assertFalse(provider1.getActive());
        verify(providerRepository).save(provider1);
    }

    @Test
    void deactivate_nonExistingProvider_doesNothing() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        providerService.deactivate(999L);

        verify(providerRepository, never()).save(any());
    }

    @Test
    void search_returnsMatchingProviders() {
        when(providerRepository.findByLastNameContainingIgnoreCase("Smith")).thenReturn(Collections.singletonList(provider1));

        List<Provider> result = providerService.search("Smith");

        assertEquals(1, result.size());
        assertEquals("Smith", result.get(0).getLastName());
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        when(providerRepository.findByLastNameContainingIgnoreCase("ZZZ")).thenReturn(Collections.emptyList());

        List<Provider> result = providerService.search("ZZZ");

        assertTrue(result.isEmpty());
    }
}
