package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.Query;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    private static final String FAKE_SSN = "000-00-0000";

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query countQuery;
    @Mock
    private Query listQuery;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(patientRepository, patientMapper);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private static Patient patient(String mrn) {
        Patient p = new Patient();
        p.setId(1L);
        p.setMrn(mrn);
        p.setSsn(FAKE_SSN);
        p.setFirstName("Test");
        p.setLastName("Patient");
        return p;
    }

    private static AuditAccess audit(String name, Class<?>... types) throws Exception {
        Method m = PatientService.class.getMethod(name, types);
        return m.getAnnotation(AuditAccess.class);
    }

    @Test
    @DisplayName("BR-SVC-01: every patient read/search/create/update is annotated for HIPAA audit")
    void brSvc01_patientAccessMethodsAreAudited() throws Exception {
        assertEquals(AuditAction.READ, audit("getPatientById", Long.class).action());
        assertEquals(AuditAction.READ, audit("getPatientByMrn", String.class).action());
        assertEquals(AuditAction.SEARCH, audit("searchPatients", String.class, String.class, Pageable.class).action());
        assertEquals(AuditAction.CREATE, audit("createPatient", PatientDTO.class).action());
        assertEquals(AuditAction.UPDATE, audit("updatePatient", Long.class, PatientDTO.class).action());
        assertEquals("Patient", audit("getPatientById", Long.class).resourceType());
    }

    @Test
    @DisplayName("BR-SVC-01: patient lookup by id and MRN returns the mapped DTO")
    void brSvc01_lookupReturnsMappedDto() {
        Patient p = patient("MRN-T1");
        PatientDTO dto = new PatientDTO();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(p));
        when(patientRepository.findByMrn("MRN-T1")).thenReturn(Optional.of(p));
        when(patientMapper.toDto(p)).thenReturn(dto);

        assertSame(dto, service.getPatientById(1L));
        assertSame(dto, service.getPatientByMrn("MRN-T1"));
    }

    @Test
    @DisplayName("BR-SVC-02: missing patient errors identify the record by id/MRN only, never by SSN")
    void brSvc02_notFoundMessagesDoNotContainSsn() {
        when(patientRepository.findById(9L)).thenReturn(Optional.empty());
        when(patientRepository.findByMrn("MRN-404")).thenReturn(Optional.empty());

        EntityNotFoundException byId = assertThrows(EntityNotFoundException.class, () -> service.getPatientById(9L));
        EntityNotFoundException byMrn = assertThrows(EntityNotFoundException.class, () -> service.getPatientByMrn("MRN-404"));

        assertEquals("Patient not found with id: 9", byId.getMessage());
        assertEquals("Patient not found with MRN: MRN-404", byMrn.getMessage());
        assertFalse(byMrn.getMessage().contains(FAKE_SSN));
    }

    @Test
    @DisplayName("BR-SVC-03: SSN-based patient lookup is disabled for HIPAA compliance")
    @SuppressWarnings("deprecation")
    void brSvc03_ssnLookupDisabled() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> service.findBySsn(FAKE_SSN));
        assertTrue(e.getMessage().contains("HIPAA"));
        assertFalse(e.getMessage().contains(FAKE_SSN));
        verifyNoInteractions(patientRepository);
    }

    @Test
    @DisplayName("BR-SVC-04: search without sort uses the repository's parameterised search")
    void brSvc04_searchWithoutSortDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Patient p = patient("MRN-T2");
        PatientDTO dto = new PatientDTO();
        when(patientRepository.searchPatients("smi", pageable)).thenReturn(new PageImpl<>(List.of(p), pageable, 1));
        when(patientMapper.toDto(p)).thenReturn(dto);

        Page<PatientDTO> page = service.searchPatients("smi", "  ", pageable);
        assertEquals(1, page.getTotalElements());
        assertSame(dto, page.getContent().get(0));

        when(patientRepository.searchPatients("smi", pageable)).thenReturn(Page.empty(pageable));
        assertEquals(0, service.searchPatients("smi", null, pageable).getTotalElements());
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("BR-SVC-05: sorted search binds the term as a parameter and orders by a whitelisted column")
    void brSvc05_sortedSearchUsesWhitelistedColumn() {
        Pageable pageable = PageRequest.of(2, 5);
        Patient p = patient("MRN-T3");
        PatientDTO dto = new PatientDTO();
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(11L);
        when(entityManager.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(listQuery);
        when(listQuery.getResultList()).thenReturn(List.of(p));
        when(patientMapper.toDto(p)).thenReturn(dto);

        Page<PatientDTO> page = service.searchPatients("doe", "lastName,desc", pageable);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture(), eq(Patient.class));
        assertTrue(sql.getValue().endsWith(" ORDER BY last_name DESC"));
        assertFalse(sql.getValue().contains("doe"));
        verify(countQuery).setParameter(1, "%doe%");
        verify(listQuery).setParameter(1, "%doe%");
        verify(listQuery).setFirstResult(10);
        verify(listQuery).setMaxResults(5);
        assertEquals(11, page.getTotalElements());
        assertSame(dto, page.getContent().get(0));
    }

    @Test
    @DisplayName("BR-SVC-05: sort accepts API field names, column names and ASC/DESC only")
    void brSvc05_sortWhitelistAcceptsKnownForms() {
        assertEquals("last_name ASC", PatientService.toOrderBy("lastName"));
        assertEquals("first_name DESC", PatientService.toOrderBy("firstName desc"));
        assertEquals("date_of_birth ASC", PatientService.toOrderBy("DATE_OF_BIRTH, asc"));
        assertEquals("mrn DESC", PatientService.toOrderBy(" mrn,DESC "));
    }

    @Test
    @DisplayName("BR-SVC-05: SQL fragments or unknown fields in sort are rejected before any query runs")
    void brSvc05_sortInjectionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.searchPatients("x", "ssn", PageRequest.of(0, 5)));
        assertThrows(IllegalArgumentException.class, () -> PatientService.toOrderBy("(SELECT ssn FROM patients)"));
        assertThrows(IllegalArgumentException.class, () -> PatientService.toOrderBy("last_name; DROP TABLE patients"));
        assertThrows(IllegalArgumentException.class, () -> PatientService.toOrderBy("last_name sideways"));
        assertThrows(IllegalArgumentException.class, () -> PatientService.toOrderBy("last_name asc nulls"));
        verify(entityManager, never()).createNativeQuery(anyString(), eq(Patient.class));
    }

    @Test
    @DisplayName("BR-SVC-06: a patient cannot be created with an MRN that already exists")
    void brSvc06_duplicateMrnRejected() {
        PatientDTO dto = PatientDTO.builder().mrn("MRN-DUP").build();
        when(patientRepository.findByMrn("MRN-DUP")).thenReturn(Optional.of(patient("MRN-DUP")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createPatient(dto));
        assertEquals("Patient with MRN MRN-DUP already exists", e.getMessage());
        verify(patientRepository, never()).save(any());
    }

    @Test
    @DisplayName("BR-SVC-06: a patient with a new MRN is saved with that MRN")
    void brSvc06_newMrnKept() {
        PatientDTO dto = PatientDTO.builder().mrn("MRN-NEW").build();
        Patient entity = patient("MRN-NEW");
        PatientDTO out = new PatientDTO();
        when(patientRepository.findByMrn("MRN-NEW")).thenReturn(Optional.empty());
        when(patientMapper.toEntity(dto)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(entity);
        when(patientMapper.toDto(entity)).thenReturn(out);

        assertSame(out, service.createPatient(dto));
        assertEquals("MRN-NEW", entity.getMrn());
    }

    @Test
    @DisplayName("BR-SVC-07: an MRN is generated (MRN + timestamp) when none is supplied")
    void brSvc07_mrnGeneratedWhenMissing() {
        PatientDTO dto = new PatientDTO();
        Patient entity = patient(null);
        when(patientMapper.toEntity(dto)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(entity);
        when(patientMapper.toDto(entity)).thenReturn(dto);

        service.createPatient(dto);

        assertNotNull(entity.getMrn());
        assertTrue(entity.getMrn().matches("MRN\\d{13,}"), entity.getMrn());
        verify(patientRepository, never()).findByMrn(any());
    }

    @Test
    @DisplayName("BR-SVC-08: updating applies the DTO onto the existing record and saves it")
    void brSvc08_updateAppliesDto() {
        Patient existing = patient("MRN-U1");
        PatientDTO dto = PatientDTO.builder().firstName("New").build();
        PatientDTO out = new PatientDTO();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(existing)).thenReturn(existing);
        when(patientMapper.toDto(existing)).thenReturn(out);

        assertSame(out, service.updatePatient(1L, dto));
        verify(patientMapper).updateEntityFromDto(dto, existing);
    }

    @Test
    @DisplayName("BR-SVC-08: updating a non-existent patient fails without saving")
    void brSvc08_updateMissingPatientFails() {
        when(patientRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.updatePatient(2L, new PatientDTO()));
        verify(patientRepository, never()).save(any());
    }
}
