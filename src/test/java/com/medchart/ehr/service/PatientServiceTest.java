package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.persistence.EntityNotFoundException;
import java.util.Collections;
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

    private static Patient patient(Long id, String mrn) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setMrn(mrn);
        return patient;
    }

    @Test
    void getPatientByIdReturnsMappedDto() {
        Patient entity = patient(1L, "MRN-1");
        PatientDTO dto = PatientDTO.builder().id(1L).mrn("MRN-1").build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(patientMapper.toDto(entity)).thenReturn(dto);

        assertThat(patientService.getPatientById(1L)).isSameAs(dto);
    }

    @Test
    void getPatientByIdThrowsWhenMissing() {
        when(patientRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientById(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void getPatientByMrnReturnsMappedDto() {
        Patient entity = patient(2L, "MRN-2");
        PatientDTO dto = PatientDTO.builder().id(2L).mrn("MRN-2").build();
        when(patientRepository.findByMrn("MRN-2")).thenReturn(Optional.of(entity));
        when(patientMapper.toDto(entity)).thenReturn(dto);

        assertThat(patientService.getPatientByMrn("MRN-2")).isSameAs(dto);
    }

    @Test
    void getPatientByMrnThrowsWhenMissing() {
        when(patientRepository.findByMrn("MRN-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientByMrn("MRN-404"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("MRN-404");
    }

    @Test
    void searchPatientsMapsPageContents() {
        Patient entity = patient(3L, "MRN-3");
        PatientDTO dto = PatientDTO.builder().id(3L).mrn("MRN-3").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(patientRepository.searchPatients("smith", pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(entity), pageable, 1));
        when(patientMapper.toDto(entity)).thenReturn(dto);

        Page<PatientDTO> result = patientService.searchPatients("smith", pageable);

        assertThat(result.getContent()).containsExactly(dto);
    }

    @Test
    void createPatientRejectsDuplicateMrn() {
        PatientDTO request = PatientDTO.builder().mrn("MRN-DUP").build();
        when(patientRepository.findByMrn("MRN-DUP")).thenReturn(Optional.of(patient(4L, "MRN-DUP")));

        assertThatThrownBy(() -> patientService.createPatient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MRN-DUP");

        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatientKeepsProvidedMrnWhenUnused() {
        PatientDTO request = PatientDTO.builder().mrn("MRN-NEW").build();
        Patient entity = patient(null, "MRN-NEW");
        Patient saved = patient(5L, "MRN-NEW");
        PatientDTO dto = PatientDTO.builder().id(5L).mrn("MRN-NEW").build();
        when(patientRepository.findByMrn("MRN-NEW")).thenReturn(Optional.empty());
        when(patientMapper.toEntity(request)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(saved);
        when(patientMapper.toDto(saved)).thenReturn(dto);

        assertThat(patientService.createPatient(request)).isSameAs(dto);
        assertThat(entity.getMrn()).isEqualTo("MRN-NEW");
    }

    @Test
    void createPatientGeneratesMrnWhenAbsent() {
        PatientDTO request = PatientDTO.builder().build();
        Patient entity = patient(null, null);
        Patient saved = patient(6L, "MRN-GENERATED");
        PatientDTO dto = PatientDTO.builder().id(6L).mrn("MRN-GENERATED").build();
        when(patientMapper.toEntity(request)).thenReturn(entity);
        when(patientRepository.save(any(Patient.class))).thenReturn(saved);
        when(patientMapper.toDto(saved)).thenReturn(dto);

        assertThat(patientService.createPatient(request)).isSameAs(dto);

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        assertThat(captor.getValue().getMrn()).startsWith("MRN");
        verify(patientRepository, never()).findByMrn(any());
    }

    @Test
    void updatePatientAppliesChangesToExistingEntity() {
        PatientDTO request = PatientDTO.builder().lastName("Doe").build();
        Patient existing = patient(7L, "MRN-7");
        PatientDTO dto = PatientDTO.builder().id(7L).mrn("MRN-7").lastName("Doe").build();
        when(patientRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(existing)).thenReturn(existing);
        when(patientMapper.toDto(existing)).thenReturn(dto);

        assertThat(patientService.updatePatient(7L, request)).isSameAs(dto);

        verify(patientMapper).updateEntityFromDto(request, existing);
    }

    @Test
    void updatePatientThrowsWhenMissing() {
        PatientDTO request = PatientDTO.builder().build();
        when(patientRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.updatePatient(8L, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("8");

        verify(patientRepository, never()).save(any());
    }

    @Test
    void findBySsnIsDisabled() {
        assertThatThrownBy(() -> patientService.findBySsn("123-45-6789"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
