package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderService providerService;

    @Test
    void searchDelegatesRawLastNameToDerivedRepositoryMethod() {
        String lastName = "Smith";
        List<Provider> providers = Collections.singletonList(new Provider());
        when(providerRepository.findByActiveTrueAndLastNameContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(lastName))
                .thenReturn(providers);

        List<Provider> result = providerService.search(lastName);

        verify(providerRepository)
                .findByActiveTrueAndLastNameContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(lastName);
        assertSame(providers, result);
    }

    @Test
    void searchPassesSqlInjectionShapedInputAsPlainParameter() {
        String lastName = "x' OR '1'='1";
        List<Provider> providers = Collections.emptyList();
        when(providerRepository.findByActiveTrueAndLastNameContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(lastName))
                .thenReturn(providers);

        List<Provider> result = providerService.search(lastName);

        verify(providerRepository)
                .findByActiveTrueAndLastNameContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(lastName);
        assertSame(providers, result);
    }
}
