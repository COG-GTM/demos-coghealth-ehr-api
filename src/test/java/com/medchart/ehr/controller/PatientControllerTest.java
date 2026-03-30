package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.persistence.EntityNotFoundException;
import org.springframework.web.util.NestedServletException;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
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
    private com.medchart.ehr.config.JwtTokenProvider jwtTokenProvider;

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
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .gender(Gender.MALE)
                .active(true)
                .build();
    }

    @Test
    void getPatient_existingId_returns200() throws Exception {
        when(patientService.getPatientById(1L)).thenReturn(patientDTO);

        mockMvc.perform(get("/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn").value("MRN001"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void getPatient_nonExistingId_throwsException() {
        when(patientService.getPatientById(999L)).thenThrow(new EntityNotFoundException("Patient not found"));

        assertThrows(NestedServletException.class, () ->
                mockMvc.perform(get("/v1/patients/999")));
    }

    @Test
    void getPatientByMrn_existingMrn_returns200() throws Exception {
        when(patientService.getPatientByMrn("MRN001")).thenReturn(patientDTO);

        mockMvc.perform(get("/v1/patients/mrn/MRN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mrn").value("MRN001"));
    }

    @Test
    void searchPatients_returns200WithPage() throws Exception {
        Page<PatientDTO> page = new PageImpl<>(Collections.singletonList(patientDTO), PageRequest.of(0, 20), 1);
        when(patientService.searchPatients(eq("Doe"), any())).thenReturn(page);

        mockMvc.perform(get("/v1/patients/search").param("q", "Doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Doe"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createPatient_validInput_returns201() throws Exception {
        PatientDTO input = PatientDTO.builder()
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .build();

        PatientDTO created = PatientDTO.builder()
                .id(2L)
                .mrn("MRN002")
                .firstName("Jane")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender(Gender.FEMALE)
                .build();

        when(patientService.createPatient(any(PatientDTO.class))).thenReturn(created);

        mockMvc.perform(post("/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.mrn").value("MRN002"));
    }

    @Test
    void updatePatient_existingId_returns200() throws Exception {
        PatientDTO updateInput = PatientDTO.builder()
                .firstName("Updated")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .build();

        PatientDTO updatedResult = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("Updated")
                .lastName("Doe")
                .build();

        when(patientService.updatePatient(eq(1L), any(PatientDTO.class))).thenReturn(updatedResult);

        mockMvc.perform(put("/v1/patients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    void updatePatient_nonExistingId_throwsException() {
        when(patientService.updatePatient(eq(999L), any(PatientDTO.class)))
                .thenThrow(new EntityNotFoundException("Patient not found"));

        PatientDTO updateInput = PatientDTO.builder()
                .firstName("Updated")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .build();

        assertThrows(NestedServletException.class, () ->
                mockMvc.perform(put("/v1/patients/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateInput))));
    }
}
