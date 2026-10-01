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
    void getPatientById_existingPatient_returnsDto() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientById(1L);

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
        assertEquals("John", result.getFirstName());
        verify(patientRepository).findById(1L);
    }

    @Test
    void getPatientById_nonExistingPatient_throwsEntityNotFoundException() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientById(999L));
    }

    @Test
    void getPatientByMrn_existingMrn_returnsDto() {
        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientByMrn("MRN001");

        assertNotNull(result);
        assertEquals("MRN001", result.getMrn());
    }

    @Test
    void getPatientByMrn_nonExistingMrn_throwsEntityNotFoundException() {
        when(patientRepository.findByMrn("INVALID")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientByMrn("INVALID"));
    }

    @Test
    void searchPatients_returnsPageOfDtos() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Patient> patientPage = new PageImpl<>(Collections.singletonList(patient));
        when(patientRepository.searchPatients("John", pageable)).thenReturn(patientPage);
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        Page<PatientDTO> result = patientService.searchPatients("John", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("John", result.getContent().get(0).getFirstName());
    }

    @Test
    void createPatient_newPatient_returnsCreatedDto() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .build();

        Patient newPatient = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .build();

        Patient savedPatient = Patient.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
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
    void createPatient_duplicateMrn_throwsIllegalArgument() {
        PatientDTO inputDTO = PatientDTO.builder()
                .mrn("MRN001")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(patient));

        assertThrows(IllegalArgumentException.class,
                () -> patientService.createPatient(inputDTO));
    }

    @Test
    void createPatient_withMrnProvided_usesThatMrn() {
        PatientDTO inputDTO = PatientDTO.builder()
                .mrn("MRN999")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        Patient newPatient = Patient.builder()
                .mrn("MRN999")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientRepository.findByMrn("MRN999")).thenReturn(Optional.empty());
        when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
        when(patientRepository.save(newPatient)).thenReturn(newPatient);
        when(patientMapper.toDto(newPatient)).thenReturn(inputDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertNotNull(result);
        assertEquals("MRN999", result.getMrn());
    }

    @Test
    void updatePatient_existingPatient_returnsUpdatedDto() {
        PatientDTO updateDTO = PatientDTO.builder()
                .firstName("John")
                .lastName("UpdatedDoe")
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(patient)).thenReturn(patient);
        when(patientMapper.toDto(patient)).thenReturn(updateDTO);

        PatientDTO result = patientService.updatePatient(1L, updateDTO);

        assertNotNull(result);
        verify(patientMapper).updateEntityFromDto(updateDTO, patient);
        verify(patientRepository).save(patient);
    }

    @Test
    void updatePatient_nonExistingPatient_throwsEntityNotFoundException() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> patientService.updatePatient(999L, patientDTO));
    }

    @Test
    void findBySsn_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> patientService.findBySsn("123-45-6789"));
    }
}
