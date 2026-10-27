package com.medchart.ehr.controller;

import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.InvalidEncounterStateException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EncounterDTO> getById(@PathVariable Long id) {
        return encounterService.findByIdWithDetails(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<EncounterDTO> getByPatient(@PathVariable Long patientId) {
        return encounterService.findByPatientId(patientId);
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterService.findByPatientId(patientId, pageable);
    }

    @GetMapping("/provider/{providerId}")
    public List<EncounterDTO> getByProvider(@PathVariable Long providerId) {
        return encounterService.findByProviderId(providerId);
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return encounterService.getProviderSchedule(providerId, date);
    }

    @GetMapping("/date-range")
    public List<EncounterDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return encounterService.findByDateRange(startDate, endDate);
    }

    @GetMapping("/status/{status}")
    public List<EncounterDTO> getByStatus(@PathVariable EncounterStatus status) {
        return encounterService.findByStatus(status);
    }

    @PostMapping
    public ResponseEntity<EncounterDTO> create(@Valid @RequestBody EncounterDTO encounter) {
        return ResponseEntity.status(HttpStatus.CREATED).body(encounterService.create(encounter));
    }

    @PutMapping("/{id}")
    public EncounterDTO update(@PathVariable Long id, @Valid @RequestBody EncounterDTO encounter) {
        return encounterService.update(id, encounter);
    }

    @PostMapping("/{id}/check-in")
    public EncounterDTO checkIn(@PathVariable Long id) {
        return encounterService.checkIn(id);
    }

    @PostMapping("/{id}/start")
    public EncounterDTO start(@PathVariable Long id) {
        return encounterService.startEncounter(id);
    }

    @PostMapping("/{id}/complete")
    public EncounterDTO complete(@PathVariable Long id, @RequestBody(required = false) String notes) {
        return encounterService.completeEncounter(id, notes);
    }

    @PostMapping("/{id}/cancel")
    public EncounterDTO cancel(@PathVariable Long id) {
        return encounterService.cancelEncounter(id);
    }

    @PostMapping("/{id}/no-show")
    public EncounterDTO noShow(@PathVariable Long id) {
        return encounterService.markNoShow(id);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidEncounterStateException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException e) {
        String message = e instanceof ObjectOptimisticLockingFailureException
                ? "Encounter was modified by another request; reload and retry"
                : e.getMessage();
        return error(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
