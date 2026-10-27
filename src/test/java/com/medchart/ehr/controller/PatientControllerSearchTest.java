package com.medchart.ehr.controller;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatientControllerSearchTest {

    @Mock
    private PatientRepository mockRepo;

    @Mock
    private PatientMapper mockMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PatientService patientService = new PatientService(mockRepo, mockMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new PatientController(patientService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(SELECT 1)", "last_name;--"})
    void returnsBadRequestForUnsupportedSort(String sort) throws Exception {
        mockMvc.perform(get("/v1/patients/search")
                        .param("q", "a")
                        .param("sort", sort))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mockRepo);
    }

    @Test
    void acceptsAllowlistedSortAndPassesMappedSortToRepository() throws Exception {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.<Patient>emptyList()));

        mockMvc.perform(get("/v1/patients/search")
                        .param("q", "a")
                        .param("sort", "last_name,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "lastName"), pageableCaptor.getValue().getSort());
    }
}
