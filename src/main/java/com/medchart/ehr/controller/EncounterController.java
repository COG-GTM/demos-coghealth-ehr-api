package com.medchart.ehr.controller;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
            @AuthenticationPrincipal User user) {
        return encounterService.findByIdForRequester(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<Encounter> getByNumber(
            @PathVariable String encounterNumber,
            @AuthenticationPrincipal User user) {
        return encounterService.findByEncounterNumber(encounterNumber, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<Encounter> getByPatient(
            @PathVariable Long patientId,
            @AuthenticationPrincipal User user) {
        return encounterService.findByPatientId(patientId, user);
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<Encounter> getByPatientPaged(
            @PathVariable Long patientId,
            Pageable pageable,
            @AuthenticationPrincipal User user) {
        return encounterService.findByPatientId(patientId, pageable, user);
    }

    @GetMapping("/provider/{providerId}")
    public List<Encounter> getByProvider(
            @PathVariable Long providerId,
            @AuthenticationPrincipal User user) {
        return encounterService.findByProviderId(providerId, user);
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<Encounter> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user) {
        return encounterService.getProviderSchedule(providerId, date, user);
    }

    @GetMapping("/date-range")
    public List<Encounter> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user) {
        return encounterService.findByDateRange(startDate, endDate, user);
    }

    @GetMapping("/status/{status}")
    public List<Encounter> getByStatus(
            @PathVariable EncounterStatus status,
            @AuthenticationPrincipal User user) {
        return encounterService.findByStatus(status, user);
    }

    @PostMapping
    public Encounter create(
            @RequestBody Encounter encounter,
            @AuthenticationPrincipal User user) {
        return encounterService.create(encounter, user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Encounter> update(
            @PathVariable Long id,
            @RequestBody Encounter encounter,
            @AuthenticationPrincipal User user) {
        return encounterService.update(id, encounter, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<Void> checkIn(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return encounterService.checkIn(id, user) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return encounterService.startEncounter(id, user) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable Long id,
            @RequestBody(required = false) String notes,
            @AuthenticationPrincipal User user) {
        return encounterService.completeEncounter(id, notes, user)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return encounterService.cancelEncounter(id, user) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> noShow(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return encounterService.markNoShow(id, user) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
