package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceMrnTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService patientService;

    @Test
    void generatedMrnsAreNotDerivedFromTheClock() {
        long before = System.currentTimeMillis();
        String mrn = patientService.generateMrn();
        long after = System.currentTimeMillis();

        assertThat(mrn).startsWith("MRN").hasSize(16);
        String suffix = mrn.substring(3);
        assertThat(suffix).matches("[0-9A-HJKMNP-TV-Z]{13}");

        for (long millis = before; millis <= after; millis++) {
            assertThat(mrn).isNotEqualTo("MRN" + millis);
        }
    }

    @Test
    void generatedMrnsAreUnpredictableAndUnique() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(patientService.generateMrn());
        }

        assertThat(generated).hasSize(1000);

        // Consecutive identifiers must not walk a narrow, guessable range.
        String first = patientService.generateMrn();
        String second = patientService.generateMrn();
        assertThat(commonPrefixLength(first, second)).isLessThan(8);
    }

    @Test
    void createPatientAssignsAnOpaqueMrnWhenNoneSupplied() {
        PatientDTO request = new PatientDTO();
        Patient entity = new Patient();
        when(patientMapper.toEntity(request)).thenReturn(entity);
        when(patientRepository.findByMrn(anyString())).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientMapper.toDto(any(Patient.class))).thenReturn(request);

        patientService.createPatient(request);

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        org.mockito.Mockito.verify(patientRepository).save(saved.capture());
        String mrn = saved.getValue().getMrn();
        assertThat(mrn).matches("MRN[0-9A-HJKMNP-TV-Z]{13}");
        assertThat(mrn.substring(3)).doesNotMatch("\\d{13}");
    }

    private static int commonPrefixLength(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
