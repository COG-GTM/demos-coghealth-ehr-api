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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/v1/encounters")
public class EncounterController {

    static final long MAX_DATE_RANGE_DAYS = 90;

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
                .map(e -> detail(e, "View encounter"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{encounterNumber}")
    public ResponseEntity<EncounterDTO> getByNumber(@PathVariable String encounterNumber) {
        return encounterService.findByEncounterNumber(encounterNumber)
                .map(e -> detail(e, "View encounter by number"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    public List<EncounterDTO> getByPatient(@PathVariable Long patientId) {
        return list(patientId, encounterService.findByPatientId(patientId), "List patient encounters");
    }

    @GetMapping("/patient/{patientId}/paged")
    public Page<EncounterDTO> getByPatientPaged(@PathVariable Long patientId, Pageable pageable) {
        Page<Encounter> page = encounterService.findByPatientId(patientId, pageable);
        accessAuditor.logListAccess(patientId, page.getContent(), "List patient encounters (paged)");
        return page.map(encounterMapper::toDto);
    }

    @GetMapping("/provider/{providerId}")
    public List<EncounterDTO> getByProvider(@PathVariable Long providerId) {
        return list(null, encounterService.findByProviderId(providerId), "List provider encounters");
    }

    @GetMapping("/provider/{providerId}/schedule")
    public List<EncounterDTO> getProviderSchedule(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return list(null, encounterService.getProviderSchedule(providerId, date), "View provider schedule");
    }

    @GetMapping("/date-range")
    public List<EncounterDTO> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_DATE_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range must not exceed " + MAX_DATE_RANGE_DAYS + " days");
        }
        return list(null, encounterService.findByDateRange(startDate, endDate), "List encounters by date range");
    }

    @GetMapping("/status/{status}")
    public List<EncounterDTO> getByStatus(@PathVariable EncounterStatus status) {
        return list(null, encounterService.findByStatus(status), "List encounters by status");
    }

    @PostMapping
    public EncounterDTO create(@RequestBody Encounter encounter) {
        Encounter created = encounterService.create(encounter);
        accessAuditor.logAccess(created, AuditAction.CREATE, "Create encounter");
        return encounterMapper.toDto(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EncounterDTO> update(@PathVariable Long id, @RequestBody Encounter encounter) {
        return encounterService.findById(id)
                .map(existing -> {
                    encounter.setId(id);
                    Encounter updated = encounterService.update(encounter);
                    accessAuditor.logAccess(updated, AuditAction.UPDATE, "Update encounter");
                    return ResponseEntity.ok(encounterMapper.toDto(updated));
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

    private EncounterDTO detail(Encounter encounter, String description) {
        accessAuditor.logAccess(encounter, AuditAction.READ, description);
        return encounterMapper.toDetailDto(encounter);
    }

    private List<EncounterDTO> list(Long patientId, List<Encounter> encounters, String description) {
        accessAuditor.logListAccess(patientId, encounters, description);
        return encounterMapper.toDtoList(encounters);
    }
}
