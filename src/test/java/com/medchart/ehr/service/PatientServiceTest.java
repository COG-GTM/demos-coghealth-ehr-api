package com.medchart.ehr.service;

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

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                .mrn("MRN123")
                .firstName("John")
                .lastName("Doe")
                .build();

        patientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN123")
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    @Test
    void getPatientById_throwsEntityNotFoundException_whenPatientNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Patient not found with id: 99");
    }

    @Test
    void getPatientById_returnsDTO_whenPatientExists() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(patientDTO);

        PatientDTO result = patientService.getPatientById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getMrn()).isEqualTo("MRN123");
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(patientRepository).findById(1L);
        verify(patientMapper).toDto(patient);
    }

    @Test
    void createPatient_throwsIllegalArgumentException_whenMrnAlreadyExists() {
        PatientDTO newPatient = PatientDTO.builder()
                .mrn("MRN123")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientRepository.findByMrn("MRN123")).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> patientService.createPatient(newPatient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Patient with MRN MRN123 already exists");
    }

    @Test
    void createPatient_succeedsAndGeneratesMrn_whenNoMrnProvided() {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .build();

        Patient entityWithoutMrn = Patient.builder()
                .firstName("Jane")
                .lastName("Smith")
                .build();

        Patient savedEntity = Patient.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        PatientDTO savedDTO = PatientDTO.builder()
                .id(2L)
                .mrn("MRN1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .build();

        when(patientMapper.toEntity(inputDTO)).thenReturn(entityWithoutMrn);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient p = invocation.getArgument(0);
            // Verify MRN was generated before save
            assertThat(p.getMrn()).isNotNull();
            assertThat(p.getMrn()).startsWith("MRN");
            p.setId(2L);
            return p;
        });
        when(patientMapper.toDto(any(Patient.class))).thenReturn(savedDTO);

        PatientDTO result = patientService.createPatient(inputDTO);

        assertThat(result).isNotNull();
        assertThat(result.getMrn()).isNotNull();
        assertThat(result.getMrn()).startsWith("MRN");
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void findBySsn_alwaysThrowsUnsupportedOperationException() {
        assertThatThrownBy(() -> patientService.findBySsn("123-45-6789"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SSN-based patient lookup is disabled for HIPAA compliance");
    }
}
