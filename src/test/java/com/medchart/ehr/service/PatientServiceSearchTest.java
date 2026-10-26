package com.medchart.ehr.service;

import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientServiceSearchTest {

    @Test
    void likeWildcardsAreSearchedLiterally() {
        assertEquals("!%!%", PatientService.escapeLikePattern("%%"));
        assertEquals("!_!_", PatientService.escapeLikePattern("__"));
        assertEquals("a!!b", PatientService.escapeLikePattern("a!b"));
        assertEquals("doe", PatientService.escapeLikePattern("doe"));
    }

    @Test
    void searchPassesEscapedTermToRepository() {
        PatientRepository repository = mock(PatientRepository.class);
        when(repository.searchPatients(anyString(), any())).thenReturn(Page.empty());
        PatientService service = new PatientService(repository, mock(PatientMapper.class));

        service.searchPatients("%%", PageRequest.of(0, 10));

        verify(repository).searchPatients("!%!%", PageRequest.of(0, 10));
    }
}
