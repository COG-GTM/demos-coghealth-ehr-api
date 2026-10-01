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

    private Patient patient;
    private PatientDTO patientDTO;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();

        patientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Test
    void getPatientById_existingId_returnsPatientDTO() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientById(1L);

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        verify(patientRepository).findById(1L);
        verify(patientMapper).toDto(patient);
    }

    @Test
    void getPatientById_nonExistingId_throwsEntityNotFoundException() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> patientService.getPatientById(999L)
        );
        assertTrue(exception.getMessage().contains("999"));
    }

    @Test
    void getPatientByMrn_existingMrn_returnsPatientDTO() {
        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientByMrn("MRN001");

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        verify(patientRepository).findByMrn("MRN001");
    }

    @Test
    void getPatientByMrn_nonExistingMrn_throwsEntityNotFoundException() {
        when(patientRepository.findByMrn("INVALID")).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> patientService.getPatientByMrn("INVALID")
        );
    }

    @Test
    void searchPatients_returnsPageOfPatientDTOs() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Patient> patientPage = new PageImpl<>(Collections.singletonList(patient));
        when(patientRepository.searchPatients("Doe", pageable)).thenReturn(patientPage);
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        Page<PatientDTO> result = patientService.searchPatients("Doe", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Doe", result.getContent().get(0).getLastName());
    }

    @Test
    void searchPatients_emptyResults_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Patient> emptyPage = new PageImpl<>(Collections.emptyList());
        when(patientRepository.searchPatients("ZZZ", pageable)).thenReturn(emptyPage);

        Page<PatientDTO> result = patientService.searchPatients("ZZZ", pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void createPatient_newPatient_returnsCreatedDTO() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .build();

        Patient newPatient = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .build();

        Patient savedPatient = Patient.builder()
                .id(2L)
                .mrn("MRN123")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .build();

        PatientDTO savedDTO = PatientDTO.builder()
                .id(2L)
                .mrn("MRN123")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
        when(patientMapper.toDto(savedPatient)).thenReturn(savedDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void createPatient_duplicateMrn_throwsIllegalArgumentException() {
        PatientDTO inputDTO = PatientDTO.builder()
                .mrn("MRN001")
                .firstName("Duplicate")
                .lastName("Patient")
                .build();

        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));

        assertThrows(
                IllegalArgumentException.class,
                () -> patientService.createPatient(inputDTO)
        );
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatient_noMrn_generatesOne() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("NoMrn")
                .lastName("Patient")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .build();

        Patient newPatient = Patient.builder()
                .firstName("NoMrn")
                .lastName("Patient")
                .build();

        Patient savedPatient = Patient.builder()
                .id(3L)
                .mrn("MRN_GENERATED")
                .firstName("NoMrn")
                .lastName("Patient")
                .build();

        PatientDTO savedDTO = PatientDTO.builder()
                .id(3L)
                .mrn("MRN_GENERATED")
                .firstName("NoMrn")
                .lastName("Patient")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
        when(patientMapper.toDto(savedPatient)).thenReturn(savedDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertNotNull(result);
        verify(patientRepository).save(argThat(p -> p.getMrn() != null));
    }

    @Test
    void updatePatient_existingId_returnsUpdatedDTO() {
        PatientDTO updateDTO = PatientDTO.builder()
                .firstName("Updated")
                .lastName("Doe")
                .build();

        Patient updatedPatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Updated")
                .lastName("Doe")
                .build();

        PatientDTO updatedResultDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Updated")
                .lastName("Doe")
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(patient)).thenReturn(updatedPatient);
        when(patientMapper.toDto(updatedPatient)).thenReturn(updatedResultDTO);

        PatientDTO result = patientService.updatePatient(1L, updateDTO);

        assertNotNull(result);
        assertEquals("Updated", result.getFirstName());
        verify(patientMapper).updateEntityFromDto(updateDTO, patient);
        verify(patientRepository).save(patient);
    }

    @Test
    void updatePatient_nonExistingId_throwsEntityNotFoundException() {
        PatientDTO updateDTO = PatientDTO.builder()
                .firstName("Updated")
                .build();

        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> patientService.updatePatient(999L, updateDTO)
        );
    }

    @Test
    void findBySsn_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> patientService.findBySsn("123-45-6789")
        );
    }
}
