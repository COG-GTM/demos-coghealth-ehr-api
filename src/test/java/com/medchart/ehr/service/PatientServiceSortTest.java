package com.medchart.ehr.service;

import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PatientServiceSortTest {

    @Test
    void allowedSortsMapToFixedColumns() {
        assertThat(PatientService.toOrderBy("lastName")).isEqualTo("last_name ASC, id ASC");
        assertThat(PatientService.toOrderBy("lastName,desc")).isEqualTo("last_name DESC, id ASC");
        assertThat(PatientService.toOrderBy("last_name DESC")).isEqualTo("last_name DESC, id ASC");
        assertThat(PatientService.toOrderBy(" dateOfBirth , asc ")).isEqualTo("date_of_birth ASC, id ASC");
        assertThat(PatientService.toOrderBy("mrn")).isEqualTo("mrn ASC, id ASC");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ssn",
            "last_name; DROP TABLE patients",
            "(CASE WHEN (SELECT substr(ssn,1,1) FROM patients WHERE id=1)='1' THEN last_name ELSE mrn END)",
            "last_name,desc,mrn",
            "last_name sideways",
            "1",
            "last_name -- "
    })
    void anythingElseIsRejectedBeforeQuerying(String sort) {
        PatientRepository repository = mock(PatientRepository.class);
        PatientService service = new PatientService(repository, mock(PatientMapper.class));

        assertThatThrownBy(() -> service.searchPatients("smith", sort, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(repository);
    }
}
