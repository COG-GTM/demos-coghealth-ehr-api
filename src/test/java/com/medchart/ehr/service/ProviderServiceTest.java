package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository repo;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService(repo);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    @DisplayName("BR-SVC-21: deactivating a provider is a soft delete (active=false), not a removal")
    void brSvc21_deactivateSoftDeletes() {
        Provider p = new Provider();
        p.setActive(true);
        p.setNpi("0000000000");
        when(repo.findById(1L)).thenReturn(Optional.of(p));

        service.deactivate(1L);

        assertFalse(p.getActive());
        verify(repo).save(p);
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("BR-SVC-21: deactivating an unknown provider is a no-op")
    void brSvc21_deactivateUnknown() {
        when(repo.findById(2L)).thenReturn(Optional.empty());
        service.deactivate(2L);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("BR-SVC-21: provider name search binds the user input as a parameter, never into SQL")
    void brSvc21_searchBindsParameter() {
        when(entityManager.createNativeQuery(any(String.class), eq(Provider.class))).thenReturn(query);
        Provider p = new Provider();
        when(query.getResultList()).thenReturn(List.of(p));

        String hostile = "x' OR '1'='1";
        assertEquals(List.of(p), service.search(hostile));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture(), eq(Provider.class));
        assertFalse(sql.getValue().contains(hostile));
        verify(query).setParameter("pattern", "%" + hostile + "%");
    }

    @Test
    @DisplayName("BR-SVC-23: provider lookups and saves delegate to the repository unchanged")
    void brSvc23_providerDelegation() {
        Provider p = new Provider();
        when(repo.findAll()).thenReturn(List.of(p));
        when(repo.findByActiveTrue()).thenReturn(List.of(p));
        when(repo.findById(1L)).thenReturn(Optional.of(p));
        when(repo.findByNpi("0000000000")).thenReturn(Optional.of(p));
        when(repo.findByDepartment("Cardiology")).thenReturn(List.of(p));
        when(repo.findBySpecialty("Surgery")).thenReturn(List.of(p));
        when(repo.findAllDepartments()).thenReturn(List.of("Cardiology"));
        when(repo.findAllSpecialties()).thenReturn(List.of("Surgery"));
        when(repo.save(p)).thenReturn(p);

        assertEquals(1, service.findAll().size());
        assertEquals(1, service.findActive().size());
        assertSame(p, service.findById(1L).get());
        assertSame(p, service.findByNpi("0000000000").get());
        assertEquals(1, service.findByDepartment("Cardiology").size());
        assertEquals(1, service.findBySpecialty("Surgery").size());
        assertEquals(List.of("Cardiology"), service.getAllDepartments());
        assertEquals(List.of("Surgery"), service.getAllSpecialties());
        assertSame(p, service.save(p));
    }
}
