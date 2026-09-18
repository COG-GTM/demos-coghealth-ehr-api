package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProviderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProviderService providerService;

    private Provider provider(Long id, String npi, boolean active) {
        return Provider.builder()
                .id(id)
                .npi(npi)
                .firstName("Ada")
                .lastName("Lovelace")
                .providerType(ProviderType.PHYSICIAN)
                .department("CARDIOLOGY")
                .specialty("Cardiology")
                .active(active)
                .build();
    }

    @Test
    void getAllReturnsActiveProvidersWhenActiveFlagTrue() throws Exception {
        when(providerService.findActive()).thenReturn(List.of(provider(1L, "1111111111", true)));

        mockMvc.perform(get("/v1/providers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].npi").value("1111111111"));

        verify(providerService).findActive();
        verify(providerService, never()).findAll();
    }

    @Test
    void getAllReturnsAllProvidersWhenActiveFlagFalse() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(provider(1L, "1111111111", true), provider(2L, "2222222222", false)));

        mockMvc.perform(get("/v1/providers").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(providerService).findAll();
        verify(providerService, never()).findActive();
    }

    @Test
    void getAllReturnsAllProvidersWhenActiveFlagAbsent() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(provider(1L, "1111111111", true)));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(providerService).findAll();
        verify(providerService, never()).findActive();
    }

    @Test
    void getByIdReturnsProviderWhenFound() throws Exception {
        when(providerService.findById(7L)).thenReturn(Optional.of(provider(7L, "3333333333", true)));

        mockMvc.perform(get("/v1/providers/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        when(providerService.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/{id}", 7L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNpiReturnsNotFoundWhenMissing() throws Exception {
        when(providerService.findByNpi("9999999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/providers/npi/{npi}", "9999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchDelegatesLastNameToService() throws Exception {
        when(providerService.search("love")).thenReturn(List.of(provider(1L, "1111111111", true)));

        mockMvc.perform(get("/v1/providers/search").param("lastName", "love"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(providerService).search("love");
    }

    @Test
    void searchWithoutLastNameReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/providers/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSavesProvider() throws Exception {
        Provider request = provider(null, "4444444444", true);
        when(providerService.save(any(Provider.class))).thenReturn(provider(9L, "4444444444", true));

        mockMvc.perform(post("/v1/providers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));

        verify(providerService).save(any(Provider.class));
    }

    @Test
    void updateReturnsOkAndPreservesPathIdWhenProviderExists() throws Exception {
        when(providerService.findById(5L)).thenReturn(Optional.of(provider(5L, "5555555555", true)));
        when(providerService.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Provider request = provider(99L, "5555555555", true);

        mockMvc.perform(put("/v1/providers/{id}", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updateReturnsNotFoundAndDoesNotSaveWhenProviderMissing() throws Exception {
        when(providerService.findById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/providers/{id}", 5L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(provider(5L, "5555555555", true))))
                .andExpect(status().isNotFound());

        verify(providerService, never()).save(any(Provider.class));
    }

    @Test
    void deactivateReturnsNoContentAndDelegatesId() throws Exception {
        mockMvc.perform(delete("/v1/providers/{id}", 42L))
                .andExpect(status().isNoContent());

        verify(providerService, times(1)).deactivate(eq(42L));
    }

    @Test
    void departmentAndSpecialtyLookupsDelegateToService() throws Exception {
        when(providerService.findByDepartment("CARDIOLOGY")).thenReturn(List.of(provider(1L, "1111111111", true)));
        when(providerService.findBySpecialty("Cardiology")).thenReturn(Collections.emptyList());
        when(providerService.getAllDepartments()).thenReturn(List.of("CARDIOLOGY"));
        when(providerService.getAllSpecialties()).thenReturn(List.of("Cardiology"));

        mockMvc.perform(get("/v1/providers/department/{department}", "CARDIOLOGY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/v1/providers/specialty/{specialty}", "Cardiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/v1/providers/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("CARDIOLOGY"));
        mockMvc.perform(get("/v1/providers/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Cardiology"));
    }
}
