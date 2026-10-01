package com.medchart.ehr.service;

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

/**
 * Service for clinical encounter lifecycle management.
 *
 * Handles creation, lookup, and workflow state transitions
 * (check-in, start, complete, cancel, no-show) for patient encounters.
 */
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
    public Optional<Encounter> findByIdWithDetails(Long id) {
        return encounterRepository.findByIdWithDetails(id);
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findByEncounterNumber(String encounterNumber) {
        return encounterRepository.findByEncounterNumber(encounterNumber);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByPatientId(Long patientId) {
        return encounterRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByProviderId(Long providerId) {
        return encounterRepository.findByAttendingProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByStatus(EncounterStatus status) {
        return encounterRepository.findByStatus(status);
    }

    public Encounter create(Encounter encounter) {
        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return saved;
    }

    public Encounter update(Encounter encounter) {
        log.info("Updating encounter {}", encounter.getEncounterNumber());
        return encounterRepository.save(encounter);
    }

    public void checkIn(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CHECKED_IN);
            encounterRepository.save(enc);
            log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        });
    }

    public void startEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.IN_PROGRESS);
            encounterRepository.save(enc);
            log.info("Encounter {} started", enc.getEncounterNumber());
        });
    }

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

    public void cancelEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CANCELLED);
            encounterRepository.save(enc);
            log.info("Encounter {} cancelled", enc.getEncounterNumber());
        });
    }

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
