package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser
    void getPatient_returnsOk() throws Exception {
        PatientDTO dto = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(patientService.getPatientById(1L)).thenReturn(dto);

        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn").value("MRN001"))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    @WithMockUser
    void getPatientByMrn_returnsOk() throws Exception {
        PatientDTO dto = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(patientService.getPatientByMrn("MRN001")).thenReturn(dto);

        mockMvc.perform(get("/v1/patients/mrn/MRN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn").value("MRN001"));
    }

    @Test
    @WithMockUser
    void searchPatients_returnsOk() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(Collections.emptyList());
        when(patientService.searchPatients(anyString(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/v1/patients/search").param("q", "Doe"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void createPatient_returnsCreated() throws Exception {
        PatientDTO input = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();

        PatientDTO created = PatientDTO.builder()
                .id(2L)
                .mrn("MRN002")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();

        when(patientService.createPatient(any(PatientDTO.class))).thenReturn(created);

        mockMvc.perform(post("/v1/patients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mrn").value("MRN002"));
    }

    @Test
    @WithMockUser
    void updatePatient_returnsOk() throws Exception {
        PatientDTO input = PatientDTO.builder()
                .firstName("John")
                .lastName("Updated")
                .dateOfBirth(LocalDate.of(1985, 5, 10))
                .build();

        PatientDTO updated = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Updated")
                .build();

        when(patientService.updatePatient(eq(1L), any(PatientDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/v1/patients/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"));
    }
}
