package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.dto.LicenseDTO;
import com.medchart.ehr.dto.ProviderCreateRequest;
import com.medchart.ehr.dto.ProviderResponse;
import com.medchart.ehr.dto.ProviderUpdateRequest;
import com.medchart.ehr.mapper.ProviderMapper;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderServiceTest {

    private ProviderRepository repository;
    private ProviderService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProviderRepository.class);
        service = new ProviderService(repository, new ProviderMapper());
        when(repository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Provider alice() {
        Set<License> licenses = new HashSet<>();
        licenses.add(License.builder().licenseType("MD").licenseNumber("A123").state("CA").status("ACTIVE").build());
        return Provider.builder()
                .id(12L).version(7L).npi("1234567890")
                .firstName("Alice").lastName("Nguyen").credentials("MD")
                .providerType(ProviderType.PHYSICIAN)
                .department("CARDIOLOGY").specialty("Cardiology")
                .email("alice@mercyvalley.example").pager("555-0100").phoneMobile("555-0101")
                .licenses(licenses).active(true).acceptingPatients(true)
                .build();
    }

    @Test
    void updateMergesOnlyProvidedFieldsOntoLoadedEntity() {
        Provider stored = alice();
        when(repository.findById(12L)).thenReturn(Optional.of(stored));

        Optional<ProviderResponse> result = service.update(12L,
                ProviderUpdateRequest.builder().credentials("MD, FACC").build());

        assertThat(result).isPresent();
        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(repository).save(saved.capture());
        Provider p = saved.getValue();
        assertThat(p).isSameAs(stored);
        assertThat(p.getId()).isEqualTo(12L);
        assertThat(p.getVersion()).isEqualTo(7L);
        assertThat(p.getCredentials()).isEqualTo("MD, FACC");
        assertThat(p.getNpi()).isEqualTo("1234567890");
        assertThat(p.getEmail()).isEqualTo("alice@mercyvalley.example");
        assertThat(p.getPager()).isEqualTo("555-0100");
        assertThat(p.getDepartment()).isEqualTo("CARDIOLOGY");
        assertThat(p.getLicenses()).hasSize(1);
        assertThat(p.getActive()).isTrue();
    }

    @Test
    void updateReplacesLicensesOnlyWhenProvided() {
        Provider stored = alice();
        when(repository.findById(12L)).thenReturn(Optional.of(stored));

        service.update(12L, ProviderUpdateRequest.builder()
                .licenses(List.of(LicenseDTO.builder().licenseType("DEA").licenseNumber("X9").state("CA")
                        .expirationDate(LocalDate.of(2028, 1, 1)).build()))
                .build());

        assertThat(stored.getLicenses()).extracting(License::getLicenseType).containsExactly("DEA");
    }

    @Test
    void updateRejectsNpiOwnedByAnotherProvider() {
        when(repository.findById(12L)).thenReturn(Optional.of(alice()));
        when(repository.findByNpi("9999999999"))
                .thenReturn(Optional.of(Provider.builder().id(13L).npi("9999999999").build()));

        assertThatThrownBy(() -> service.update(12L, ProviderUpdateRequest.builder().npi("9999999999").build()))
                .isInstanceOf(DuplicateNpiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateOfMissingProviderReturnsEmpty() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.update(99L, ProviderUpdateRequest.builder().npi("0000000000").build())).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void createBuildsNewEntityWithServerManagedDefaults() {
        when(repository.findByNpi("1112223334")).thenReturn(Optional.empty());

        service.create(ProviderCreateRequest.builder()
                .npi("1112223334").firstName("Bob").lastName("Lee").providerType(ProviderType.PHARMACIST)
                .build());

        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(repository).save(saved.capture());
        Provider p = saved.getValue();
        assertThat(p.getId()).isNull();
        assertThat(p.getVersion()).isNull();
        assertThat(p.getActive()).isTrue();
        assertThat(p.getAcceptingPatients()).isTrue();
        assertThat(p.getTerminationDate()).isNull();
    }

    @Test
    void createRejectsDuplicateNpi() {
        when(repository.findByNpi("1234567890")).thenReturn(Optional.of(alice()));

        assertThatThrownBy(() -> service.create(ProviderCreateRequest.builder()
                .npi("1234567890").firstName("Mallory").lastName("X").providerType(ProviderType.PHYSICIAN)
                .build()))
                .isInstanceOf(DuplicateNpiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void readsReturnResponseWithoutInternalColumns() {
        when(repository.findById(12L)).thenReturn(Optional.of(alice()));

        ProviderResponse response = service.findById(12L).orElseThrow();

        assertThat(response.getNpi()).isEqualTo("1234567890");
        assertThat(response.getFullName()).isEqualTo("Alice Nguyen, MD");
        assertThat(response.getLicenses()).hasSize(1);
    }
}
