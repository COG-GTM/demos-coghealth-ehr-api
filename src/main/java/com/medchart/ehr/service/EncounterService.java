package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.repository.EncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    
    private final EncounterRepository encounterRepository;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findById(Long id) {
        return encounterRepository.findById(id);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter with details")
    public Optional<Encounter> findByIdWithDetails(Long id) {
        return encounterRepository.findByIdWithDetails(id);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    public Optional<Encounter> findByEncounterNumber(String encounterNumber) {
        return encounterRepository.findByEncounterNumber(encounterNumber);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View patient encounters")
    public List<Encounter> findByPatientId(Long patientId) {
        return encounterRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View patient encounters paged")
    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View provider encounters")
    public List<Encounter> findByProviderId(Long providerId) {
        return encounterRepository.findByAttendingProviderId(providerId);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View provider schedule")
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by date range")
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by status")
    public List<Encounter> findByStatus(EncounterStatus status) {
        return encounterRepository.findByStatus(status);
    }

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
    public Encounter create(Encounter encounter) {
        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return saved;
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Update encounter")
    public Encounter update(Encounter encounter) {
        log.info("Updating encounter {}", encounter.getEncounterNumber());
        return encounterRepository.save(encounter);
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Patient check-in")
    public void checkIn(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CHECKED_IN);
            encounterRepository.save(enc);
            log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Start encounter")
    public void startEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.IN_PROGRESS);
            encounterRepository.save(enc);
            log.info("Encounter {} started", enc.getEncounterNumber());
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Complete encounter")
    public void completeEncounter(Long encounterId, String notes) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.COMPLETED);
            if (notes != null) {
                enc.setNotes(notes);
            }
            encounterRepository.save(enc);
            log.info("Encounter {} completed", enc.getEncounterNumber());
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Cancel encounter")
    public void cancelEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CANCELLED);
            encounterRepository.save(enc);
            log.info("Encounter {} cancelled", enc.getEncounterNumber());
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Mark no-show")
    public void markNoShow(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.NO_SHOW);
            encounterRepository.save(enc);
            log.info("Encounter {} marked as no-show", enc.getEncounterNumber());
        });
    }

    @Transactional(readOnly = true)
    public long getPatientEncounterCount(Long patientId) {
        return encounterRepository.countByPatientId(patientId);
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
