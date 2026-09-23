package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

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

    @Test
    void searchIsScopedToAProvidersOwnPatients() {
        User provider = User.builder().username("dana").roles(Set.of(User.Role.PROVIDER)).providerId(7L).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(patientAccessGuard.requireAuthenticatedUser()).thenReturn(provider);
        when(patientAccessGuard.hasUnrestrictedPatientAccess(provider)).thenReturn(false);
        when(patientRepository.searchPatientsForProvider("smith", 7L, pageable)).thenReturn(Page.empty(pageable));

        patientService.searchPatients("smith", pageable);

        verify(patientRepository, never()).searchPatients(any(), any());
        verify(patientRepository).searchPatientsForProvider("smith", 7L, pageable);
    }

    @Test
    void searchReturnsNothingForAProviderWithNoProviderLink() {
        User provider = User.builder().username("dana").roles(Set.of(User.Role.PROVIDER)).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(patientAccessGuard.requireAuthenticatedUser()).thenReturn(provider);
        when(patientAccessGuard.hasUnrestrictedPatientAccess(provider)).thenReturn(false);

        assertThat(patientService.searchPatients("smith", pageable)).isEmpty();

        verify(patientRepository, never()).searchPatients(any(), any());
        verify(patientRepository, never()).searchPatientsForProvider(any(), any(), any());
    }

    @Test
    void searchIsUnscopedForAdministrativeUsers() {
        User admin = User.builder().username("root").roles(Set.of(User.Role.ADMIN)).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(patientAccessGuard.requireAuthenticatedUser()).thenReturn(admin);
        when(patientAccessGuard.hasUnrestrictedPatientAccess(admin)).thenReturn(true);
        when(patientRepository.searchPatients("smith", pageable)).thenReturn(Page.empty(pageable));

        patientService.searchPatients("smith", pageable);

        verify(patientRepository).searchPatients("smith", pageable);
    }
}
