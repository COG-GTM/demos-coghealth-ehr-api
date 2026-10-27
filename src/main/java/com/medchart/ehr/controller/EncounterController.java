package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/encounters")
@PreAuthorize("hasAnyRole('PROVIDER', 'STAFF', 'ADMIN')")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter")
    @GetMapping("/{id}")
    public ResponseEntity<EncounterDTO> getById(@PathVariable Long id) {
        return encounterService.findByIdWithDetails(id)
                .map(EncounterDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(EncounterDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters")
    @GetMapping("/patient/{patientId}")
    public List<EncounterDTO> getByPatient(@PathVariable Long patientId) {
        return toDtos(encounterService.findByPatientId(patientId));
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters (paged)")
    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterService.findByPatientId(patientId, pageable).map(EncounterDTO::from);
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "List provider encounters")
    @GetMapping("/provider/{providerId}")
    public List<EncounterDTO> getByProvider(@PathVariable Long providerId) {
        return toDtos(encounterService.findByProviderId(providerId));
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "View provider schedule")
    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return toDtos(encounterService.getProviderSchedule(providerId, date));
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by date range")
    @GetMapping("/date-range")
    public List<EncounterDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return toDtos(encounterService.findByDateRange(startDate, endDate));
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by status")
    @GetMapping("/status/{status}")
    public List<EncounterDTO> getByStatus(@PathVariable EncounterStatus status) {
        return toDtos(encounterService.findByStatus(status));
    }

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
    @PostMapping
    public EncounterDTO create(@RequestBody Encounter encounter) {
        return EncounterDTO.from(encounterService.create(encounter));
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Update encounter")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<EncounterDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    return ResponseEntity.ok(EncounterDTO.from(encounterService.update(encounter)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Check in encounter")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable Long id) {
        encounterService.checkIn(id);
        return ResponseEntity.ok().build();
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Start encounter")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        encounterService.startEncounter(id);
        return ResponseEntity.ok().build();
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Complete encounter")
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<Void> complete(@PathVariable Long id, @RequestBody(required = false) String notes) {
        encounterService.completeEncounter(id, notes);
        return ResponseEntity.ok().build();
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Cancel encounter")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        encounterService.cancelEncounter(id);
        return ResponseEntity.ok().build();
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Mark encounter no-show")
    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        encounterService.markNoShow(id);
        return ResponseEntity.ok().build();
    }

    private static List<EncounterDTO> toDtos(List<Encounter> encounters) {
        return encounters.stream().map(EncounterDTO::from).collect(Collectors.toList());
    }
}
