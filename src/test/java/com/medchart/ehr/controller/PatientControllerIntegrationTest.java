package com.medchart.ehr.controller;

import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@DisplayName("PatientController Integration Tests")
class PatientControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    private ObjectMapper objectMapper;
    private PatientDTO samplePatientDTO;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        samplePatientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("GET /v1/patients/{id}")
    class GetPatientById {

        @Test
        @WithMockUser
        @DisplayName("should return patient when found")
        void shouldReturnPatientWhenFound() throws Exception {
            when(patientService.getPatientById(1L)).thenReturn(samplePatientDTO);

            mockMvc.perform(get("/v1/patients/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.mrn").value("MRN001"))
                    .andExpect(jsonPath("$.firstName").value("John"))
                    .andExpect(jsonPath("$.lastName").value("Doe"));
        }

        @Test
        @WithMockUser
        @DisplayName("should propagate EntityNotFoundException when patient not found")
        void shouldPropagateExceptionWhenNotFound() throws Exception {
            when(patientService.getPatientById(999L))
                    .thenThrow(new EntityNotFoundException("Patient not found with id: 999"));

            // No @ControllerAdvice exists, so the exception propagates as NestedServletException
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.util.NestedServletException.class,
                    () -> mockMvc.perform(get("/v1/patients/999")));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/mrn/{mrn}")
    class GetPatientByMrn {

        @Test
        @WithMockUser
        @DisplayName("should return patient when found by MRN")
        void shouldReturnPatientWhenFoundByMrn() throws Exception {
            when(patientService.getPatientByMrn("MRN001")).thenReturn(samplePatientDTO);

            mockMvc.perform(get("/v1/patients/mrn/MRN001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mrn").value("MRN001"));
        }
    }

    @Nested
    @DisplayName("GET /v1/patients/search")
    class SearchPatients {

        @Test
        @WithMockUser
        @DisplayName("should return page of matching patients")
        void shouldReturnPageOfMatchingPatients() throws Exception {
            Page<PatientDTO> page = new PageImpl<>(List.of(samplePatientDTO));
            when(patientService.searchPatients(eq("Doe"), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/v1/patients/search").param("q", "Doe"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].lastName").value("Doe"));
        }
    }

    @Nested
    @DisplayName("POST /v1/patients")
    class CreatePatient {

        @Test
        @WithMockUser
        @DisplayName("should create patient and return 201")
        void shouldCreatePatientAndReturn201() throws Exception {
            PatientDTO inputDTO = PatientDTO.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .dateOfBirth(LocalDate.of(1990, 6, 20))
                    .build();

            PatientDTO createdDTO = PatientDTO.builder()
                    .id(2L)
                    .mrn("MRN12345")
                    .firstName("Jane")
                    .lastName("Smith")
                    .dateOfBirth(LocalDate.of(1990, 6, 20))
                    .build();

            when(patientService.createPatient(any(PatientDTO.class))).thenReturn(createdDTO);

            mockMvc.perform(post("/v1/patients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(inputDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.mrn").value("MRN12345"))
                    .andExpect(jsonPath("$.firstName").value("Jane"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when first name is missing")
        void shouldReturn400WhenFirstNameMissing() throws Exception {
            PatientDTO invalidDTO = PatientDTO.builder()
                    .lastName("Smith")
                    .build();

            mockMvc.perform(post("/v1/patients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDTO)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /v1/patients/{id}")
    class UpdatePatient {

        @Test
        @WithMockUser
        @DisplayName("should update patient and return 200")
        void shouldUpdatePatientAndReturn200() throws Exception {
            PatientDTO updateDTO = PatientDTO.builder()
                    .firstName("John")
                    .lastName("Updated")
                    .dateOfBirth(LocalDate.of(1985, 3, 15))
                    .build();

            PatientDTO updatedDTO = PatientDTO.builder()
                    .id(1L)
                    .mrn("MRN001")
                    .firstName("John")
                    .lastName("Updated")
                    .build();

            when(patientService.updatePatient(eq(1L), any(PatientDTO.class))).thenReturn(updatedDTO);

            mockMvc.perform(put("/v1/patients/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastName").value("Updated"));
        }
    }
}
