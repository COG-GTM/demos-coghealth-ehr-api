package com.medchart.ehr.controller;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.ProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProviderController.class)
@DisplayName("ProviderController Integration Tests")
class ProviderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    private ObjectMapper objectMapper;
    private Provider sampleProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleProvider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Jane")
                .lastName("Smith")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("GET /v1/providers")
    class GetAll {

        @Test
        @WithMockUser
        @DisplayName("should return all providers")
        void shouldReturnAllProviders() throws Exception {
            when(providerService.findAll()).thenReturn(List.of(sampleProvider));

            mockMvc.perform(get("/v1/providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].npi").value("1234567890"))
                    .andExpect(jsonPath("$[0].firstName").value("Jane"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return active providers when active=true")
        void shouldReturnActiveProviders() throws Exception {
            when(providerService.findActive()).thenReturn(List.of(sampleProvider));

            mockMvc.perform(get("/v1/providers").param("active", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].active").value(true));
        }
    }

    @Nested
    @DisplayName("GET /v1/providers/{id}")
    class GetById {

        @Test
        @WithMockUser
        @DisplayName("should return provider when found")
        void shouldReturnProviderWhenFound() throws Exception {
            when(providerService.findById(1L)).thenReturn(Optional.of(sampleProvider));

            mockMvc.perform(get("/v1/providers/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.npi").value("1234567890"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when provider not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(providerService.findById(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/providers/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/providers/npi/{npi}")
    class GetByNpi {

        @Test
        @WithMockUser
        @DisplayName("should return provider by NPI")
        void shouldReturnProviderByNpi() throws Exception {
            when(providerService.findByNpi("1234567890")).thenReturn(Optional.of(sampleProvider));

            mockMvc.perform(get("/v1/providers/npi/1234567890"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.npi").value("1234567890"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 for unknown NPI")
        void shouldReturn404ForUnknownNpi() throws Exception {
            when(providerService.findByNpi("0000000000")).thenReturn(Optional.empty());

            mockMvc.perform(get("/v1/providers/npi/0000000000"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /v1/providers/department/{department}")
    class GetByDepartment {

        @Test
        @WithMockUser
        @DisplayName("should return providers in department")
        void shouldReturnProvidersInDepartment() throws Exception {
            when(providerService.findByDepartment("Medicine")).thenReturn(List.of(sampleProvider));

            mockMvc.perform(get("/v1/providers/department/Medicine"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].department").value("Medicine"));
        }
    }

    @Nested
    @DisplayName("GET /v1/providers/departments")
    class GetDepartments {

        @Test
        @WithMockUser
        @DisplayName("should return all departments")
        void shouldReturnAllDepartments() throws Exception {
            when(providerService.getAllDepartments()).thenReturn(List.of("Medicine", "Surgery"));

            mockMvc.perform(get("/v1/providers/departments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Medicine"))
                    .andExpect(jsonPath("$[1]").value("Surgery"));
        }
    }

    @Nested
    @DisplayName("GET /v1/providers/search")
    class Search {

        @Test
        @WithMockUser
        @DisplayName("should search providers by last name")
        void shouldSearchByLastName() throws Exception {
            when(providerService.search("Smith")).thenReturn(List.of(sampleProvider));

            mockMvc.perform(get("/v1/providers/search").param("lastName", "Smith"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].lastName").value("Smith"));
        }
    }

    @Nested
    @DisplayName("POST /v1/providers")
    class Create {

        @Test
        @WithMockUser
        @DisplayName("should create provider")
        void shouldCreateProvider() throws Exception {
            when(providerService.save(any(Provider.class))).thenReturn(sampleProvider);

            mockMvc.perform(post("/v1/providers")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleProvider)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.npi").value("1234567890"));
        }
    }

    @Nested
    @DisplayName("DELETE /v1/providers/{id}")
    class Deactivate {

        @Test
        @WithMockUser
        @DisplayName("should deactivate provider")
        void shouldDeactivateProvider() throws Exception {
            mockMvc.perform(delete("/v1/providers/1").with(csrf()))
                    .andExpect(status().isNoContent());

            verify(providerService).deactivate(1L);
        }
    }
}
