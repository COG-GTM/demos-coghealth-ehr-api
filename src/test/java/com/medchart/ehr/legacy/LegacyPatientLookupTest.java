package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.patient.Patient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyPatientLookupTest {

    private EntityManager entityManager;
    private Query query;
    private SimpleMeterRegistry meterRegistry;
    private LegacyPatientLookup lookup;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        meterRegistry = new SimpleMeterRegistry();
        when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        lookup = new LegacyPatientLookup(entityManager, meterRegistry);
    }

    @Test
    void mrnLookupReturnsNullWhenNoPatientMatches() {
        when(query.getSingleResult()).thenThrow(new NoResultException());

        assertNull(lookup.findPatientByMrn("MRN-1"));
        assertEquals(1.0, counter("mrn", "not_found"));
    }

    @Test
    void mrnLookupSurfacesInfrastructureFailures() {
        when(query.getSingleResult()).thenThrow(new PersistenceException("connection refused"));

        assertThrows(LegacyLookupException.class, () -> lookup.findPatientByMrn("MRN-1"));
        assertEquals(1.0, counter("mrn", "error"));
    }

    @Test
    void ssnLookupSurfacesInfrastructureFailures() {
        when(query.getSingleResult()).thenThrow(new PersistenceException("connection refused"));

        assertThrows(LegacyLookupException.class, () -> lookup.findPatientBySsn("123-45-6789"));
        assertEquals(1.0, counter("ssn", "error"));
    }

    @Test
    void ssnLookupReturnsPatientOnMatch() {
        Patient patient = new Patient();
        when(query.getSingleResult()).thenReturn(patient);

        assertEquals(patient, lookup.findPatientBySsn("123-45-6789"));
        assertEquals(1.0, counter("ssn", "found"));
    }

    private double counter(String lookupType, String outcome) {
        return meterRegistry.counter("legacy.patient.lookup", "lookup", lookupType, "outcome", outcome).count();
    }
}
