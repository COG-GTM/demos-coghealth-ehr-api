package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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
    private com.medchart.ehr.config.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;
    private Provider provider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .acceptingPatients(true)
                .build();
    }

    @Test
    void getAll_noActiveFilter_returnsAllProviders() throws Exception {
        when(providerService.findAll()).thenReturn(Collections.singletonList(provider));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].npi").value("1234567890"))
                .andExpect(jsonPath("$[0].firstName").value("Alice"));
    }

    @Test
    void getAll_activeTrue_returnsActiveProviders() throws Exception {
        when(providerService.findActive()).thenReturn(Collections.singletonList(provider));

        mockMvc.perform(get("/v1/providers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void getById_existingId_returns200() throws Exception {
        when(providerService.findById(1L)).thenReturn(Optional.of(provider));

        mockMvc.perform(get("/v1/providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npi").value("1234567890"));
    }

    @Test
    void getById_nonExistingId_returns404() throws Exception {
        when(providerService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNpi_existingNpi_returns200() throws Exception {
        when(providerService.findByNpi("1234567890")).thenReturn(Optional.of(provider));

        mockMvc.perform(get("/v1/providers/npi/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void getByNpi_nonExistingNpi_returns404() throws Exception {
        when(providerService.findByNpi("0000000000")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/npi/0000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByDepartment_returns200() throws Exception {
        when(providerService.findByDepartment("Medicine")).thenReturn(Collections.singletonList(provider));

        mockMvc.perform(get("/v1/providers/department/Medicine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].department").value("Medicine"));
    }

    @Test
    void getBySpecialty_returns200() throws Exception {
        when(providerService.findBySpecialty("Internal Medicine")).thenReturn(Collections.singletonList(provider));

        mockMvc.perform(get("/v1/providers/specialty/Internal Medicine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].specialty").value("Internal Medicine"));
    }

    @Test
    void getDepartments_returns200() throws Exception {
        when(providerService.getAllDepartments()).thenReturn(Arrays.asList("Medicine", "Surgery"));

        mockMvc.perform(get("/v1/providers/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Medicine"))
                .andExpect(jsonPath("$[1]").value("Surgery"));
    }

    @Test
    void getSpecialties_returns200() throws Exception {
        when(providerService.getAllSpecialties()).thenReturn(Arrays.asList("Internal Medicine", "Family Medicine"));

        mockMvc.perform(get("/v1/providers/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Internal Medicine"));
    }

    @Test
    void search_returns200() throws Exception {
        when(providerService.search("Smith")).thenReturn(Collections.singletonList(provider));

        mockMvc.perform(get("/v1/providers/search").param("lastName", "Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Smith"));
    }

    @Test
    void create_returns200() throws Exception {
        when(providerService.save(any(Provider.class))).thenReturn(provider);

        mockMvc.perform(post("/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npi").value("1234567890"));
    }

    @Test
    void update_existingId_returns200() throws Exception {
        when(providerService.findById(1L)).thenReturn(Optional.of(provider));
        when(providerService.save(any(Provider.class))).thenReturn(provider);

        mockMvc.perform(put("/v1/providers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isOk());
    }

    @Test
    void update_nonExistingId_returns404() throws Exception {
        when(providerService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/providers/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(provider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivate_returns204() throws Exception {
        doNothing().when(providerService).deactivate(1L);

        mockMvc.perform(delete("/v1/providers/1"))
                .andExpect(status().isNoContent());

        verify(providerService).deactivate(1L);
    }
}
