package com.medchart.ehr.service;

import com.medchart.ehr.domain.patient.IdentifierType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.PatientIdentifier;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.dto.PatientIdentifierDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PatientServiceIdentifierMaskingTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientMapper patientMapper;
    @InjectMocks
    private PatientService patientService;

    @Test
    void readsMaskSsnToLastFourAndLeaveOtherIdentifiers() {
        Patient patient = new Patient();
        given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
        given(patientMapper.toDto(patient)).willReturn(dto(
            identifier(10L, IdentifierType.SSN, "123-45-6789"),
            identifier(11L, IdentifierType.INSURANCE_MEMBER_ID, "BCX123456")));

        PatientDTO result = patientService.getPatientById(1L);

        assertEquals("***-**-6789", result.getIdentifiers().get(0).getIdentifierValue());
        assertEquals("BCX123456", result.getIdentifiers().get(1).getIdentifierValue());
    }

    @Test
    void mrnLookupAndSearchMaskSsn() {
        Patient patient = new Patient();
        given(patientRepository.findByMrn("MRN001")).willReturn(Optional.of(patient));
        given(patientRepository.searchPatients(any(), any()))
            .willReturn(new PageImpl<>(List.of(patient), PageRequest.of(0, 20), 1));
        given(patientMapper.toDto(patient))
            .willReturn(dto(identifier(10L, IdentifierType.SSN, "123456789")))
            .willReturn(dto(identifier(10L, IdentifierType.SSN, "12")));

        assertEquals("***-**-6789",
            patientService.getPatientByMrn("MRN001").getIdentifiers().get(0).getIdentifierValue());
        assertEquals("***-**-****",
            patientService.searchPatients("smi", PageRequest.of(0, 20)).getContent().get(0)
                .getIdentifiers().get(0).getIdentifierValue());
    }

    @Test
    void updateEchoingMaskedSsnKeepsStoredValue() {
        Patient existing = new Patient();
        PatientIdentifier stored = new PatientIdentifier();
        stored.setId(10L);
        stored.setIdentifierType(IdentifierType.SSN);
        stored.setIdentifierValue("123-45-6789");
        existing.setIdentifiers(new ArrayList<>(List.of(stored)));
        given(patientRepository.findById(17L)).willReturn(Optional.of(existing));
        given(patientRepository.save(existing)).willReturn(existing);
        given(patientMapper.toDto(existing)).willReturn(dto(identifier(10L, IdentifierType.SSN, "123-45-6789")));

        PatientDTO update = dto(identifier(10L, IdentifierType.SSN, "***-**-6789"));
        PatientDTO result = patientService.updatePatient(17L, update);

        ArgumentCaptor<PatientDTO> mapped = ArgumentCaptor.forClass(PatientDTO.class);
        verify(patientMapper).updateEntityFromDto(mapped.capture(), any());
        assertEquals("123-45-6789", mapped.getValue().getIdentifiers().get(0).getIdentifierValue());
        assertEquals("***-**-6789", result.getIdentifiers().get(0).getIdentifierValue());
    }

    @Test
    void updateWithMaskedSsnForUnknownIdentifierIsRejected() {
        Patient existing = new Patient();
        given(patientRepository.findById(17L)).willReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> patientService.updatePatient(17L, dto(identifier(99L, IdentifierType.SSN, "***-**-6789"))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createWithMaskedSsnIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> patientService.createPatient(dto(identifier(null, IdentifierType.SSN, "***-**-6789"))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createWithNoncanonicalRedactedSsnIsRejected() {
        for (String redacted : new String[] {"***-**-****", "*****6789", "XXX-**-678*", "***-**-6789 "}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> patientService.createPatient(dto(identifier(null, IdentifierType.SSN, redacted))));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
        verify(patientRepository, never()).save(any());
    }

    @Test
    void patientEntityJsonOmitsSsnAndIdentifiers() throws Exception {
        Patient patient = new Patient();
        patient.setMrn("MRN001");
        patient.setSsn("123-45-6789");
        PatientIdentifier stored = new PatientIdentifier();
        stored.setIdentifierType(IdentifierType.SSN);
        stored.setIdentifierValue("123-45-6789");
        patient.setIdentifiers(new ArrayList<>(List.of(stored)));

        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(patient);

        assertFalse(json.contains("6789"), json);
        assertTrue(json.contains("MRN001"));
    }

    private static PatientDTO dto(PatientIdentifierDTO... identifiers) {
        return PatientDTO.builder()
            .firstName("Bob")
            .lastName("Smith")
            .identifiers(new ArrayList<>(Arrays.asList(identifiers)))
            .build();
    }

    private static PatientIdentifierDTO identifier(Long id, IdentifierType type, String value) {
        return PatientIdentifierDTO.builder().id(id).identifierType(type).identifierValue(value).build();
    }
}
