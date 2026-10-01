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
import java.util.List;
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
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();

        patientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Test
    void getPatientById_shouldReturnPatient_whenExists() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientById(1L);

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        assertEquals("John", result.getFirstName());
        verify(patientRepository).findById(1L);
    }

    @Test
    void getPatientById_shouldThrowException_whenNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientById(99L));
    }

    @Test
    void getPatientByMrn_shouldReturnPatient_whenExists() {
        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientByMrn("MRN001");

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        verify(patientRepository).findByMrn("MRN001");
    }

    @Test
    void getPatientByMrn_shouldThrowException_whenNotFound() {
        when(patientRepository.findByMrn("INVALID")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientByMrn("INVALID"));
    }

    @Test
    void searchPatients_shouldReturnPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Patient> patientPage = new PageImpl<>(List.of(patient));
        when(patientRepository.searchPatients("Doe", pageable)).thenReturn(patientPage);
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        Page<PatientDTO> result = patientService.searchPatients("Doe", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("John", result.getContent().get(0).getFirstName());
    }

    @Test
    void searchPatients_shouldReturnEmptyPage_whenNoMatch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Patient> emptyPage = new PageImpl<>(List.of());
        when(patientRepository.searchPatients("NoMatch", pageable)).thenReturn(emptyPage);

        Page<PatientDTO> result = patientService.searchPatients("NoMatch", pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void createPatient_shouldCreateAndReturnPatient() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        Patient newPatient = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        Patient savedPatient = Patient.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        PatientDTO savedDTO = PatientDTO.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
        when(patientMapper.toDto(savedPatient)).thenReturn(savedDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertNotNull(result);
        assertEquals("Jane", result.getFirstName());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void createPatient_shouldGenerateMrn_whenNotProvided() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        Patient newPatient = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .build();

        Patient savedPatient = Patient.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
        when(patientMapper.toDto(savedPatient)).thenReturn(PatientDTO.builder().mrn("MRN1234567890").build());

        patientService.createPatient(inputDTO);

        verify(patientRepository).save(argThat(p -> p.getMrn() != null && p.getMrn().startsWith("MRN")));
    }

    @Test
    void createPatient_shouldThrowException_whenDuplicateMrn() {
        PatientDTO inputDTO = PatientDTO.builder()
                .mrn("MRN001")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));

        assertThrows(IllegalArgumentException.class,
                () -> patientService.createPatient(inputDTO));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void updatePatient_shouldUpdateAndReturn() {
        PatientDTO updateDTO = PatientDTO.builder()
                .firstName("Johnny")
                .lastName("Doe")
                .build();

        Patient updatedPatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Johnny")
                .lastName("Doe")
                .build();

        PatientDTO updatedDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Johnny")
                .lastName("Doe")
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(patient)).thenReturn(updatedPatient);
        when(patientMapper.toDto(updatedPatient)).thenReturn(updatedDTO);

        PatientDTO result = patientService.updatePatient(1L, updateDTO);

        assertNotNull(result);
        assertEquals("Johnny", result.getFirstName());
        verify(patientMapper).updateEntityFromDto(updateDTO, patient);
    }

    @Test
    void updatePatient_shouldThrowException_whenNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.updatePatient(99L, patientDTO));
    }

    @Test
    void findBySsn_shouldThrowUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class,
                () -> patientService.findBySsn("123-45-6789"));
    }
}
