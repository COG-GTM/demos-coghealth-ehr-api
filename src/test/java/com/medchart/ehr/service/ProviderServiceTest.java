package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderService")
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderService providerService;

    private Provider sampleProvider;

    @BeforeEach
    void setUp() {
        sampleProvider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("findAll should return all providers")
    void findAllShouldReturnAllProviders() {
        when(providerRepository.findAll()).thenReturn(List.of(sampleProvider));

        List<Provider> result = providerService.findAll();

        assertThat(result).hasSize(1);
        verify(providerRepository).findAll();
    }

    @Test
    @DisplayName("findActive should return active providers only")
    void findActiveShouldReturnActiveProviders() {
        when(providerRepository.findByActiveTrue()).thenReturn(List.of(sampleProvider));

        List<Provider> result = providerService.findActive();

        assertThat(result).hasSize(1);
        verify(providerRepository).findByActiveTrue();
    }

    @Test
    @DisplayName("findById should return provider when found")
    void findByIdShouldReturnProvider() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));

        Optional<Provider> result = providerService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getNpi()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("findByNpi should return provider when found")
    void findByNpiShouldReturnProvider() {
        when(providerRepository.findByNpi("1234567890")).thenReturn(Optional.of(sampleProvider));

        Optional<Provider> result = providerService.findByNpi("1234567890");

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByDepartment should return providers in department")
    void findByDepartmentShouldReturnProviders() {
        when(providerRepository.findByDepartment("Medicine")).thenReturn(List.of(sampleProvider));

        List<Provider> result = providerService.findByDepartment("Medicine");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findBySpecialty should return providers with specialty")
    void findBySpecialtyShouldReturnProviders() {
        when(providerRepository.findBySpecialty("Internal Medicine")).thenReturn(List.of(sampleProvider));

        List<Provider> result = providerService.findBySpecialty("Internal Medicine");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllDepartments should return distinct departments")
    void getAllDepartmentsShouldReturnDistinctDepartments() {
        when(providerRepository.findAllDepartments()).thenReturn(List.of("Medicine", "Surgery"));

        List<String> result = providerService.getAllDepartments();

        assertThat(result).containsExactly("Medicine", "Surgery");
    }

    @Test
    @DisplayName("getAllSpecialties should return distinct specialties")
    void getAllSpecialtiesShouldReturnDistinctSpecialties() {
        when(providerRepository.findAllSpecialties()).thenReturn(List.of("Internal Medicine", "Cardiology"));

        List<String> result = providerService.getAllSpecialties();

        assertThat(result).containsExactly("Internal Medicine", "Cardiology");
    }

    @Test
    @DisplayName("save should persist provider")
    void saveShouldPersistProvider() {
        when(providerRepository.save(sampleProvider)).thenReturn(sampleProvider);

        Provider result = providerService.save(sampleProvider);

        assertThat(result).isEqualTo(sampleProvider);
        verify(providerRepository).save(sampleProvider);
    }

    @Test
    @DisplayName("deactivate should set provider inactive")
    void deactivateShouldSetProviderInactive() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));

        providerService.deactivate(1L);

        assertThat(sampleProvider.getActive()).isFalse();
        verify(providerRepository).save(sampleProvider);
    }

    @Test
    @DisplayName("deactivate should do nothing when provider not found")
    void deactivateShouldDoNothingWhenNotFound() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        providerService.deactivate(999L);

        verify(providerRepository, never()).save(any());
    }

    @Test
    @DisplayName("search should find providers by last name")
    void searchShouldFindProvidersByLastName() {
        when(providerRepository.findByLastNameContainingIgnoreCase("Smith")).thenReturn(List.of(sampleProvider));

        List<Provider> result = providerService.search("Smith");

        assertThat(result).hasSize(1);
    }
}
