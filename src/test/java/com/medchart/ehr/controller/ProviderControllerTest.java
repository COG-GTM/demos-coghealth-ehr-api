package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProviderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;
    private Provider provider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Sarah")
                .lastName("Johnson")
                .credentials("MD")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .build();
    }

    @Test
    void getAll_shouldReturnAllProviders() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(provider));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].npi", is("1234567890")));
    }

    @Test
    void getAll_withActiveFilter_shouldReturnActiveOnly() throws Exception {
        when(providerService.findActive()).thenReturn(List.of(provider));

        mockMvc.perform(get("/v1/providers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(providerService).findActive();
        verify(providerService, never()).findAll();
    }

    @Test
    void getById_shouldReturn200_whenFound() throws Exception {
        when(providerService.findById(1L)).thenReturn(Optional.of(provider));

        mockMvc.perform(get("/v1/providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Sarah")))
                .andExpect(jsonPath("$.lastName", is("Johnson")));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(providerService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNpi_shouldReturn200() throws Exception {
        when(providerService.findByNpi("1234567890")).thenReturn(Optional.of(provider));

        mockMvc.perform(get("/v1/providers/npi/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npi", is("1234567890")));
    }

    @Test
    void getByNpi_shouldReturn404_whenNotFound() throws Exception {
        when(providerService.findByNpi("0000000000")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/npi/0000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByDepartment_shouldReturnList() throws Exception {
        when(providerService.findByDepartment("Medicine")).thenReturn(List.of(provider));

        mockMvc.perform(get("/v1/providers/department/Medicine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getBySpecialty_shouldReturnList() throws Exception {
        when(providerService.findBySpecialty("Internal Medicine")).thenReturn(List.of(provider));

        mockMvc.perform(get("/v1/providers/specialty/Internal Medicine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getDepartments_shouldReturnDistinctList() throws Exception {
        when(providerService.getAllDepartments()).thenReturn(List.of("Medicine", "Surgery"));

        mockMvc.perform(get("/v1/providers/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is("Medicine")));
    }

    @Test
    void getSpecialties_shouldReturnDistinctList() throws Exception {
        when(providerService.getAllSpecialties())
                .thenReturn(List.of("Internal Medicine", "Cardiology"));

        mockMvc.perform(get("/v1/providers/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void search_shouldReturnMatchingProviders() throws Exception {
        when(providerService.search("John")).thenReturn(List.of(provider));

        mockMvc.perform(get("/v1/providers/search").param("lastName", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void create_shouldReturn200() throws Exception {
        when(providerService.save(any(Provider.class))).thenReturn(provider);

        mockMvc.perform(post("/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npi", is("1234567890")));
    }

    @Test
    void update_shouldReturn200_whenExists() throws Exception {
        when(providerService.findById(1L)).thenReturn(Optional.of(provider));
        when(providerService.save(any(Provider.class))).thenReturn(provider);

        mockMvc.perform(put("/v1/providers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldReturn404_whenNotFound() throws Exception {
        when(providerService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/providers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivate_shouldReturn204() throws Exception {
        doNothing().when(providerService).deactivate(1L);

        mockMvc.perform(delete("/v1/providers/1"))
                .andExpect(status().isNoContent());

        verify(providerService).deactivate(1L);
    }
}
