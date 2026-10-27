package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceSortTest {

    @Mock
    private PatientRepository mockRepo;

    @Mock
    private PatientMapper mockMapper;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(mockRepo, mockMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(SELECT 1)",
            "last_name;--",
            "last_name desc; DROP TABLE patients",
            "(CASE WHEN (SELECT substr(ssn,1,1) FROM patients WHERE id=1)='1' THEN last_name ELSE first_name END)",
            "ssn",
            "last_name,sideways",
            "last_name,desc,first_name"
    })
    void rejectsUnsupportedSortParametersBeforeRepositoryCall(String sort) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> patientService.searchPatients("a", sort, PageRequest.of(0, 10)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Unsupported sort parameter", exception.getReason());
        verifyNoInteractions(mockRepo);
    }

    @Test
    void sortsLastNameDescendingAndPreservesPageAndSize() {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        patientService.searchPatients("a", "last_name,desc",
                PageRequest.of(2, 17, Sort.by(Sort.Direction.ASC, "ssn")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(17, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "lastName"), pageable.getSort());
    }

    @Test
    void sortsDateOfBirthAscendingByDefault() {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        patientService.searchPatients("a", "date_of_birth", PageRequest.of(0, 10));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "dateOfBirth"), pageableCaptor.getValue().getSort());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   \t"})
    void ignoresPageableSortWhenSortParameterIsNullOrBlank(String sort) {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(Page.<Patient>empty());

        patientService.searchPatients("a", sort,
                PageRequest.of(1, 12, Sort.by(Sort.Direction.DESC, "ssn")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(12, pageable.getPageSize());
        assertEquals(Sort.unsorted(), pageable.getSort());
    }

    @Test
    void preservesUnpagedRequestWhenSortIsNull() {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(Page.<Patient>empty());

        patientService.searchPatients("a", null, Pageable.unpaged());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        assertTrue(pageableCaptor.getValue().isUnpaged());
    }

    @Test
    void appliesSortToUnpagedRequest() {
        when(mockRepo.searchPatients(eq("a"), any(Pageable.class)))
                .thenReturn(Page.<Patient>empty());

        patientService.searchPatients("a", "last_name,desc", Pageable.unpaged());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mockRepo).searchPatients(eq("a"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(Sort.by(Sort.Direction.DESC, "lastName"), pageable.getSort());
    }

    @Test
    void rejectsUnsupportedSortForUnpagedRequestBeforeRepositoryCall() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> patientService.searchPatients("a", "(SELECT 1)", Pageable.unpaged()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Unsupported sort parameter", exception.getReason());
        verifyNoInteractions(mockRepo);
    }
}
