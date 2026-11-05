package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyPatientLookupTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;
    @InjectMocks
    private LegacyPatientLookup lookup;

    @Test
    @DisplayName("BR-LEG-03: MRN lookups bind the MRN as a parameter and return the patient")
    void brLeg03_mrnLookupBound() {
        Patient p = new Patient();
        when(entityManager.createNativeQuery("SELECT * FROM patients WHERE mrn = ?1", Patient.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(p);

        assertSame(p, lookup.findPatientByMrn("MRN-1' OR '1'='1"));
        verify(query).setParameter(1, "MRN-1' OR '1'='1");
    }

    @Test
    @DisplayName("BR-LEG-03: an unknown MRN or SSN returns null rather than throwing")
    void brLeg03_notFoundReturnsNull() {
        when(entityManager.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new NoResultException());
        assertNull(lookup.findPatientByMrn("MRN-404"));
        assertNull(lookup.findPatientBySsn("000-00-0000"));
    }

    @Test
    @DisplayName("BR-LEG-03: SSN lookups bind the SSN as a parameter")
    void brLeg03_ssnLookupBound() {
        Patient p = new Patient();
        when(entityManager.createNativeQuery("SELECT * FROM patients WHERE ssn = ?1", Patient.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(p);
        assertSame(p, lookup.findPatientBySsn("000-00-0000"));
        verify(query).setParameter(1, "000-00-0000");
    }

    @Test
    @DisplayName("BR-LEG-04: name and free-text searches are case-insensitive contains matches with bound parameters")
    void brLeg04_containsSearchBound() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("ILIKE ?1")), eq(Patient.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Patient()));
        assertEquals(1, lookup.findPatientsByLastName("test").size());
        verify(query).setParameter(1, "%test%");
    }

    @Test
    @DisplayName("BR-LEG-04: raw search matches first name, last name or MRN and excludes SSN from results")
    void brLeg04_rawSearch() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.contains("first_name ILIKE ?1 OR last_name ILIKE ?1 OR mrn ILIKE ?1")
                && !s.contains("ssn")))).thenReturn(query);
        Object[] row = {1L, "MRN-1", "Test", "Patient", null};
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));
        assertEquals(1, lookup.searchPatientsRaw("pat").size());
        verify(query).setParameter(1, "%pat%");
    }

    @Test
    @DisplayName("BR-LEG-05: demographics are returned keyed by field name for the requested patient id")
    void brLeg05_demographics() {
        when(entityManager.createNativeQuery(argThat((String s) -> s != null && s.endsWith("FROM patients WHERE id = ?1")))).thenReturn(query);
        Object[] row = {42L, "MRN-1", "000-00-0000", "Test", "Patient", "2000-01-01",
                "555-0100", "555-0101", "t@example.test", "1 Test St", "Testville", "NY", "00000"};
        when(query.getSingleResult()).thenReturn(row);

        Map<String, Object> d = lookup.getPatientDemographics(42L);

        verify(query).setParameter(1, 42L);
        assertEquals(42L, d.get("id"));
        assertEquals("MRN-1", d.get("mrn"));
        assertEquals("Test", d.get("firstName"));
        assertEquals("Patient", d.get("lastName"));
        assertEquals("555-0101", d.get("phoneMobile"));
        assertEquals("1 Test St", d.get("street"));
        assertEquals("00000", d.get("zipCode"));
        assertEquals(13, d.size());
        assertFalse(d.containsKey("password"));
    }
}
