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

import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientServiceLoggingTest {

    private static final String MRN = "MRN-0001234";

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PatientMapper patientMapper = mock(PatientMapper.class);
    private final PatientService patientService = new PatientService(patientRepository, patientMapper);

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(PatientService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        serviceLogger.detachAppender(appender);
    }

    @Test
    void createPatientLogsIdNotMrn() {
        PatientDTO dto = PatientDTO.builder().mrn(MRN).build();
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.empty());
        when(patientMapper.toEntity(dto)).thenReturn(Patient.builder().mrn(MRN).build());
        when(patientRepository.save(any(Patient.class))).thenReturn(Patient.builder().id(42L).mrn(MRN).build());

        patientService.createPatient(dto);

        assertEquals("Created patient record id=42", loggedMessages());
    }

    @Test
    void updatePatientLogsIdNotMrn() {
        Patient existing = Patient.builder().id(7L).mrn(MRN).build();
        when(patientRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(existing)).thenReturn(existing);

        patientService.updatePatient(7L, PatientDTO.builder().build());

        assertEquals("Updated patient record id=7", loggedMessages());
    }

    private String loggedMessages() {
        String messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(messages.contains(MRN), "MRN must not be logged: " + messages);
        return messages;
    }
}
