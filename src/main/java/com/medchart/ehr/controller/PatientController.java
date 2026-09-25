package com.medchart.ehr.controller;

import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patient", description = "Patient management endpoints")
public class PatientController {

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
        return ResponseEntity.ok(patientService.searchPatients(q, pageable));
    }

    @PostMapping
    @Operation(summary = "Create new patient")
    public ResponseEntity<PatientDTO> createPatient(@Valid @RequestBody PatientDTO patientDTO) {
        PatientDTO created = patientService.createPatient(patientDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update patient")
    public ResponseEntity<PatientDTO> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody PatientDTO patientDTO) {
        return ResponseEntity.ok(patientService.updatePatient(id, patientDTO));
    }
}
