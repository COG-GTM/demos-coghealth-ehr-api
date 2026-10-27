package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "last_name", "lastName",
            "lastName", "lastName",
            "first_name", "firstName",
            "firstName", "firstName",
            "mrn", "mrn",
            "date_of_birth", "dateOfBirth",
            "dateOfBirth", "dateOfBirth");

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
    public PatientDTO getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with id: " + id));
        return patientMapper.toDto(patient);
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient by MRN")
    public PatientDTO getPatientByMrn(String mrn) {
        Patient patient = patientRepository.findByMrn(mrn)
                .orElseThrow(() -> new EntityNotFoundException("Patient not found with MRN: " + mrn));
        return patientMapper.toDto(patient);
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Search patients")
    public Page<PatientDTO> searchPatients(String searchTerm, String sort, Pageable pageable) {
        if (sort == null || sort.isBlank()) {
            return patientRepository.searchPatients(searchTerm,
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                    .map(patientMapper::toDto);
        }

        Sort resolvedSort = resolveSort(sort);
        return patientRepository.searchPatients(searchTerm,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolvedSort))
                .map(patientMapper::toDto);
    }

    private Sort resolveSort(String sort) {
        String[] tokens = sort.trim().split("[,\\s]+", -1);
        if (tokens.length < 1 || tokens.length > 2) {
            throw unsupportedSortParameter();
        }

        String property = SORTABLE_FIELDS.get(tokens[0]);
        if (property == null) {
            throw unsupportedSortParameter();
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (tokens.length == 2) {
            if ("desc".equalsIgnoreCase(tokens[1])) {
                direction = Sort.Direction.DESC;
            } else if (!"asc".equalsIgnoreCase(tokens[1])) {
                throw unsupportedSortParameter();
            }
        }

        return Sort.by(direction, property);
    }

    private ResponseStatusException unsupportedSortParameter() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort parameter");
    }

    @Transactional
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Patient", description = "Create patient record")
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
