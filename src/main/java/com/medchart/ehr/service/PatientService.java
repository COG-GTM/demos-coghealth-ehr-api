package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.patient.IdentifierType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.PatientIdentifier;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.dto.PatientIdentifierDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityNotFoundException;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
    public PatientDTO getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));
        return toMaskedDto(patient);
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient by MRN")
    public PatientDTO getPatientByMrn(String mrn) {
        Patient patient = patientRepository.findByMrn(mrn)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with MRN: " + mrn));
        return toMaskedDto(patient);
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Search patients")
    public Page<PatientDTO> searchPatients(String searchTerm, Pageable pageable) {
        return patientRepository.searchPatients(searchTerm, pageable)
                .map(this::toMaskedDto);
    }

    @Transactional
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Patient", description = "Create patient record")
    public PatientDTO createPatient(PatientDTO patientDTO) {
        rejectMaskedSsns(patientDTO);
        if (patientDTO.getMrn() != null) {
            Optional<Patient> existing = patientRepository.findByMrn(patientDTO.getMrn());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Patient with MRN " + patientDTO.getMrn() + " already exists");
            }
        }

        Patient patient = patientMapper.toEntity(patientDTO);
        if (patient.getMrn() == null) {
            patient.setMrn(generateMrn());
        }

        Patient saved = patientRepository.save(patient);
        log.info("Created patient with MRN: {}", saved.getMrn());
        return toMaskedDto(saved);
    }

    @Transactional
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient", description = "Update patient record")
    public PatientDTO updatePatient(Long id, PatientDTO patientDTO) {
        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));

        restoreMaskedSsns(patientDTO, existing);
        patientMapper.updateEntityFromDto(patientDTO, existing);
        Patient saved = patientRepository.save(existing);
        log.info("Updated patient with MRN: {}", saved.getMrn());
        return toMaskedDto(saved);
    }

    private PatientDTO toMaskedDto(Patient patient) {
        return PatientIdentifierMasker.mask(patientMapper.toDto(patient));
    }

    private void rejectMaskedSsns(PatientDTO patientDTO) {
        if (patientDTO.getIdentifiers() != null
                && patientDTO.getIdentifiers().stream().anyMatch(PatientIdentifierMasker::isMaskedSsn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSN identifier value must not be masked");
        }
    }

    // Responses carry masked SSNs; a client that echoes one back keeps the stored value.
    private void restoreMaskedSsns(PatientDTO patientDTO, Patient existing) {
        if (patientDTO.getIdentifiers() == null) {
            return;
        }
        for (PatientIdentifierDTO identifier : patientDTO.getIdentifiers()) {
            if (!PatientIdentifierMasker.isMaskedSsn(identifier)) {
                continue;
            }
            String stored = existing.getIdentifiers().stream()
                    .filter(e -> e.getIdentifierType() == IdentifierType.SSN)
                    .filter(e -> identifier.getId() != null && identifier.getId().equals(e.getId()))
                    .map(PatientIdentifier::getIdentifierValue)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "SSN identifier value must not be masked"));
            identifier.setIdentifierValue(stored);
        }
    }

    /**
     * @deprecated SSN-based patient lookup is deprecated for HIPAA compliance.
     * Use getPatientByMrn() or searchPatients() instead.
     */
    @Deprecated
    public Optional<PatientDTO> findBySsn(String ssn) {
        throw new UnsupportedOperationException(
            "SSN-based patient lookup is disabled for HIPAA compliance. " +
            "Use MRN or name-based search instead.");
    }

    private String generateMrn() {
        return "MRN" + System.currentTimeMillis();
    }
}
