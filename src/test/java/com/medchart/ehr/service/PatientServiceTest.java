package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService")
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService patientService;

    private Patient samplePatient;
    private PatientDTO samplePatientDTO;

    @BeforeEach
    void setUp() {
        samplePatient = Patient.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();

        samplePatientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("getPatientById")
    class GetPatientById {

        @Test
        @DisplayName("should return patient when found")
        void shouldReturnPatientWhenFound() {
            when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
            when(patientMapper.toDto(samplePatient)).thenReturn(samplePatientDTO);

            PatientDTO result = patientService.getPatientById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getMrn()).isEqualTo("MRN001");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Doe");
            verify(patientRepository).findById(1L);
            verify(patientMapper).toDto(samplePatient);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(patientRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> patientService.getPatientById(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Patient not found with id: 999");
        }
    }

    @Nested
    @DisplayName("getPatientByMrn")
    class GetPatientByMrn {

        @Test
        @DisplayName("should return patient when found by MRN")
        void shouldReturnPatientWhenFoundByMrn() {
            when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(samplePatient));
            when(patientMapper.toDto(samplePatient)).thenReturn(samplePatientDTO);

            PatientDTO result = patientService.getPatientByMrn("MRN001");

            assertThat(result).isNotNull();
            assertThat(result.getMrn()).isEqualTo("MRN001");
            verify(patientRepository).findByMrn("MRN001");
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when MRN not found")
        void shouldThrowWhenMrnNotFound() {
            when(patientRepository.findByMrn("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> patientService.getPatientByMrn("INVALID"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Patient not found with MRN: INVALID");
        }
    }

    @Nested
    @DisplayName("searchPatients")
    class SearchPatients {

        @Test
        @DisplayName("should return page of patients matching search term")
        void shouldReturnPageOfPatients() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Patient> patientPage = new PageImpl<>(List.of(samplePatient), pageable, 1);
            when(patientRepository.searchPatients("Doe", pageable)).thenReturn(patientPage);
            when(patientMapper.toDto(samplePatient)).thenReturn(samplePatientDTO);

            Page<PatientDTO> result = patientService.searchPatients("Doe", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getLastName()).isEqualTo("Doe");
            verify(patientRepository).searchPatients("Doe", pageable);
        }

        @Test
        @DisplayName("should return empty page when no matches")
        void shouldReturnEmptyPageWhenNoMatches() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Patient> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(patientRepository.searchPatients("ZZZ", pageable)).thenReturn(emptyPage);

            Page<PatientDTO> result = patientService.searchPatients("ZZZ", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("createPatient")
    class CreatePatient {

        @Test
        @DisplayName("should create patient successfully")
        void shouldCreatePatientSuccessfully() {
            PatientDTO inputDTO = PatientDTO.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .dateOfBirth(LocalDate.of(1990, 6, 20))
                    .build();

            Patient newPatient = Patient.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .dateOfBirth(LocalDate.of(1990, 6, 20))
                    .build();

            Patient savedPatient = Patient.builder()
                    .id(2L)
                    .mrn("MRN12345")
                    .firstName("Jane")
                    .lastName("Smith")
                    .dateOfBirth(LocalDate.of(1990, 6, 20))
                    .build();

            PatientDTO savedDTO = PatientDTO.builder()
                    .id(2L)
                    .mrn("MRN12345")
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
            when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
            when(patientMapper.toDto(savedPatient)).thenReturn(savedDTO);

            PatientDTO result = patientService.createPatient(inputDTO);

            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("Jane");
            verify(patientRepository).save(any(Patient.class));
        }

        @Test
        @DisplayName("should generate MRN when not provided")
        void shouldGenerateMrnWhenNotProvided() {
            PatientDTO inputDTO = PatientDTO.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            Patient newPatient = Patient.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            Patient savedPatient = Patient.builder()
                    .id(2L)
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            PatientDTO savedDTO = PatientDTO.builder()
                    .id(2L)
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            when(patientMapper.toEntity(inputDTO)).thenReturn(newPatient);
            when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
            when(patientMapper.toDto(savedPatient)).thenReturn(savedDTO);

            patientService.createPatient(inputDTO);

            verify(patientRepository).save(argThat(patient ->
                    patient.getMrn() != null && patient.getMrn().startsWith("MRN")));
        }

        @Test
        @DisplayName("should throw when MRN already exists")
        void shouldThrowWhenMrnAlreadyExists() {
            PatientDTO inputDTO = PatientDTO.builder()
                    .mrn("MRN001")
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(samplePatient));

            assertThatThrownBy(() -> patientService.createPatient(inputDTO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Patient with MRN MRN001 already exists");
        }
    }

    @Nested
    @DisplayName("updatePatient")
    class UpdatePatient {

        @Test
        @DisplayName("should update patient successfully")
        void shouldUpdatePatientSuccessfully() {
            PatientDTO updateDTO = PatientDTO.builder()
                    .firstName("John")
                    .lastName("Updated")
                    .build();

            Patient updatedPatient = Patient.builder()
                    .id(1L)
                    .mrn("MRN001")
                    .firstName("John")
                    .lastName("Updated")
                    .build();

            PatientDTO resultDTO = PatientDTO.builder()
                    .id(1L)
                    .mrn("MRN001")
                    .firstName("John")
                    .lastName("Updated")
                    .build();

            when(patientRepository.findById(1L)).thenReturn(Optional.of(samplePatient));
            when(patientRepository.save(samplePatient)).thenReturn(updatedPatient);
            when(patientMapper.toDto(updatedPatient)).thenReturn(resultDTO);

            PatientDTO result = patientService.updatePatient(1L, updateDTO);

            assertThat(result.getLastName()).isEqualTo("Updated");
            verify(patientMapper).updateEntityFromDto(updateDTO, samplePatient);
            verify(patientRepository).save(samplePatient);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when updating non-existent patient")
        void shouldThrowWhenUpdatingNonExistentPatient() {
            PatientDTO updateDTO = PatientDTO.builder().firstName("Test").build();
            when(patientRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> patientService.updatePatient(999L, updateDTO))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Patient not found with id: 999");
        }
    }

    @Nested
    @DisplayName("findBySsn")
    class FindBySsn {

        @Test
        @DisplayName("should throw UnsupportedOperationException for HIPAA compliance")
        void shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> patientService.findBySsn("123-45-6789"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("SSN-based patient lookup is disabled for HIPAA compliance");
        }
    }
}
