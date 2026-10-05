package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.mapper.PatientMapperImpl;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService patientService;

    @Test
    void getPatientByIdReturnsMappedDto() {
        Patient patient = Patient.builder().id(1L).mrn("MRN001").build();
        PatientDTO dto = PatientDTO.builder().id(1L).mrn("MRN001").build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(dto);

        assertThat(patientService.getPatientById(1L)).isSameAs(dto);
    }

    @Test
    void getPatientByIdThrowsWhenMissing() {
        when(patientRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientById(42L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Patient not found with id: 42");
    }

    @Test
    void getPatientByMrnThrowsWhenMissing() {
        when(patientRepository.findByMrn("MRN404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientByMrn("MRN404"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Patient not found with MRN: MRN404");
    }

    @Test
    void createPatientRejectsDuplicateMrn() {
        PatientDTO dto = PatientDTO.builder().mrn("MRN001").build();
        when(patientRepository.findByMrn("MRN001")).thenReturn(Optional.of(new Patient()));

        assertThatThrownBy(() -> patientService.createPatient(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MRN001");
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatientGeneratesMrnWhenMissing() {
        PatientDTO dto = PatientDTO.builder().firstName("Ada").lastName("Lovelace").build();
        Patient entity = Patient.builder().firstName("Ada").lastName("Lovelace").build();
        when(patientMapper.toEntity(dto)).thenReturn(entity);
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientMapper.toDto(any(Patient.class))).thenReturn(dto);

        patientService.createPatient(dto);

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getMrn()).startsWith("MRN");
    }

    @Test
    void updatePatientAppliesNonNullDtoFieldsToExistingEntity() {
        PatientService serviceWithRealMapper = new PatientService(patientRepository, new PatientMapperImpl());
        Patient existing = Patient.builder().id(7L).mrn("MRN007").firstName("Ada").lastName("Old").build();
        PatientDTO update = PatientDTO.builder().lastName("Updated").build();
        when(patientRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientDTO result = serviceWithRealMapper.updatePatient(7L, update);

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getLastName()).isEqualTo("Updated");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(saved.getValue().getMrn()).isEqualTo("MRN007");
        assertThat(result.getLastName()).isEqualTo("Updated");
    }

    @Test
    void ssnLookupIsDisabled() {
        assertThatThrownBy(() -> patientService.findBySsn("123-45-6789"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
