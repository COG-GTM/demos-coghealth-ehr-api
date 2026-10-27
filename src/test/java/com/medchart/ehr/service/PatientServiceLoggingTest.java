package com.medchart.ehr.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientServiceLoggingTest {

    private static final String MRN = "MRN-SECRET-4242";

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PatientMapper patientMapper = mock(PatientMapper.class);
    private final PatientService patientService = new PatientService(patientRepository, patientMapper);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(PatientService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void createPatientLogsIdNotMrn() {
        PatientDTO dto = PatientDTO.builder().mrn(MRN).build();
        Patient patient = Patient.builder().mrn(MRN).build();
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.empty());
        when(patientMapper.toEntity(dto)).thenReturn(patient);
        when(patientRepository.save(patient)).thenAnswer(inv -> {
            patient.setId(17L);
            return patient;
        });

        patientService.createPatient(dto);

        assertLogsContainIdButNoMrn(17L);
    }

    @Test
    void updatePatientLogsIdNotMrn() {
        Patient existing = Patient.builder().id(23L).mrn(MRN).build();
        PatientDTO dto = PatientDTO.builder().mrn(MRN).build();
        when(patientRepository.findById(23L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenReturn(existing);

        patientService.updatePatient(23L, dto);

        assertLogsContainIdButNoMrn(23L);
    }

    @Test
    void unknownMrnLookupExceptionOmitsMrn() {
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> patientService.getPatientByMrn(MRN));

        assertEquals("Patient not found", ex.getMessage());
    }

    @Test
    void duplicateMrnExceptionOmitsMrn() {
        PatientDTO dto = PatientDTO.builder().mrn(MRN).build();
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.of(Patient.builder().id(5L).mrn(MRN).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> patientService.createPatient(dto));

        assertFalse(ex.getMessage().contains(MRN), ex.getMessage());
    }

    private void assertLogsContainIdButNoMrn(long id) {
        assertFalse(appender.list.isEmpty(), "expected a log line");
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(MRN), event.getFormattedMessage());
        }
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("id=" + id)));
    }
}
