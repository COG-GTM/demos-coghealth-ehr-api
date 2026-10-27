package com.medchart.ehr.controller;

import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patient", description = "Patient management endpoints")
@PreAuthorize(PatientController.CAN_READ)
public class PatientController {

    static final String CAN_READ = "hasAnyRole('PROVIDER','STAFF','ADMIN')";
    static final String CAN_WRITE = "hasAnyRole('STAFF','ADMIN')";
    static final int MIN_SEARCH_LENGTH = 2;
    static final int MAX_PAGE_SIZE = 100;

    private final PatientService patientService;

    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID")
    public ResponseEntity<PatientDTO> getPatient(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    @GetMapping("/mrn/{mrn}")
    @Operation(summary = "Get patient by MRN")
    public ResponseEntity<PatientDTO> getPatientByMrn(@PathVariable String mrn) {
        return ResponseEntity.ok(patientService.getPatientByMrn(mrn));
    }

    @GetMapping("/search")
    @Operation(summary = "Search patients")
    public ResponseEntity<Page<PatientDTO>> searchPatients(
            @RequestParam String q,
            Pageable pageable) {
        String term = q.trim();
        if (term.length() < MIN_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Search term must be at least " + MIN_SEARCH_LENGTH + " characters");
        }
        Pageable bounded = pageable.getPageSize() > MAX_PAGE_SIZE
                ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
                : pageable;
        return ResponseEntity.ok(patientService.searchPatients(term, bounded));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Create new patient")
    public ResponseEntity<PatientDTO> createPatient(@Valid @RequestBody PatientDTO patientDTO) {
        PatientDTO created = patientService.createPatient(patientDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update patient")
    public ResponseEntity<PatientDTO> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody PatientDTO patientDTO) {
        return ResponseEntity.ok(patientService.updatePatient(id, patientDTO));
    }
}
