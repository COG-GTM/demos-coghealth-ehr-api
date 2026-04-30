package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService patientService;

    private Patient samplePatient;
    private PatientDTO sampleDTO;

    @BeforeEach
    void setUp() {
        samplePatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .active(true)
                .deceased(false)
                .build();

        sampleDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Test
    void getPatientById_shouldReturnPatientDTO() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientMapper.toDto(samplePatient)).thenReturn(sampleDTO);

        PatientDTO result = patientService.getPatientById(1L);

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        assertEquals("John", result.getFirstName());
        verify(patientRepository).findById(1L);
    }

    @Test
    void getPatientById_shouldThrowWhenNotFound() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientById(999L));

        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    void getPatientByMrn_shouldReturnPatientDTO() {
        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(samplePatient));
        when(patientMapper.toDto(samplePatient)).thenReturn(sampleDTO);

        PatientDTO result = patientService.getPatientByMrn("MRN001");

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        verify(patientRepository).findByMrn("MRN001");
    }

    @Test
    void getPatientByMrn_shouldThrowWhenNotFound() {
        when(patientRepository.findByMrn("INVALID")).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientByMrn("INVALID"));

        assertTrue(ex.getMessage().contains("INVALID"));
    }

    @Test
    void searchPatients_shouldReturnPageOfDTOs() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Patient> patientPage = new PageImpl<>(Collections.singletonList(samplePatient));
        when(patientRepository.searchPatients("Doe", pageable)).thenReturn(patientPage);
        when(patientMapper.toDto(samplePatient)).thenReturn(sampleDTO);

        Page<PatientDTO> result = patientService.searchPatients("Doe", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("John", result.getContent().get(0).getFirstName());
    }

    @Test
    void searchPatients_shouldReturnEmptyPageForNoResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Patient> emptyPage = new PageImpl<>(Collections.emptyList());
        when(patientRepository.searchPatients("ZZZZZ", pageable)).thenReturn(emptyPage);

        Page<PatientDTO> result = patientService.searchPatients("ZZZZZ", pageable);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void createPatient_shouldSaveAndReturnDTO() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();
        Patient inputEntity = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();
        Patient savedEntity = Patient.builder()
                .id(2L)
                .mrn("MRN" + System.currentTimeMillis())
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();
        PatientDTO savedDTO = PatientDTO.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(inputEntity);
        when(patientRepository.save(inputEntity)).thenReturn(savedEntity);
        when(patientMapper.toDto(savedEntity)).thenReturn(savedDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        verify(patientRepository).save(inputEntity);
    }

    @Test
    void createPatient_shouldGenerateMrnWhenNotProvided() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();
        Patient entity = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(entity);
        when(patientMapper.toDto(entity)).thenReturn(sampleDTO);

        patientService.createPatient(inputDTO);

        assertNotNull(entity.getMrn());
        assertTrue(entity.getMrn().startsWith("MRN"));
    }

    @Test
    void createPatient_shouldRejectDuplicateMrn() {
        PatientDTO inputDTO = PatientDTO.builder()
                .mrn("MRN001")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .build();

        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(samplePatient));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> patientService.createPatient(inputDTO));

        assertTrue(ex.getMessage().contains("MRN001"));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void updatePatient_shouldMergeAndSave() {
        PatientDTO updates = PatientDTO.builder()
                .firstName("Jonathan")
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
        when(patientRepository.save(samplePatient)).thenReturn(samplePatient);
        when(patientMapper.toDto(samplePatient)).thenReturn(sampleDTO);

        PatientDTO result = patientService.updatePatient(1L, updates);

        assertNotNull(result);
        verify(patientMapper).updateEntityFromDto(updates, samplePatient);
        verify(patientRepository).save(samplePatient);
    }

    @Test
    void updatePatient_shouldThrowWhenNotFound() {
        PatientDTO updates = PatientDTO.builder().firstName("Jonathan").build();
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.updatePatient(999L, updates));

        verify(patientRepository, never()).save(any());
    }

    @Test
    void findBySsn_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> patientService.findBySsn("123-45-6789"));
    }

    @Test
    void findBySsn_errorMessageShouldMentionHIPAA() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> patientService.findBySsn("123-45-6789"));

        assertTrue(ex.getMessage().contains("HIPAA"));
    }
}
