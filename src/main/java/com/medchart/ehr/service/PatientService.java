package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.config.PatientAccess;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {

    static final int MIN_SEARCH_TERM_LENGTH = 2;
    static final int MAX_SEARCH_TERM_LENGTH = 100;
    static final int MAX_SEARCH_PAGE_SIZE = 50;

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
    @PreAuthorize(PatientAccess.READ)
    public PatientDTO getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));
        return patientMapper.toDto(patient);
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient by MRN")
    @PreAuthorize(PatientAccess.READ)
    public PatientDTO getPatientByMrn(String mrn) {
        Patient patient = patientRepository.findByMrn(mrn)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with MRN: " + mrn));
        return patientMapper.toDto(patient);
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Search patients")
    @PreAuthorize(PatientAccess.READ)
    public Page<PatientDTO> searchPatients(String searchTerm, Pageable pageable) {
        String term = searchTerm == null ? "" : searchTerm.trim();
        if (term.length() < MIN_SEARCH_TERM_LENGTH || term.length() > MAX_SEARCH_TERM_LENGTH) {
            throw new IllegalArgumentException("Search term must be between " + MIN_SEARCH_TERM_LENGTH
                    + " and " + MAX_SEARCH_TERM_LENGTH + " characters");
        }
        return patientRepository.searchPatients(escapeLike(term), boundPage(pageable))
                .map(patientMapper::toDto);
    }

    static String escapeLike(String term) {
        return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static Pageable boundPage(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_SEARCH_PAGE_SIZE);
        }
        return PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_SEARCH_PAGE_SIZE), pageable.getSort());
    }

    @Transactional
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Patient", description = "Create patient record")
    @PreAuthorize(PatientAccess.WRITE)
    public PatientDTO createPatient(PatientDTO patientDTO) {
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
        return patientMapper.toDto(saved);
    }

    @Transactional
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient", description = "Update patient record")
    @PreAuthorize(PatientAccess.WRITE)
    public PatientDTO updatePatient(Long id, PatientDTO patientDTO) {
        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));

        patientMapper.updateEntityFromDto(patientDTO, existing);
        Patient saved = patientRepository.save(existing);
        log.info("Updated patient with MRN: {}", saved.getMrn());
        return patientMapper.toDto(saved);
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
