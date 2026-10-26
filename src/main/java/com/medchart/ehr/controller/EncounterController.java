package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
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

    public EncounterController(EncounterService encounterService, EncounterMapper encounterMapper) {
        this.encounterService = encounterService;
        this.encounterMapper = encounterMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EncounterDTO> getById(@PathVariable Long id) {
        return encounterService.findByIdWithDetails(id)
                .map(encounterMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(encounterMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters")
    @GetMapping("/patient/{patientId}")
    public List<EncounterDTO> getByPatient(@PathVariable Long patientId) {
        return encounterMapper.toDtoList(encounterService.findByPatientId(patientId));
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters (paged)")
    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        return encounterMapper.toDtoPage(encounterService.findByPatientId(patientId, pageable));
    }

    @GetMapping("/provider/{providerId}")
    public List<EncounterDTO> getByProvider(@PathVariable Long providerId) {
        return encounterMapper.toDtoList(encounterService.findByProviderId(providerId));
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return encounterMapper.toDtoList(encounterService.getProviderSchedule(providerId, date));
    }

    @GetMapping("/date-range")
    public List<EncounterDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return encounterMapper.toDtoList(encounterService.findByDateRange(startDate, endDate));
    }

    @GetMapping("/status/{status}")
    public List<EncounterDTO> getByStatus(@PathVariable EncounterStatus status) {
        return encounterMapper.toDtoList(encounterService.findByStatus(status));
    }

    @PostMapping
    public EncounterDTO create(@RequestBody Encounter encounter) {
        return encounterMapper.toDto(encounterService.create(encounter));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EncounterDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    return ResponseEntity.ok(encounterMapper.toDto(encounterService.update(encounter)));
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
