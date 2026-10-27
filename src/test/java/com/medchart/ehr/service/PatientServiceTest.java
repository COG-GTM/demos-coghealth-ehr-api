package com.medchart.ehr.service;

import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository, patientMapper);
    }

    @Test
    void rejectsSqlExpressionWithoutCallingRepository() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> patientService.searchPatients("a", "(SELECT 1)", PageRequest.of(0, 20)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(patientRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ssn",
            "last_name; DROP TABLE patients",
            "(CASE WHEN (SELECT substr(ssn,1,1) FROM patients WHERE id=1)='1' THEN last_name ELSE mrn END)",
            "lastName,sideways",
            "lastName desc, ssn",
            "id"
    })
    void rejectsUnknownOrMalformedSort(String sort) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> patientService.searchPatients("a", sort, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(patientRepository);
    }

    @Test
    void sortsByAllowedFieldAndKeepsPageableWindow() {
        Pageable pageable = PageRequest.of(2, 10);
        when(patientRepository.searchPatients(anyString(), any(Pageable.class))).thenReturn(Page.empty());

        patientService.searchPatients("a", "lastName,desc", pageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository).searchPatients(eq("a"), pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(pageable.getPageNumber(), capturedPageable.getPageNumber());
        assertEquals(pageable.getPageSize(), capturedPageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "lastName"), capturedPageable.getSort());
    }

    @ParameterizedTest
    @CsvSource({
            "date_of_birth, dateOfBirth, ASC",
            "mrn ASC, mrn, ASC"
    })
    void supportsAllowedAliasesAndCaseInsensitiveDirection(String sort, String property, String direction) {
        when(patientRepository.searchPatients(anyString(), any(Pageable.class))).thenReturn(Page.empty());

        patientService.searchPatients("a", sort, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository).searchPatients(eq("a"), pageableCaptor.capture());
        assertEquals(Sort.by(Sort.Direction.valueOf(direction), property), pageableCaptor.getValue().getSort());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void passesOriginalPageableWhenSortIsNullOrBlank(String sort) {
        Pageable pageable = PageRequest.of(2, 10);
        when(patientRepository.searchPatients(anyString(), any(Pageable.class))).thenReturn(Page.empty());

        patientService.searchPatients("a", sort, pageable);

        verify(patientRepository).searchPatients(eq("a"), same(pageable));
    }
}
