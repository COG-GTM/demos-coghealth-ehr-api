package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.EncounterAccessAuditor;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.service.EncounterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/encounters")
public class EncounterController {

    private final EncounterService encounterService;
    private final EncounterMapper encounterMapper;
    private final EncounterAccessAuditor accessAuditor;

    public EncounterController(EncounterService encounterService, EncounterMapper encounterMapper,
                               EncounterAccessAuditor accessAuditor) {
        this.encounterService = encounterService;
        this.encounterMapper = encounterMapper;
        this.accessAuditor = accessAuditor;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EncounterDTO> getById(@PathVariable Long id) {
        return encounterService.findByIdWithDetails(id)
                .map(e -> audited(e, AuditAction.READ, "View encounter"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(e -> audited(e, AuditAction.READ, "View encounter by number"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<EncounterDTO> getByPatient(@PathVariable Long patientId) {
        return auditedList(patientId, encounterService.findByPatientId(patientId), "List patient encounters");
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        Page<Encounter> page = encounterService.findByPatientId(patientId, pageable);
        String description = "List patient encounters (paged)";
        try {
            Page<EncounterDTO> dtos = encounterMapper.toDtoPage(page);
            accessAuditor.logEncounterListAccess(patientId, page.getContent(), description);
            return dtos;
        } catch (RuntimeException e) {
            accessAuditor.logEncounterListAccessFailure(patientId, page.getContent(), description, e);
            throw e;
        }
    }

    @GetMapping("/provider/{providerId}")
    public List<EncounterDTO> getByProvider(@PathVariable Long providerId) {
        return auditedList(null, encounterService.findByProviderId(providerId), "List provider encounters");
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return auditedList(null, encounterService.getProviderSchedule(providerId, date), "View provider schedule");
    }

    @GetMapping("/date-range")
    public List<EncounterDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return auditedList(null, encounterService.findByDateRange(startDate, endDate), "List encounters by date range");
    }

    @GetMapping("/status/{status}")
    public List<EncounterDTO> getByStatus(@PathVariable EncounterStatus status) {
        return auditedList(null, encounterService.findByStatus(status), "List encounters by status");
    }

    @PostMapping
    public EncounterDTO create(@RequestBody Encounter encounter) {
        return audited(encounterService.create(encounter), AuditAction.CREATE, "Create encounter");
    }

    @PutMapping("/{id}")
    public ResponseEntity<EncounterDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    return ResponseEntity.ok(audited(encounterService.update(encounter), AuditAction.UPDATE, "Update encounter"));
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

    private EncounterDTO audited(Encounter encounter, AuditAction action, String description) {
        try {
            EncounterDTO dto = encounterMapper.toDto(encounter);
            accessAuditor.logEncounterAccess(encounter, action, description);
            return dto;
        } catch (RuntimeException e) {
            accessAuditor.logEncounterAccessFailure(encounter, action, description, e);
            throw e;
        }
    }

    private List<EncounterDTO> auditedList(Long patientId, List<Encounter> encounters, String description) {
        try {
            List<EncounterDTO> dtos = encounterMapper.toDtoList(encounters);
            accessAuditor.logEncounterListAccess(patientId, encounters, description);
            return dtos;
        } catch (RuntimeException e) {
            accessAuditor.logEncounterListAccessFailure(patientId, encounters, description, e);
            throw e;
        }
    }
}
