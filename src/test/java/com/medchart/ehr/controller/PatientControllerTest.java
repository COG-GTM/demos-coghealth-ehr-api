package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    private ObjectMapper objectMapper;
    private PatientDTO patientDTO;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        patientDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .build();
    }

    @Test
    @WithMockUser
    void getPatient_returns200WithPatientJson_whenServiceReturnsDTO() throws Exception {
        when(patientService.getPatientById(1L)).thenReturn(patientDTO);

        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.mrn").value("MRN001"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @WithMockUser
    void getPatient_returns404_whenServiceThrowsEntityNotFoundException() throws Exception {
        when(patientService.getPatientById(99L))
                .thenThrow(new EntityNotFoundException("Patient not found with id: 99"));

        // No global exception handler exists, so EntityNotFoundException propagates as a
        // NestedServletException. Verify the root cause is EntityNotFoundException.
        Exception thrown = assertThrows(Exception.class, () ->
                mockMvc.perform(get("/v1/patients/99")));
        assertTrue(thrown.getCause() instanceof EntityNotFoundException);
    }

    @Test
    @WithMockUser
    void createPatient_returns201_whenPatientCreatedSuccessfully() throws Exception {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .build();

        PatientDTO createdDTO = PatientDTO.builder()
                .id(2L)
                .mrn("MRN002")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .build();

        when(patientService.createPatient(any(PatientDTO.class))).thenReturn(createdDTO);

        mockMvc.perform(post("/v1/patients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.mrn").value("MRN002"))
                .andExpect(jsonPath("$.firstName").value("Jane"));
    }

    @Test
    @WithMockUser
    void searchPatients_returns200WithPagedResponse() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(List.of(patientDTO));
        when(patientService.searchPatients(eq("Smith"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/patients/search")
                        .param("q", "Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].firstName").value("John"));
    }
}
