package com.medchart.ehr.service;

import com.medchart.ehr.config.MethodSecurityConfig;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig({PatientService.class, MethodSecurityConfig.class})
class PatientServiceTest {

    @Autowired
    private PatientService patientService;

    @MockBean
    private PatientRepository patientRepository;

    @MockBean
    private PatientMapper patientMapper;

    @Test
    void unauthenticatedCallIsRejected() {
        assertThatThrownBy(() -> patientService.getPatientById(17L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void nonClinicalRoleCannotReadOrSearch() {
        assertThatThrownBy(() -> patientService.getPatientByMrn("MRN123")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> patientService.searchPatients("doe", PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotWrite() {
        assertThatThrownBy(() -> patientService.createPatient(new PatientDTO())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> patientService.updatePatient(17L, new PatientDTO()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanRead() {
        Patient patient = new Patient();
        when(patientRepository.findById(17L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(new PatientDTO());

        assertThat(patientService.getPatientById(17L)).isNotNull();
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void blankOrShortSearchTermIsRejected() {
        for (String term : new String[] {null, "", "   ", "a", " a "}) {
            assertThatThrownBy(() -> patientService.searchPatients(term, PageRequest.of(0, 20)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> patientService.searchPatients("x".repeat(101), PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(patientRepository);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void searchEscapesWildcardsAndCapsPageSize() {
        when(patientRepository.searchPatients(anyString(), any())).thenReturn(Page.empty());

        patientService.searchPatients(" %_! ", PageRequest.of(3, 2000));

        ArgumentCaptor<String> term = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository).searchPatients(term.capture(), page.capture());
        assertThat(term.getValue()).isEqualTo("!%!_!!");
        assertThat(page.getValue().getPageSize()).isEqualTo(PatientService.MAX_SEARCH_PAGE_SIZE);
        assertThat(page.getValue().getPageNumber()).isEqualTo(3);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void unpagedSearchIsBounded() {
        when(patientRepository.searchPatients(anyString(), any())).thenReturn(Page.empty());

        patientService.searchPatients("doe", Pageable.unpaged());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository).searchPatients(any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(PatientService.MAX_SEARCH_PAGE_SIZE);
    }
}
