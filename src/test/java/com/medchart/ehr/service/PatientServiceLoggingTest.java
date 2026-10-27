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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientServiceLoggingTest {

    private static final String MRN = "MRN4417730021";

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PatientMapper patientMapper = mock(PatientMapper.class);
    private final PatientService patientService = new PatientService(patientRepository, patientMapper);

    private final Logger logger = (Logger) LoggerFactory.getLogger(PatientService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void createLogsPatientIdNotMrn() {
        PatientDTO dto = PatientDTO.builder().mrn(MRN).build();
        Patient entity = Patient.builder().mrn(MRN).build();
        Patient saved = Patient.builder().id(42L).mrn(MRN).build();
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.empty());
        when(patientMapper.toEntity(dto)).thenReturn(entity);
        when(patientRepository.save(entity)).thenReturn(saved);

        patientService.createPatient(dto);

        assertThat(logOutput()).contains("id=42").doesNotContain(MRN);
    }

    @Test
    void updateLogsPatientIdNotMrn() {
        Patient existing = Patient.builder().id(7L).mrn(MRN).build();
        when(patientRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenReturn(existing);

        patientService.updatePatient(7L, PatientDTO.builder().mrn(MRN).build());

        assertThat(logOutput()).contains("id=7").doesNotContain(MRN);
    }

    @Test
    void exceptionMessagesDoNotEchoMrn() {
        when(patientRepository.findByMrn(MRN)).thenReturn(Optional.of(Patient.builder().id(1L).mrn(MRN).build()));
        assertThatThrownBy(() -> patientService.createPatient(PatientDTO.builder().mrn(MRN).build()))
                .hasMessageNotContaining(MRN);

        when(patientRepository.findByMrn("MRN0000000000")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> patientService.getPatientByMrn("MRN0000000000"))
                .hasMessageNotContaining("MRN0000000000");
    }

    private String logOutput() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }
}
