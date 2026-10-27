package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        providerService = new ProviderService(providerRepository);
        ReflectionTestUtils.setField(providerService, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString(), eq(Provider.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
    }

    @Test
    void searchBindsInjectionPayloadAsParameter() {
        List<Provider> result = providerService.search("x' OR '1'='1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture(), eq(Provider.class));
        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("x' OR"));
        assertFalse(sql.contains("'1'='1"));
        assertTrue(sql.contains("?1"));
        verify(query).setParameter(1, "%x' OR '1'='1%");
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void searchEscapesLikeWildcards() {
        providerService.search("50%_a\\b");

        verify(query).setParameter(1, "%50\\%\\_a\\\\b%");
    }

    @Test
    void searchWrapsPlainNameForSubstringMatch() {
        providerService.search("Smith");

        verify(query).setParameter(1, "%Smith%");
    }
}
