package com.medchart.ehr.controller;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for clinical encounter management.
 *
 * Provides CRUD operations and workflow transitions (check-in, start,
 * complete, cancel, no-show) for patient encounters. All access to
 * encounter data is audit-logged for HIPAA compliance.
 */
@RestController
@RequestMapping("/v1/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Encounter> getById(@PathVariable Long id) {
        return encounterService.findByIdWithDetails(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<Encounter> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<Encounter> getByPatient(@PathVariable Long patientId) {
        return encounterService.findByPatientId(patientId);
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<Encounter> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterService.findByPatientId(patientId, pageable);
    }

    @GetMapping("/provider/{providerId}")
    public List<Encounter> getByProvider(@PathVariable Long providerId) {
        return encounterService.findByProviderId(providerId);
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<Encounter> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return encounterService.getProviderSchedule(providerId, date);
    }

    @GetMapping("/date-range")
    public List<Encounter> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return encounterService.findByDateRange(startDate, endDate);
    }

    @GetMapping("/status/{status}")
    public List<Encounter> getByStatus(@PathVariable EncounterStatus status) {
        return encounterService.findByStatus(status);
    }

    @PostMapping
    public Encounter create(@RequestBody Encounter encounter) {
        return encounterService.create(encounter);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Encounter> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    return ResponseEntity.ok(encounterService.update(encounter));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable Long id) {
        encounterService.checkIn(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        encounterService.startEncounter(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long id, @RequestBody(required = false) String notes) {
        encounterService.completeEncounter(id, notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        encounterService.cancelEncounter(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        encounterService.markNoShow(id);
        return ResponseEntity.ok().build();
    }
}
