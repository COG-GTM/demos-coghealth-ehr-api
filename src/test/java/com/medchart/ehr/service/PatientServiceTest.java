package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.security.MrnGenerator;
import com.medchart.ehr.security.PatientAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private PatientAccessGuard patientAccessGuard;

    @Mock
    private MrnGenerator mrnGenerator;

    @InjectMocks
    private PatientService patientService;

    @Test
    void getPatientByIdIsAuthorizedBeforeTheRecordIsRead() {
        doThrow(new AccessDeniedException("denied"))
                .when(patientAccessGuard).requireAccessToPatient(42L);

        assertThatThrownBy(() -> patientService.getPatientById(42L))
                .isInstanceOf(AccessDeniedException.class);

        verify(patientRepository, never()).findById(any());
    }

    @Test
    void getPatientByMrnIsAuthorizedAgainstTheResolvedPatient() {
        Patient patient = new Patient();
        patient.setId(42L);
        patient.setMrn("MRN0123456789ABCD");
        when(patientRepository.findByMrn("MRN0123456789ABCD")).thenReturn(Optional.of(patient));
        doThrow(new AccessDeniedException("denied"))
                .when(patientAccessGuard).requireAccessToPatient(42L);

        assertThatThrownBy(() -> patientService.getPatientByMrn("MRN0123456789ABCD"))
                .isInstanceOf(AccessDeniedException.class);

        verify(patientMapper, never()).toDto(any());
    }

    @Test
    void createPatientAssignsAnOpaqueGeneratedMrn() {
        PatientDTO request = PatientDTO.builder().firstName("Ada").lastName("Lovelace").build();
        Patient entity = new Patient();
        when(patientMapper.toEntity(request)).thenReturn(entity);
        when(mrnGenerator.generate()).thenReturn("MRN0F1E2D3C4B5A6978");
        when(patientRepository.findByMrn("MRN0F1E2D3C4B5A6978")).thenReturn(Optional.empty());
        when(patientRepository.save(entity)).thenReturn(entity);

        patientService.createPatient(request);

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getMrn()).isEqualTo("MRN0F1E2D3C4B5A6978");
        assertThat(saved.getValue().getMrn()).doesNotContain(String.valueOf(System.currentTimeMillis() / 100_000));
    }

    @Test
    void createPatientRetriesWhenAGeneratedMrnCollides() {
        PatientDTO request = PatientDTO.builder().firstName("Ada").lastName("Lovelace").build();
        Patient entity = new Patient();
        when(patientMapper.toEntity(request)).thenReturn(entity);
        when(mrnGenerator.generate()).thenReturn("MRNAAAAAAAAAAAAAAAA", "MRNBBBBBBBBBBBBBBBB");
        when(patientRepository.findByMrn("MRNAAAAAAAAAAAAAAAA")).thenReturn(Optional.of(new Patient()));
        when(patientRepository.findByMrn("MRNBBBBBBBBBBBBBBBB")).thenReturn(Optional.empty());
        when(patientRepository.save(entity)).thenReturn(entity);

        patientService.createPatient(request);

        assertThat(entity.getMrn()).isEqualTo("MRNBBBBBBBBBBBBBBBB");
    }

    @Test
    void searchRequiresAnAuthenticatedCaller() {
        doThrow(new AccessDeniedException("denied")).when(patientAccessGuard).requireAuthenticatedUser();

        assertThatThrownBy(() -> patientService.searchPatients("smith", null))
                .isInstanceOf(AccessDeniedException.class);

        verify(patientRepository, never()).searchPatients(any(), any());
    }
}
