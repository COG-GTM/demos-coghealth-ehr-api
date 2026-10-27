package com.medchart.ehr.controller;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Encounter> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByIdForRequester(id, requester)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<Encounter> getByNumber(
            @PathVariable String encounterNumber,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByEncounterNumberForRequester(encounterNumber, requester)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<Encounter> getByPatient(@PathVariable Long patientId, @AuthenticationPrincipal User requester) {
        return encounterService.findByPatientId(patientId, requester);
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<Encounter> getByPatientPaged(
            @PathVariable Long patientId,
            Pageable pageable,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByPatientId(patientId, pageable, requester);
    }

    @GetMapping("/provider/{providerId}")
    public List<Encounter> getByProvider(
            @PathVariable Long providerId,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByProviderId(providerId, requester);
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<Encounter> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User requester) {
        return encounterService.getProviderSchedule(providerId, date, requester);
    }

    @GetMapping("/date-range")
    public List<Encounter> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByDateRange(startDate, endDate, requester);
    }

    @GetMapping("/status/{status}")
    public List<Encounter> getByStatus(
            @PathVariable EncounterStatus status,
            @AuthenticationPrincipal User requester) {
        return encounterService.findByStatus(status, requester);
    }

    @PostMapping
    public Encounter create(@RequestBody Encounter encounter, @AuthenticationPrincipal User requester) {
        return encounterService.create(encounter, requester);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Encounter> update(
            @PathVariable Long id,
            @RequestBody Encounter encounter,
            @AuthenticationPrincipal User requester) {
        return encounterService.update(id, encounter, requester)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        return encounterService.checkIn(id, requester)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        return encounterService.startEncounter(id, requester)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable Long id,
            @RequestBody(required = false) String notes,
            @AuthenticationPrincipal User requester) {
        return encounterService.completeEncounter(id, notes, requester)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        return encounterService.cancelEncounter(id, requester)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        return encounterService.markNoShow(id, requester)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied() {
        return ResponseEntity.status(403).build();
    }
}
