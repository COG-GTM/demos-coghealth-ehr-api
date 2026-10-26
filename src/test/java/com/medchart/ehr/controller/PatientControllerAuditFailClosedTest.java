package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAspect;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditEventRepository;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.audit.RecordingTransactionManager;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientControllerAuditFailClosedTest {

    private AuditEventRepository auditEventRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PatientRepository patientRepository = mock(PatientRepository.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        Patient patient = new Patient();
        when(patientRepository.findById(42L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(
                PatientDTO.builder().id(42L).mrn("MRN0000042").firstName("Jane").lastName("Doe").build());

        auditEventRepository = mock(AuditEventRepository.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new PatientService(patientRepository, patientMapper));
        factory.setProxyTargetClass(true);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        factory.addAspect(new AuditAspect(
                transactionManager.transactional(new AuditService(auditEventRepository)), transactionManager));
        PatientService auditedService = factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(new PatientController(auditedService)).build();
    }

    @Test
    void patientRecordIsReturnedWhenAuditIsPersisted() throws Exception {
        mockMvc.perform(get("/v1/patients/42"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Doe")));

        verify(auditEventRepository).saveAndFlush(any(AuditEvent.class));
    }

    @Test
    void auditFailureReturnsErrorInsteadOfPatientRecord() throws Exception {
        when(auditEventRepository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        MvcResult result = mockMvc.perform(get("/v1/patients/42"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Doe"));
        assertFalse(body.contains("MRN0000042"));
    }
}
