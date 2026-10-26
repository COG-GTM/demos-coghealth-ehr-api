package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceTest {

    private ProviderRepository repository;
    private ProviderService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProviderRepository.class);
        when(repository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ProviderService(repository);
    }

    private static ProviderDTO request(String npi) {
        return ProviderDTO.builder()
                .npi(npi)
                .firstName("Alice")
                .lastName("Chen")
                .providerType(ProviderType.PHYSICIAN)
                .email("alice.chen@example.org")
                .licenses(Set.of(License.builder().licenseNumber("MD-1").state("CA").build()))
                .build();
    }

    @Test
    void createAlwaysPersistsNewEntity() {
        when(repository.findByNpi("1234567890")).thenReturn(Optional.empty());

        service.create(request("1234567890"));

        ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
        verify(repository).save(captor.capture());
        Provider saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getVersion()).isNull();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getNpi()).isEqualTo("1234567890");
        assertThat(saved.getLicenses()).hasSize(1);
    }

    @Test
    void createRejectsDuplicateNpi() {
        when(repository.findByNpi("1234567890")).thenReturn(Optional.of(Provider.builder().id(4L).build()));

        assertThatThrownBy(() -> service.create(request("1234567890"))).isInstanceOf(DuplicateNpiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateCopiesOnlyWritableFieldsOntoLoadedEntity() {
        LocalDateTime created = LocalDateTime.of(2020, 1, 1, 0, 0);
        Provider existing = Provider.builder()
                .id(4L).version(7L).active(false).createdAt(created)
                .npi("1234567890").firstName("Old").lastName("Name")
                .providerType(ProviderType.PHYSICIAN)
                .licenses(new HashSet<>(Set.of(License.builder().licenseNumber("OLD").build())))
                .build();
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        when(repository.findByNpi("1234567890")).thenReturn(Optional.of(existing));

        Provider saved = service.update(4L, request("1234567890")).orElseThrow();

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(4L);
        assertThat(saved.getVersion()).isEqualTo(7L);
        assertThat(saved.getActive()).isFalse();
        assertThat(saved.getCreatedAt()).isEqualTo(created);
        assertThat(saved.getFirstName()).isEqualTo("Alice");
        assertThat(saved.getLicenses()).extracting(License::getLicenseNumber).containsExactly("MD-1");
    }

    @Test
    void updateRejectsNpiOwnedByAnotherProvider() {
        when(repository.findById(4L)).thenReturn(Optional.of(Provider.builder().id(4L).npi("1234567890").build()));
        when(repository.findByNpi("5555555555")).thenReturn(Optional.of(Provider.builder().id(5L).build()));

        assertThatThrownBy(() -> service.update(4L, request("5555555555"))).isInstanceOf(DuplicateNpiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateOfUnknownProviderIsEmpty() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.update(99L, request("1234567890"))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void deactivateReportsWhetherProviderExists() {
        Provider existing = Provider.builder().id(4L).build();
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.deactivate(4L)).isTrue();
        assertThat(existing.getActive()).isFalse();
        assertThat(service.deactivate(99L)).isFalse();
    }
}
