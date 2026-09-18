package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PatientController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.medchart\\.ehr\\.config\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    private PatientDTO patient;

    @BeforeEach
    void setUp() {
        patient = PatientDTO.builder()
                .id(42L)
                .mrn("MRN-1001")
                .firstName("Ada")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1980, 5, 17))
                .build();
    }

    @Test
    void getPatientReturnsPatient() throws Exception {
        given(patientService.getPatientById(42L)).willReturn(patient);

        mockMvc.perform(get("/v1/patients/{id}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.mrn").value("MRN-1001"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }

    @Test
    void getPatientPropagatesNotFound() throws Exception {
        given(patientService.getPatientById(99L))
                .willThrow(new EntityNotFoundException("Patient not found with id: 99"));

        assertThatThrownBy(() -> mockMvc.perform(get("/v1/patients/{id}", 99L)))
                .hasRootCauseInstanceOf(EntityNotFoundException.class)
                .hasRootCauseMessage("Patient not found with id: 99");
    }

    @Test
    void getPatientByMrnReturnsPatient() throws Exception {
        given(patientService.getPatientByMrn("MRN-1001")).willReturn(patient);

        mockMvc.perform(get("/v1/patients/mrn/{mrn}", "MRN-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn").value("MRN-1001"))
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void getPatientByMrnPropagatesNotFound() throws Exception {
        given(patientService.getPatientByMrn("MRN-MISSING"))
                .willThrow(new EntityNotFoundException("Patient not found with MRN: MRN-MISSING"));

        assertThatThrownBy(() -> mockMvc.perform(get("/v1/patients/mrn/{mrn}", "MRN-MISSING")))
                .hasRootCauseInstanceOf(EntityNotFoundException.class)
                .hasRootCauseMessage("Patient not found with MRN: MRN-MISSING");
    }

    @Test
    void searchPatientsPassesQueryAndPageable() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(
                Collections.singletonList(patient), PageRequest.of(1, 5), 6);
        given(patientService.searchPatients(eq("lovelace"), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/v1/patients/search")
                        .param("q", "lovelace")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mrn").value("MRN-1001"))
                .andExpect(jsonPath("$.totalElements").value(6));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).searchPatients(eq("lovelace"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void searchPatientsRequiresQueryParameter() throws Exception {
        mockMvc.perform(get("/v1/patients/search"))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).searchPatients(any(), any());
    }

    @Test
    void createPatientReturnsCreated() throws Exception {
        PatientDTO request = PatientDTO.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.of(1980, 5, 17))
                .build();
        given(patientService.createPatient(any(PatientDTO.class))).willReturn(patient);

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.mrn").value("MRN-1001"));

        ArgumentCaptor<PatientDTO> captor = ArgumentCaptor.forClass(PatientDTO.class);
        verify(patientService).createPatient(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(captor.getValue().getLastName()).isEqualTo("Lovelace");
        assertThat(captor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 17));
    }

    @Test
    void createPatientRejectsBlankRequiredFields() throws Exception {
        PatientDTO invalid = PatientDTO.builder()
                .firstName("")
                .lastName("Lovelace")
                .build();

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).createPatient(any());
    }

    @Test
    void createPatientRejectsFutureDateOfBirth() throws Exception {
        PatientDTO invalid = PatientDTO.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .dateOfBirth(LocalDate.now().plusDays(1))
                .build();

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).createPatient(any());
    }

    @Test
    void updatePatientReturnsUpdatedPatient() throws Exception {
        PatientDTO request = PatientDTO.builder()
                .firstName("Ada")
                .lastName("Byron")
                .build();
        PatientDTO updated = PatientDTO.builder()
                .id(42L)
                .mrn("MRN-1001")
                .firstName("Ada")
                .lastName("Byron")
                .build();
        given(patientService.updatePatient(eq(42L), any(PatientDTO.class))).willReturn(updated);

        mockMvc.perform(put("/v1/patients/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Byron"));

        ArgumentCaptor<PatientDTO> captor = ArgumentCaptor.forClass(PatientDTO.class);
        verify(patientService).updatePatient(eq(42L), captor.capture());
        assertThat(captor.getValue().getLastName()).isEqualTo("Byron");
    }

    @Test
    void updatePatientRejectsInvalidBody() throws Exception {
        PatientDTO invalid = PatientDTO.builder()
                .firstName("Ada")
                .lastName(" ")
                .build();

        mockMvc.perform(put("/v1/patients/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(patientService, never()).updatePatient(any(), any());
    }

    @Test
    void updatePatientPropagatesNotFound() throws Exception {
        PatientDTO request = PatientDTO.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .build();
        given(patientService.updatePatient(eq(99L), any(PatientDTO.class)))
                .willThrow(new EntityNotFoundException("Patient not found with id: 99"));

        String body = objectMapper.writeValueAsString(request);

        assertThatThrownBy(() -> mockMvc.perform(put("/v1/patients/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .hasRootCauseInstanceOf(EntityNotFoundException.class)
                .hasRootCauseMessage("Patient not found with id: 99");
    }
}
