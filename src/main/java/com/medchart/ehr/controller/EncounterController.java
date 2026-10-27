package com.medchart.ehr.controller;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterResponseDTO;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/encounters")
@PreAuthorize("hasAnyRole('PROVIDER', 'STAFF', 'ADMIN')")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EncounterResponseDTO> getById(@PathVariable Long id) {
        return okOrNotFound(encounterService.findByIdWithDetails(id));
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterResponseDTO> getByNumber(@PathVariable String encounterNumber) {
        return okOrNotFound(encounterService.findByEncounterNumber(encounterNumber));
    }

    @GetMapping("/patient/{patientId}")
    public List<EncounterResponseDTO> getByPatient(@PathVariable Long patientId) {
        return encounterService.findByPatientId(patientId);
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterResponseDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterService.findByPatientId(patientId, pageable);
    }

    @GetMapping("/provider/{providerId}")
    public List<EncounterResponseDTO> getByProvider(@PathVariable Long providerId) {
        return encounterService.findByProviderId(providerId);
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterResponseDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return encounterService.getProviderSchedule(providerId, date);
    }

    @GetMapping("/date-range")
    public List<EncounterResponseDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return encounterService.findByDateRange(startDate, endDate);
    }

    @GetMapping("/status/{status}")
    public List<EncounterResponseDTO> getByStatus(@PathVariable EncounterStatus status) {
        return encounterService.findByStatus(status);
    }

    @PostMapping
    public EncounterResponseDTO create(@RequestBody Encounter encounter) {
        return encounterService.create(encounter);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<EncounterResponseDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return okOrNotFound(encounterService.update(id, encounter));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable Long id) {
        return emptyOrNotFound(encounterService.checkIn(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        return emptyOrNotFound(encounterService.startEncounter(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<Void> complete(@PathVariable Long id, @RequestBody(required = false) String notes) {
        return emptyOrNotFound(encounterService.completeEncounter(id, notes));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        return emptyOrNotFound(encounterService.cancelEncounter(id));
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        return emptyOrNotFound(encounterService.markNoShow(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    private static ResponseEntity<EncounterResponseDTO> okOrNotFound(Optional<EncounterResponseDTO> encounter) {
        return encounter.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Void> emptyOrNotFound(Optional<EncounterResponseDTO> encounter) {
        return encounter.isPresent() ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
