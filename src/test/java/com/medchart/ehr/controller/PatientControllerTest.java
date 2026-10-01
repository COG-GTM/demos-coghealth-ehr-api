package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.util.NestedServletException;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

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
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Test
    void getPatient_shouldReturn200_whenFound() throws Exception {
        when(patientService.getPatientById(1L)).thenReturn(patientDTO);

        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn", is("MRN001")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")));
    }

    @Test
    void getPatient_shouldThrowException_whenNotFound() throws Exception {
        when(patientService.getPatientById(99L)).thenThrow(new EntityNotFoundException("Not found"));

        assertThrows(NestedServletException.class,
                () -> mockMvc.perform(get("/v1/patients/99")));
    }

    @Test
    void getPatientByMrn_shouldReturn200() throws Exception {
        when(patientService.getPatientByMrn("MRN001")).thenReturn(patientDTO);

        mockMvc.perform(get("/v1/patients/mrn/MRN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn", is("MRN001")));
    }

    @Test
    void searchPatients_shouldReturnPagedResults() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(List.of(patientDTO));
        when(patientService.searchPatients(eq("Doe"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/patients/search")
                        .param("q", "Doe")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].firstName", is("John")));
    }

    @Test
    void createPatient_shouldReturn201() throws Exception {
        PatientDTO inputDTO = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        PatientDTO createdDTO = PatientDTO.builder()
                .id(2L)
                .mrn("MRN002")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 6, 20))
                .build();

        when(patientService.createPatient(any(PatientDTO.class))).thenReturn(createdDTO);

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.mrn", is("MRN002")))
                .andExpect(jsonPath("$.firstName", is("Jane")));
    }

    @Test
    void updatePatient_shouldReturn200() throws Exception {
        PatientDTO updatedDTO = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Johnny")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .build();

        when(patientService.updatePatient(eq(1L), any(PatientDTO.class))).thenReturn(updatedDTO);

        mockMvc.perform(put("/v1/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Johnny")));
    }
}
