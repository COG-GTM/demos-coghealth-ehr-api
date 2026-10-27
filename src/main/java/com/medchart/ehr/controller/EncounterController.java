package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterResponseDTO;
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

    private static final String CLINICAL_ROLES = "hasAnyRole('PROVIDER', 'STAFF')";

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @GetMapping("/{id}")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter")
    public ResponseEntity<EncounterResponseDTO> getById(
            @PathVariable Long id,
            @RequestParam(required = false) Long providerId) {
        return encounterService.findByIdForProvider(id, providerId)
                .map(EncounterResponseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    public ResponseEntity<EncounterResponseDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(EncounterResponseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters")
    public List<EncounterResponseDTO> getByPatient(@PathVariable Long patientId) {
        return toResponse(encounterService.findByPatientId(patientId));
    }

    @GetMapping("/patient/{patientId}/paged")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters (paged)")
    public Page<EncounterResponseDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterService.findByPatientId(patientId, pageable).map(EncounterResponseDTO::from);
    }

    @GetMapping("/provider/{providerId}")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List provider encounters")
    public List<EncounterResponseDTO> getByProvider(@PathVariable Long providerId) {
        return toResponse(encounterService.findByProviderId(providerId));
    }

    @GetMapping("/provider/{providerId}/schedule")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View provider schedule")
    public List<EncounterResponseDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return toResponse(encounterService.getProviderSchedule(providerId, date));
    }

    @GetMapping("/date-range")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List encounters by date range")
    public List<EncounterResponseDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return toResponse(encounterService.findByDateRange(startDate, endDate));
    }

    @GetMapping("/status/{status}")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List encounters by status")
    public List<EncounterResponseDTO> getByStatus(@PathVariable EncounterStatus status) {
        return toResponse(encounterService.findByStatus(status));
    }

    @PostMapping
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
    public EncounterResponseDTO create(@RequestBody Encounter encounter) {
        return EncounterResponseDTO.from(encounterService.create(encounter));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Update encounter")
    public ResponseEntity<EncounterResponseDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    return ResponseEntity.ok(EncounterResponseDTO.from(encounterService.update(encounter)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Check in encounter")
    public ResponseEntity<Void> checkIn(@PathVariable Long id) {
        encounterService.checkIn(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Start encounter")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        encounterService.startEncounter(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Complete encounter")
    public ResponseEntity<Void> complete(@PathVariable Long id, @RequestBody(required = false) String notes) {
        encounterService.completeEncounter(id, notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Cancel encounter")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        encounterService.cancelEncounter(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(CLINICAL_ROLES)
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Mark encounter no-show")
    public ResponseEntity<Void> noShow(@PathVariable Long id) {
        encounterService.markNoShow(id);
        return ResponseEntity.ok().build();
    }

    private static List<EncounterResponseDTO> toResponse(List<Encounter> encounters) {
        return encounters.stream().map(EncounterResponseDTO::from).collect(Collectors.toList());
    }
}
