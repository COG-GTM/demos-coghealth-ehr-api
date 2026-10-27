package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceTest {

    private final ProviderRepository repository = mock(ProviderRepository.class);
    private final ProviderService service = new ProviderService(repository);

    private Provider stored() {
        Provider p = new Provider();
        p.setId(4L);
        p.setVersion(8L);
        p.setActive(true);
        p.setFirstName("Alice");
        p.setLastName("Chen");
        p.setNpi("1111111111");
        p.setDepartment("Surgery");
        p.setPhoneMobile("555-0199");
        p.getLicenses().add(License.builder().licenseType("MD").licenseNumber("A123456").state("CA").build());
        return p;
    }

    private ProviderDTO dto(Long version) {
        ProviderDTO dto = new ProviderDTO();
        dto.setNpi("1234567890");
        dto.setFirstName("Alicia");
        dto.setLastName("Chen");
        dto.setProviderType(ProviderType.PHYSICIAN);
        dto.setExpectedVersion(version);
        return dto;
    }

    @Test
    void staleVersionIsRejected() {
        when(repository.findById(4L)).thenReturn(Optional.of(stored()));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.update(4L, dto(7L)));
        verify(repository, never()).save(any());
    }

    @Test
    void matchingOrAbsentVersionUpdatesWithoutTouchingServerFields() {
        Provider existing = stored();
        when(repository.findById(4L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Provider saved = service.update(4L, dto(8L)).orElseThrow();
        assertEquals("Alicia", saved.getFirstName());
        assertEquals(4L, saved.getId());
        assertEquals(8L, saved.getVersion());
        assertEquals(Boolean.TRUE, saved.getActive());

        service.update(4L, dto(null)).orElseThrow();
    }

    @Test
    void updateNeverChangesNpiOrVersion() {
        when(repository.findById(4L)).thenReturn(Optional.of(stored()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Provider saved = service.update(4L, dto(8L)).orElseThrow();
        assertEquals("1111111111", saved.getNpi());
        assertEquals(8L, saved.getVersion());
    }

    @Test
    void omittedFieldsKeepStoredValues() {
        when(repository.findById(4L)).thenReturn(Optional.of(stored()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderDTO partial = new ProviderDTO();
        partial.setDepartment("Cardiology");
        Provider saved = service.update(4L, partial).orElseThrow();

        assertEquals("Cardiology", saved.getDepartment());
        assertEquals("Alice", saved.getFirstName());
        assertEquals("Chen", saved.getLastName());
        assertEquals("555-0199", saved.getPhoneMobile());
        assertEquals(1, saved.getLicenses().size());
    }

    @Test
    void deactivateReportsUnknownProvider() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.deactivate(99L));
        verify(repository, never()).save(any());
    }
}
